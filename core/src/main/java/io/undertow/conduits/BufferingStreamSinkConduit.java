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
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;

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

    public BufferingStreamSinkConduit(StreamSinkConduit next, final Pool<ByteBuffer> pool) {
        super(next);
        this.pool = pool;
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
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
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
            return next.flush();
        } else if (buffer.getResource().position() == 0) {
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
            public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> channel, HttpServerExchange exchange) {
                return BufferingStreamSinkConduit.this;
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
            res = next.write(byteBuffer);
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
        if (shutdown || upgraded) {
            if (!flushBuffer()) {
                return false;
            }
            return next.flush();
        }
        return true;
    }

    @Override
    public void terminateWrites() throws IOException {
        shutdown = true;
        next.terminateWrites();
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

