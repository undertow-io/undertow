package io.undertow.server.handlers.cache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.StreamSinkConduit;

/**
 * @author Stuart Douglas
 */
public class ResponseCachingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final DirectBufferCache.CacheEntry cacheEntry;
    private final long length;
    private long written;

    /**
     * Construct a new instance.
     *
     * @param next       the delegate conduit to set
     * @param cacheEntry
     * @param length
     */
    protected ResponseCachingStreamSinkConduit(final StreamSinkConduit next, final DirectBufferCache.CacheEntry cacheEntry, final long length) {
        super(next);
        this.cacheEntry = cacheEntry;
        this.length = length;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        LimitedBufferSlicePool.PooledByteBuffer[] pooled = cacheEntry.buffers();
        ByteBuffer[] buffers = new ByteBuffer[pooled.length];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = pooled[i].getResource();
        }
        written += Buffers.copy(buffers, 0, buffers.length, src.duplicate());
        for (ByteBuffer buffer : buffers) {
            //prepare buffers for reading
            buffer.flip();
        }
        return super.write(src);
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {
        LimitedBufferSlicePool.PooledByteBuffer[] pooled = cacheEntry.buffers();
        ByteBuffer[] buffers = new ByteBuffer[pooled.length];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = pooled[i].getResource();
        }
        ByteBuffer[] src = new ByteBuffer[srcs.length];
        for (int i = 0; i < srcs.length; i++) {
            src[i] = srcs[i].duplicate();
        }
        written += Buffers.copy(buffers, 0, buffers.length, src, 0, src.length);
        for (ByteBuffer buffer : buffers) {
            //prepare buffers for reading
            buffer.flip();
        }
        return super.write(srcs, offs, len);
    }

    @Override
    public void terminateWrites() throws IOException {
        if (written == length) {
            cacheEntry.enable();
        } else {
            cacheEntry.disable();
            cacheEntry.dereference();
        }
        super.terminateWrites();
    }

    @Override
    public void truncateWrites() throws IOException {
        cacheEntry.disable();
        cacheEntry.dereference();
        super.truncateWrites();
    }
}
