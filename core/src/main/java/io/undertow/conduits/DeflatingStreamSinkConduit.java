package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Headers;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import static org.xnio.Bits.anyAreSet;

/**
 * Channel that handles deflate compression
 *
 * @author Stuart Douglas
 */
public class DeflatingStreamSinkConduit implements StreamSinkConduit {

    private final Deflater deflater;
    private final ConduitFactory<StreamSinkConduit> conduitFactory;
    private final HttpServerExchange exchange;

    private StreamSinkConduit next;
    private WriteReadyHandler writeReadyHandler;


    /**
     * The streams buffer. This is freed when the next is shutdown
     */
    private final Pooled<ByteBuffer> currentBuffer;
    /**
     * there may have been some additional data that did not fit into the first buffer
     */
    private ByteBuffer additionalBuffer;

    private int state = 0;

    private static final int SHUTDOWN = 1;
    private static final int next_SHUTDOWN = 1 << 1;
    private static final int FLUSHING_BUFFER = 1 << 2;
    private static final int WRITES_RESUMED = 1 << 3;
    private static final int CLOSED = 1 << 4;

    public DeflatingStreamSinkConduit(final ConduitFactory<StreamSinkConduit> conduitFactory, final HttpServerExchange exchange) {

        deflater = new Deflater(Deflater.DEFLATED, true);
        this.currentBuffer = exchange.getConnection().getBufferPool().allocate();
        this.exchange = exchange;
        this.conduitFactory = conduitFactory;
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (anyAreSet(SHUTDOWN | CLOSED, state)) {
            throw new ClosedChannelException();
        }
        if (!performFlushIfRequired()) {
            return 0;
        }
        if (src.remaining() == 0) {
            return 0;
        }
        byte[] data = new byte[src.remaining()];
        src.get(data);
        deflater.setInput(data);
        deflateData();
        return data.length;
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (anyAreSet(SHUTDOWN | CLOSED, state)) {
            throw new ClosedChannelException();
        }
        int total = 0;
        for (int i = offset; i < offset + length; ++i) {
            if (srcs[i].hasRemaining()) {
                int ret = write(srcs[i]);
                total += ret;
                if (ret == 0) {
                    return total;
                }
            }
        }
        return total;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (anyAreSet(SHUTDOWN | CLOSED, state)) {
            throw new ClosedChannelException();
        }
        if (!performFlushIfRequired()) {
            return 0;
        }
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }


    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (anyAreSet(SHUTDOWN | CLOSED, state)) {
            throw new ClosedChannelException();
        }
        if (!performFlushIfRequired()) {
            return 0;
        }
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public XnioWorker getWorker() {
        return exchange.getConnection().getWorker();
    }

    @Override
    public void suspendWrites() {
        if (next == null) {
            state = state & ~WRITES_RESUMED;
        } else {
            next.suspendWrites();
        }
    }


    @Override
    public boolean isWriteResumed() {
        if (next == null) {
            return anyAreSet(WRITES_RESUMED, state);
        } else {
            return next.isWriteResumed();
        }
    }

    @Override
    public void wakeupWrites() {
        if (next == null) {
            resumeWrites();
        } else {
            next.wakeupWrites();
        }
    }

    @Override
    public void resumeWrites() {
        if (next == null) {
            state |= WRITES_RESUMED;
            queueWriteListener();
        } else {
            next.resumeWrites();
        }
    }

