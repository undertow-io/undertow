/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.handlers.cache;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
        handleUpdate(origSrc);
        delegate.send(src, callback);
    }


    @Override
    public void send(final ByteBuffer[] srcs, final IoCallback callback) {
        ByteBuffer[] origSrc = new ByteBuffer[srcs.length];
        long total = 0;
        for (int i = 0; i < srcs.length; i++) {
            origSrc[i] = srcs[i].duplicate();
            total += origSrc[i].remaining();
        }
        handleUpdate(origSrc, total);
        delegate.send(srcs, callback);
    }

    @Override
    public void send(final ByteBuffer src) {
        ByteBuffer origSrc = src.duplicate();
        handleUpdate(origSrc);
        delegate.send(src);
    }

    @Override
    public void send(final ByteBuffer[] srcs) {
        ByteBuffer[] origSrc = new ByteBuffer[srcs.length];
        long total = 0;
        for (int i = 0; i < srcs.length; i++) {
            origSrc[i] = srcs[i].duplicate();
            total += origSrc[i].remaining();
        }
        handleUpdate(origSrc, total);
        delegate.send(srcs);
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        handleUpdate(ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8)));
        delegate.send(data, callback);
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        handleUpdate(ByteBuffer.wrap(data.getBytes(charset)));
        delegate.send(data, charset, callback);
    }

    @Override
    public void send(final String data) {
        handleUpdate(ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8)));
        delegate.send(data);
    }

    @Override
    public void send(final String data, final Charset charset) {
        handleUpdate(ByteBuffer.wrap(data.getBytes(charset)));
        delegate.send(data, charset);
    }

    @Override
    public void transferFrom(FileChannel channel, IoCallback callback) {
        // Transfer never caches
        delegate.transferFrom(channel, callback);
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
            buffers[i] = pooled[i].getBuffer();
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
            buffers[i] = pooled[i].getBuffer();
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
