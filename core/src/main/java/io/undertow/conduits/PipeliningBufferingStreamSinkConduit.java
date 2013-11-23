package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.protocol.http.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * A buffer that is used when processing pipelined requests, that allows the server to
 * buffer multiple responses into a single write() call.
 * <p/>
 * This can improve performance when pipelining requests.
 *
 * @author Stuart Douglas
 */
public class PipeliningBufferingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    public static final AttachmentKey<PipeliningBufferingStreamSinkConduit> ATTACHMENT_KEY = AttachmentKey.create(PipeliningBufferingStreamSinkConduit.class);

    /**
     * If this channel is shutdown
     */
    private static final int SHUTDOWN = 1;
    private static final int DELEGATE_SHUTDOWN = 1 << 1;
    private static final int FLUSHING = 1 << 3;

    private int state;

    private final Pool<ByteBuffer> pool;
    private Pooled<ByteBuffer> buffer;

    private final ExchangeCompletionListener completionListener = new PipelineExchangeCompletionListener();

    public PipeliningBufferingStreamSinkConduit(StreamSinkConduit next, final Pool<ByteBuffer> pool) {
        super(next);
        this.pool = pool;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (anyAreSet(state, FLUSHING)) {
            boolean res = flushBuffer();
            if (!res) {
                return 0;
            }
        }
        Pooled<ByteBuffer> pooled = this.buffer;
        if (pooled == null) {
            this.buffer = pooled = pool.allocate();
        }
        final ByteBuffer buffer = pooled.getResource();

        int total = 0;
        for (int i = offset; i < offset + length; ++i) {
            total += srcs[i].remaining();
        }

        if (buffer.remaining() > total) {
            int put = total;
            Buffers.copy(buffer, srcs, offset, length);
            return put;
        } else {
            return flushBufferWithUserData(srcs);
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (anyAreSet(state, FLUSHING)) {
            boolean res = flushBuffer();
            if (!res) {
                return 0;
            }
        }
        Pooled<ByteBuffer> pooled = this.buffer;
        if (pooled == null) {
            this.buffer = pooled = pool.allocate();
        }
        final ByteBuffer buffer = pooled.getResource();
        if (buffer.remaining() > src.remaining()) {
            int put = src.remaining();
            buffer.put(src);
            return put;
        } else {
            return (int) flushBufferWithUserData(new ByteBuffer[]{src});
        }
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    private long flushBufferWithUserData(final ByteBuffer[] byteBuffers) throws IOException {
        final ByteBuffer byteBuffer = buffer.getResource();
        if (byteBuffer.position() == 0) {
            try {
                return next.write(byteBuffers, 0, byteBuffers.length);
            } finally {
                buffer.free();
                buffer = null;
            }
        }

        if (!anyAreSet(state, FLUSHING)) {
            state |= FLUSHING;
            byteBuffer.flip();
        }
        int originalBufferedRemaining = byteBuffer.remaining();
        long toWrite = originalBufferedRemaining;
        ByteBuffer[] writeBufs = new ByteBuffer[byteBuffers.length + 1];
        writeBufs[0] = byteBuffer;
        for (int i = 0; i < byteBuffers.length; ++i) {
            writeBufs[i + 1] = byteBuffers[i];
            toWrite += byteBuffers[i].remaining();
        }

        long res = 0;
        long written = 0;
        do {
            res = next.write(writeBufs, 0, writeBufs.length);
            written += res;
            if (res == 0) {
                if(written > originalBufferedRemaining) {
                    buffer.free();
                    this.buffer = null;
                    state &= ~FLUSHING;
                    return written - originalBufferedRemaining;
                }
                return 0;
            }
        } while (written < toWrite);
        buffer.free();
        this.buffer = null;
        state &= ~FLUSHING;
        return written - originalBufferedRemaining;
    }

    /**
     * Flushes the cached data.
     * <p/>
     * This should be called when a read thread fails to read any more request data, to make sure that any
     * buffered data is flushed after the last pipelined request.
     * <p/>
     * If this returns false the read thread should suspend reads and resume writes
     *
     * @return <code>true</code> If the flush suceeded, false otherwise
     * @throws IOException
     */
    public boolean flushPipelinedData() throws IOException {
        if (buffer == null || (buffer.getResource().position() == 0 && allAreClear(state, FLUSHING))) {
            return next.flush();
        }
        return flushBuffer();
    }

    /**
     * Gets the channel wrapper that implements the buffering
     *
     * @return The channel wrapper
     */
    public void setupPipelineBuffer(final HttpServerExchange exchange) {
        exchange.addExchangeCompleteListener(completionListener);
        ((HttpServerConnection)exchange.getConnection()).getChannel().getSinkChannel().setConduit(this);
    }

    private boolean flushBuffer() throws IOException {
        if (buffer == null) {
            return next.flush();
        }
        final ByteBuffer byteBuffer = buffer.getResource();
        if (!anyAreSet(state, FLUSHING)) {
            state |= FLUSHING;
            byteBuffer.flip();
        }
        while (byteBuffer.hasRemaining()) {
            if (next.write(byteBuffer) == 0) {
                return false;
            }
        }
        if (!next.flush()) {
            return false;
        }
        buffer.free();
        this.buffer = null;
        state &= ~FLUSHING;
        return true;
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (buffer != null) {
            if (buffer.getResource().hasRemaining()) {
                return;
            }
        }
        next.awaitWritable(time, timeUnit);
    }

    @Override
    public void awaitWritable() throws IOException {
        if (buffer != null) {
            if (buffer.getResource().hasRemaining()) {
                return;
            }
            next.awaitWritable();
        }
    }

    @Override
    public boolean flush() throws IOException {
        if (anyAreSet(state, SHUTDOWN)) {
            if (!flushBuffer()) {
                return false;
            }
            if (anyAreSet(state, SHUTDOWN) &&
                    anyAreClear(state, DELEGATE_SHUTDOWN)) {
                state |= DELEGATE_SHUTDOWN;
                next.terminateWrites();
            }
            return next.flush();
        }
        return true;
    }

    @Override
    public void terminateWrites() throws IOException {
        state |= SHUTDOWN;
        if (buffer == null) {
            state |= DELEGATE_SHUTDOWN;
            next.terminateWrites();
        }
    }

    public void truncateWrites() throws IOException {
        try {
            next.truncateWrites();
        } finally {
            if (buffer != null) {
                buffer.free();
            }
        }
    }

    private class PipelineExchangeCompletionListener implements ExchangeCompletionListener {
        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
            //if we ever fail to read then we flush the pipeline buffer
            //this relies on us always doing an eager read when starting a request,
            //rather than waiting to be notified of data being available
            final HttpServerConnection connection = (HttpServerConnection) exchange.getConnection();
            if (connection.getExtraBytes() == null || exchange.isUpgrade()) {
                performFlush(nextListener, connection);
            } else {
                nextListener.proceed();
            }
        }

        private void performFlush(final NextListener nextListener, final HttpServerConnection connection) {
            try {
                final HttpServerConnection.ConduitState oldState = connection.resetChannel();
                if (!flushPipelinedData()) {
                    final StreamConnection channel = connection.getChannel();
                    channel.getSinkChannel().getWriteSetter().set(new ChannelListener<Channel>() {
                        @Override
                        public void handleEvent(Channel c) {
                            try {
                                if (flushPipelinedData()) {
                                    channel.getSinkChannel().getWriteSetter().set(null);
                                    channel.getSinkChannel().suspendWrites();
                                    connection.restoreChannel(oldState);
                                    nextListener.proceed();
                                }
                            } catch (IOException e) {
                                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                                IoUtils.safeClose(channel);
                            }
                        }
                    });
                    connection.getChannel().getSinkChannel().resumeWrites();
                    return;
                } else {
                    connection.restoreChannel(oldState);
                    nextListener.proceed();
                }
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(connection.getChannel());
            }
        }
    }
}