    private void queueWriteListener() {
        exchange.getConnection().getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                if(writeReadyHandler != null) {
                    try {
                        writeReadyHandler.writeReady();
                    } finally {
                        //if writes are still resumed queue up another one
                        if (next == null && isWriteResumed()) {
                            queueWriteListener();
                        }
                    }
                }
            }
        });
    }


    @Override
    public void terminateWrites() throws IOException {
        deflater.finish();
        state |= SHUTDOWN;
    }

    @Override
    public boolean isWriteShutdown() {
        return anyAreSet(state, SHUTDOWN);
    }

    @Override
    public void awaitWritable() throws IOException {
        if (next == null) {
            return;
        } else {
            next.awaitWritable();
        }
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        if (next == null) {
            return;
        } else {
            next.awaitWritable(time, timeUnit);
        }
    }

    @Override
    public XnioIoThread getWriteThread() {
        return exchange.getConnection().getIoThread();
    }

    @Override
    public void setWriteReadyHandler(final WriteReadyHandler handler) {
        this.writeReadyHandler = handler;
    }

    @Override
    public boolean flush() throws IOException {
        boolean nextCreated = false;
        try {
            if (anyAreSet(SHUTDOWN, state)) {
                if (anyAreSet(next_SHUTDOWN, state)) {
                    return next.flush();
                } else {
                    if (!performFlushIfRequired()) {
                        return false;
                    }
                    //if the deflater has not been fully flushed we need to flush it
                    if (!deflater.finished()) {
                        deflateData();
                        //if could not fully flush
                        if (!deflater.finished()) {
                            return false;
                        }
                    }
                    //ok the deflater is flushed, now we need to flush the buffer
                    if (!anyAreSet(FLUSHING_BUFFER, state)) {
                        currentBuffer.getResource().flip();
                        state |= FLUSHING_BUFFER;
                        if(next == null) {
                            nextCreated = true;
                            createnext();
                        }
                    }
                    if (performFlushIfRequired()) {
                        state |= next_SHUTDOWN;
                        currentBuffer.free();
                        next.terminateWrites();
                        return next.flush();
                    } else {
                        return false;
                    }
                }
            } else {
                return performFlushIfRequired();
            }
        } finally {
            if (nextCreated) {
                if (anyAreSet(WRITES_RESUMED, state) && !anyAreSet(next_SHUTDOWN, state)) {
                    next.resumeWrites();
                }
            }
        }
    }

    /**
     * The we are in the flushing state then we flush to the underlying stream, otherwise just return true
     *
     * @return false if there is still more to flush
     */
    private boolean performFlushIfRequired() throws IOException {
        if (anyAreSet(FLUSHING_BUFFER, state)) {
            final ByteBuffer[] bufs = new ByteBuffer[additionalBuffer == null ? 1 : 2];
            long totalLength = 0;
            bufs[0] = currentBuffer.getResource();
            totalLength += bufs[0].remaining();
            if (additionalBuffer != null) {
                bufs[1] = additionalBuffer;
                totalLength += bufs[1].remaining();
            }
            if(totalLength > 0) {
                long total = 0;
                long res = 0;
                do {
                    res = next.write(bufs, 0, bufs.length);
                    total += res;
                    if (res == 0) {
                        return false;
                    }
                } while (total < totalLength);
            }
            additionalBuffer = null;
            currentBuffer.getResource().clear();
            state = state & ~FLUSHING_BUFFER;
        }
        return true;
    }


    private void createnext() {
        if (deflater.finished()) {
            //the deflater was fully flushed before we created the channel. This means that what is in the buffer is
            //all there is
            int remaining = currentBuffer.getResource().remaining();
            if(additionalBuffer != null) {
                remaining += additionalBuffer.remaining();
            }
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Integer.toString(remaining));
        } else {
            exchange.getResponseHeaders().remove(Headers.CONTENT_LENGTH);
        }
        this.next = conduitFactory.create();
    }

    /**
     * Runs the current data through the deflater. As much as possible this will be buffered in the current output
     * stream.
     *
     * @throws IOException
     */
    private void deflateData() throws IOException {
        //we don't need to flush here, as this should have been called already by the time we get to
        //this point
        boolean nextCreated = false;
        try {
            Pooled<ByteBuffer> pooled = this.currentBuffer;
            final ByteBuffer outputBuffer = pooled.getResource();

            final boolean shutdown = anyAreSet(SHUTDOWN, state);

            byte[] buffer = new byte[1024]; //TODO: we should pool this and make it configurable or something
            while (!deflater.needsInput() || (shutdown && !deflater.finished())) {
                int count = deflater.deflate(buffer);
                if (count != 0) {
                    int remaining = outputBuffer.remaining();
                    if (remaining > count) {
                        outputBuffer.put(buffer, 0, count);
                    } else {
                        if (remaining == count) {
                            outputBuffer.put(buffer, 0, count);
                        } else {
                            outputBuffer.put(buffer, 0, remaining);
                            additionalBuffer = ByteBuffer.wrap(buffer, remaining, count - remaining);
                        }
                        outputBuffer.flip();
                        this.state |= FLUSHING_BUFFER;
                        if (next == null) {
                            nextCreated = true;
                            createnext();
                        }
                        if (!performFlushIfRequired()) {
                            return;
                        }
                    }
                }
            }
        } finally {
            if (nextCreated) {
                if (anyAreSet(WRITES_RESUMED, state)) {
                    next.resumeWrites();
                }
            }
        }
    }


    @Override
    public void truncateWrites() throws IOException {
        if (!anyAreSet(next_SHUTDOWN, state)) {
            currentBuffer.free();
        }
        state |= CLOSED;
        next.truncateWrites();
    }
}
