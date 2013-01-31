package io.undertow.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.server.ChannelWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.PipeLiningBuffer;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Buffer for pipelined requests. Basic behaviour is as follows:
 * <p/>
 *
 * @author Stuart Douglas
 */
public class BufferingStreamSinkChannel extends DelegatingStreamSinkChannel<BufferingStreamSinkChannel> implements PipeLiningBuffer {

    /**
     * If this channel is shutdown
     */
    private boolean shutdown = false;

    /**
     * When this is true then flushes will no longer pass through
     */
    private boolean upgraded = false;

    /**
     * If this is true the buffer is being written out via a direct flush.
     */
    private boolean flushing = false;

    private final Pool<ByteBuffer> pool;
    private Pooled<ByteBuffer> buffer;

    public BufferingStreamSinkChannel(StreamSinkChannel delegate, final Pool<ByteBuffer> pool) {
        super(delegate);
        this.pool = pool;
        delegate.getCloseSetter().set(new ChannelListener<Channel>() {
            @Override
            public void handleEvent(final Channel channel) {
                try {
                ChannelListeners.invokeChannelListener(BufferingStreamSinkChannel.this, closeSetter.get());
                } finally {
                    if(buffer != null) {
                        buffer.free();
                        buffer = null;
                    }
                }
            }
        });
    }

    /**
     * We do not buffer file transfers
     */
    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if(shutdown) {
            throw new ClosedChannelException();
        }
        if(!flushBuffer()) {
            return 0;
        }
        return super.transferFrom(src, position, count);
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long count = 0;
        for (int i = offset; i < length; ++i) {
            int ret = write(srcs[i]);
            count += ret;
            if (ret == 0) {
                return count;
            }
        }
        return count;

    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if(shutdown) {
            throw new ClosedChannelException();
        }
        if (flushing) {
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
        if (buffer == null) {
            return delegate.flush();
        } else if (buffer.getResource().position() == 0) {
            return delegate.flush();
        }
        if(!flushBuffer()) {
            return false;
        }
        return delegate.flush();
    }

    @Override
    public ChannelWrapper<StreamSinkChannel> getChannelWrapper() {
        return new ChannelWrapper<StreamSinkChannel>() {
            @Override
            public StreamSinkChannel wrap(ChannelFactory<StreamSinkChannel> channel, HttpServerExchange exchange) {
                return BufferingStreamSinkChannel.this;
            }
        };
    }

    @Override
    public void upgradeUnderlyingChannel() {
        upgraded = true;
    }

    private boolean flushBuffer() throws IOException {
        if (buffer == null) {
            return true;
        }
        final ByteBuffer byteBuffer = buffer.getResource();
        if (!flushing) {
            flushing = true;
            byteBuffer.flip();
        }
        int res = 0;
        do {
            res = delegate.write(byteBuffer);
            if (res == 0) {
                return false;
            }
        } while (byteBuffer.hasRemaining());
        buffer.free();
        this.buffer = null;
        flushing = false;
        return true;
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (buffer != null) {
            if (buffer.getResource().hasRemaining()) {
                return;
            }
        }
        delegate.awaitWritable(time, timeUnit);
    }

    @Override
    public void awaitWritable() throws IOException {
        if (buffer != null) {
            if (buffer.getResource().hasRemaining()) {
                return;
            }
            delegate.awaitWritable();
        }
    }

    @Override
    public boolean flush() throws IOException {
        if (shutdown || upgraded) {
            if (!flushBuffer()) {
                return false;
            }
            return delegate.flush();
        }
        return true;
    }

    @Override
    public void shutdownWrites() throws IOException {
        shutdown = true;
        delegate.shutdownWrites();
    }

    @Override
    public void close() throws IOException {
        if(this.buffer != null) {
            this.buffer.free();
            this.buffer = null;
        }
        super.close();
    }
}

