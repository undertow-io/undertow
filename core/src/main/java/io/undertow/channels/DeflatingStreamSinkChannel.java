package io.undertow.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.anyAreSet;

/**
 * Channel that handles deflate compression
 *
 * @author Stuart Douglas
 */
public class DeflatingStreamSinkChannel implements StreamSinkChannel {

    private final ChannelListener.SimpleSetter<DeflatingStreamSinkChannel> closeSetter = new ChannelListener.SimpleSetter<DeflatingStreamSinkChannel>();
    private final ChannelListener.SimpleSetter<DeflatingStreamSinkChannel> writeSetter = new ChannelListener.SimpleSetter<DeflatingStreamSinkChannel>();

    private final Deflater deflater;
    private final ChannelFactory<StreamSinkChannel> channelFactory;
    private final HttpServerExchange exchange;

    /**
     * The original content length
     */
    private final Long reportedContentLength;

    private StreamSinkChannel delegate;

    /**
     * The streams buffer. This is freed when the delegate is shutdown
     */
    private final Pooled<ByteBuffer> currentBuffer;
    /**
     * there may have been some additional data that did not fit into the first buffer
     */
    private ByteBuffer additionalBuffer;

    private int state = 0;


    private static final int SHUTDOWN = 1;
    private static final int DELEGATE_SHUTDOWN = 1 << 1;
    private static final int FLUSHING_BUFFER = 1 << 2;
    private static final int WRITES_RESUMED = 1 << 3;
    private static final int CLOSED = 1 << 4;


    public DeflatingStreamSinkChannel(final ChannelFactory<StreamSinkChannel> channelFactory, final HttpServerExchange exchange) {
        deflater = new Deflater(Deflater.DEFLATED, true);
        this.currentBuffer = exchange.getConnection().getBufferPool().allocate();
        this.exchange = exchange;
        this.channelFactory = channelFactory;
        String contentLength = exchange.getResponseHeaders().getFirst(Headers.CONTENT_LENGTH);
        if (contentLength != null) {
            reportedContentLength = Long.parseLong(contentLength);
        } else {
            reportedContentLength = null;
        }
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
    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (anyAreSet(SHUTDOWN | CLOSED, state)) {
            throw new ClosedChannelException();
        }
        if (!performFlushIfRequired()) {
            return 0;
        }
        return src.transferTo(position, count, this);
    }


    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (anyAreSet(SHUTDOWN | CLOSED, state)) {
            throw new ClosedChannelException();
        }
        if (!performFlushIfRequired()) {
            return 0;
        }
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return exchange.getConnection().getWorker();
    }

    @Override
    public void suspendWrites() {
        if (delegate == null) {
            state = state & ~WRITES_RESUMED;
        } else {
            delegate.suspendWrites();
        }
    }


    @Override
    public boolean isWriteResumed() {
        if (delegate == null) {
            return anyAreSet(WRITES_RESUMED, state);
        } else {
            return delegate.isWriteResumed();
        }
    }

    @Override
    public void wakeupWrites() {
        if (delegate == null) {
            resumeWrites();
        } else {
            delegate.wakeupWrites();
        }
    }

    @Override
    public void resumeWrites() {
        if (delegate == null) {
            state |= WRITES_RESUMED;
            queueWriteListener();
        } else {
            delegate.resumeWrites();
        }
    }

    private void queueWriteListener() {
        exchange.getConnection().getChannel().getWriteThread().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelListeners.invokeChannelListener(DeflatingStreamSinkChannel.this, writeSetter.get());
                } finally {
                    //if writes are still resumed queue up another one
                    if (delegate == null && isWriteResumed()) {
                        queueWriteListener();
                    }
                }
            }
        });
    }


    @Override
    public void shutdownWrites() throws IOException {
        deflater.finish();
        state |= SHUTDOWN;
    }

    @Override
    public void awaitWritable() throws IOException {
        if (delegate == null) {
            return;
        } else {
            delegate.awaitWritable();
        }
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        if (delegate == null) {
            return;
        } else {
            delegate.awaitWritable(time, timeUnit);
        }
    }

    @Override
    public XnioExecutor getWriteThread() {
        return exchange.getWriteThread();
    }

    @Override
    public boolean flush() throws IOException {
        boolean delegateCreated = false;
        try {
            if (anyAreSet(SHUTDOWN, state)) {
                if (anyAreSet(DELEGATE_SHUTDOWN, state)) {
                    return delegate.flush();
                } else {
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
                        if(delegate == null) {
                            delegateCreated = true;
                            createDelegate();
                        }
                    }
                    if (performFlushIfRequired()) {
                        state |= DELEGATE_SHUTDOWN;
                        currentBuffer.free();
                        delegate.shutdownWrites();
                        return delegate.flush();
                    } else {
                        return false;
                    }
                }
            } else {
                return performFlushIfRequired();
            }
        } finally {
            if (delegateCreated) {
                if (anyAreSet(WRITES_RESUMED, state) && !anyAreSet(DELEGATE_SHUTDOWN, state)) {
                    delegate.resumeWrites();
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
            long total = 0;
            long res = 0;
            do {
                res = delegate.write(bufs);
                total += res;
                if (res == 0) {
                    return false;
                }
            } while (total < totalLength);
            additionalBuffer = null;
            currentBuffer.getResource().clear();
            state = state & ~FLUSHING_BUFFER;
        }
        return true;
    }


    private void createDelegate() {
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
        this.delegate = channelFactory.create();
        delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener(this, writeSetter));
        delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
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
        boolean delegateCreated = false;
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
                        if (delegate == null) {
                            delegateCreated = true;
                            createDelegate();
                        }
                        if (!performFlushIfRequired()) {
                            return;
                        }
                    }
                }
            }
        } finally {
            if (delegateCreated) {
                if (anyAreSet(WRITES_RESUMED, state)) {
                    delegate.resumeWrites();
                }
            }
        }
    }

    @Override
    public boolean isOpen() {
        return !anyAreSet(SHUTDOWN | CLOSED, state);
    }

    @Override
    public void close() throws IOException {
        if (!anyAreSet(DELEGATE_SHUTDOWN, state)) {
            currentBuffer.free();
        }
        state |= CLOSED;
        delegate.close();
    }

    //TODO: not sure about these methods, I think we may need to store any options that are set
    @Override
    public boolean supportsOption(final Option<?> option) {
        return exchange.getConnection().getChannel().supportsOption(option);
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        return exchange.getConnection().getChannel().getOption(option);
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return exchange.getConnection().getChannel().setOption(option, value);
    }
}
