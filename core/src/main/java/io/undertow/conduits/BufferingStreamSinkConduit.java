package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.PipeLiningBuffer;
import io.undertow.util.ConduitFactory;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;

import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Buffer for pipelined requests. Basic behaviour is as follows:
 * <p/>
 *
 * @author Stuart Douglas
 */
public class BufferingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> implements PipeLiningBuffer {

    /**
     * If this channel is shutdown
     */
    private static final int SHUTDOWN = 1;
    private static final int DELEGATE_SHUTDOWN = 1<<1;
    private static final int UPGRADED = 1<<2;
    private static final int FLUSHING = 1<<3;

    private int state;

    private final Pool<ByteBuffer> pool;
    private Pooled<ByteBuffer> buffer;

    public BufferingStreamSinkConduit(StreamSinkConduit next, final Pool<ByteBuffer> pool) {
        super(next);
        this.pool = pool;
    }

    /**
     * We do not buffer file transfers
     */
    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if(anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if(!flushBuffer()) {
            return 0;
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
            int put = buffer.remaining();
            Buffers.copy(put, buffer, srcs, offset, length);
            flushBuffer();
            return put;
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
        if(pooled == null) {
            this.buffer = pooled = pool.allocate();
        }
        final ByteBuffer buffer = pooled.getResource();
        if (buffer.remaining() > src.remaining()) {
            int put = src.remaining();
            buffer.put(src);
            return put;
        } else {
            int put = buffer.remaining();
            int old = src.limit();
            src.limit(src.position() + put);
            buffer.put(src);
            src.limit(old);
            flushBuffer();
            return put;
        }
    }

    @Override
    public boolean flushPipelinedData() throws IOException {
        if (buffer == null || buffer.getResource().position() == 0) {
            return next.flush();
        }
        if(!flushBuffer()) {
            return false;
        }
        return next.flush();
    }

    @Override
    public ConduitWrapper<StreamSinkConduit> getChannelWrapper() {
        return new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
                return BufferingStreamSinkConduit.this;
            }
        };
    }

    @Override
    public void upgradeUnderlyingChannel() {
        state |= UPGRADED;
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
        int res = 0;
        do {
            res = next.write(byteBuffer);
            if (res == 0) {
                return false;
            }
        } while (byteBuffer.hasRemaining());
        if(!next.flush()) {
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
        if (anyAreSet(state, SHUTDOWN | UPGRADED)) {
            if (!flushBuffer()) {
                return false;
            }
            if(anyAreSet(state, SHUTDOWN) &&
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
        if(buffer == null) {
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
}

