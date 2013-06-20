package io.undertow.server.handlers.cache;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import org.xnio.Buffers;

/**
 * @author Stuart Douglas
 */
public class ResponseCachingSender implements Sender {

    private final Sender delegate;
    private final DirectBufferCache.CacheEntry cacheEntry;
    private final long length;
    private long written;

    public ResponseCachingSender(final Sender delegate, final DirectBufferCache.CacheEntry cacheEntry, final long length) {
        this.delegate = delegate;
        this.cacheEntry = cacheEntry;
        this.length = length;
    }

    @Override
    public void send(final ByteBuffer src, final IoCallback callback) {
        ByteBuffer origSrc = src.duplicate();
        delegate.send(src, callback);
        handleUpdate(origSrc);
    }


    @Override
    public void send(final ByteBuffer[] srcs, final IoCallback callback) {
        ByteBuffer[] origSrc = new ByteBuffer[srcs.length];
        long total = 0;
        for (int i = 0; i < srcs.length; i++) {
            origSrc[i] = srcs[i].duplicate();
            total += origSrc[i].remaining();
        }
        delegate.send(srcs, callback);
        handleUpdate(origSrc, total);
    }

    @Override
    public void send(final ByteBuffer src) {
        ByteBuffer origSrc = src.duplicate();
        delegate.send(src);
        handleUpdate(origSrc);
    }

    @Override
    public void send(final ByteBuffer[] srcs) {
        ByteBuffer[] origSrc = new ByteBuffer[srcs.length];
        long total = 0;
        for (int i = 0; i < srcs.length; i++) {
            origSrc[i] = srcs[i].duplicate();
            total += origSrc[i].remaining();
        }
        delegate.send(srcs);
        handleUpdate(origSrc, total);
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        delegate.send(data, callback);
        try {
            handleUpdate(ByteBuffer.wrap(data.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        delegate.send(data, charset, callback);
        handleUpdate(ByteBuffer.wrap(data.getBytes(charset)));
    }

    @Override
    public void send(final String data) {
        delegate.send(data);
        try {
            handleUpdate(ByteBuffer.wrap(data.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void send(final String data, final Charset charset) {
        delegate.send(data, charset);
        handleUpdate(ByteBuffer.wrap(data.getBytes(charset)));
    }

    @Override
    public void close(final IoCallback callback) {
        if (written != length) {
            cacheEntry.disable();
            cacheEntry.dereference();
        }
        delegate.close();
    }

    @Override
    public void close() {
        if (written != length) {
            cacheEntry.disable();
            cacheEntry.dereference();
        }
        delegate.close();
    }

    private void handleUpdate(final ByteBuffer origSrc) {
        LimitedBufferSlicePool.PooledByteBuffer[] pooled = cacheEntry.buffers();
        ByteBuffer[] buffers = new ByteBuffer[pooled.length];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = pooled[i].getResource();
        }
        written += Buffers.copy(buffers, 0, buffers.length, origSrc);
        if (written == length) {
            for (ByteBuffer buffer : buffers) {
                //prepare buffers for reading
                buffer.flip();
            }
            cacheEntry.enable();
        }
    }

    private void handleUpdate(final ByteBuffer[] origSrc, long totalWritten) {
        LimitedBufferSlicePool.PooledByteBuffer[] pooled = cacheEntry.buffers();
        ByteBuffer[] buffers = new ByteBuffer[pooled.length];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = pooled[i].getResource();
        }
        long leftToCopy = totalWritten;
        for (int i = 0; i < origSrc.length; ++i) {
            ByteBuffer buf = origSrc[i];
            if (buf.remaining() > leftToCopy) {
                buf.limit((int) (buf.position() + leftToCopy));
            }
            leftToCopy -= buf.remaining();
            Buffers.copy(buffers, 0, buffers.length, buf);
            if (leftToCopy == 0) {
                break;
            }
        }
        written += totalWritten;
        if (written == length) {
            for (ByteBuffer buffer : buffers) {
                //prepare buffers for reading
                buffer.flip();
            }
            cacheEntry.enable();
        }
    }
}
