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

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;

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
    public void send(final ByteBuf src, final IoCallback callback) {
        ByteBuf origSrc = src.duplicate();
        handleUpdate(origSrc);
        delegate.send(src, callback);
    }


    @Override
    public void send(final ByteBuf[] srcs, final IoCallback callback) {
        ByteBuf[] origSrc = new ByteBuf[srcs.length];
        for (int i = 0; i < srcs.length; i++) {
            origSrc[i] = srcs[i].duplicate();
        }
        handleUpdate(origSrc);
        delegate.send(srcs, callback);
    }

    @Override
    public void send(final ByteBuf src) {
        ByteBuf origSrc = src.duplicate();
        handleUpdate(origSrc);
        delegate.send(src);
    }

    @Override
    public void send(final ByteBuf[] srcs) {
        ByteBuf[] origSrc = new ByteBuf[srcs.length];
        for (int i = 0; i < srcs.length; i++) {
            origSrc[i] = srcs[i].duplicate();
        }
        handleUpdate(origSrc);
        delegate.send(srcs);
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        handleUpdate(Unpooled.copiedBuffer(data, StandardCharsets.UTF_8));
        delegate.send(data, callback);
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        handleUpdate(Unpooled.copiedBuffer(data, charset));
        delegate.send(data, charset, callback);
    }

    @Override
    public void send(final String data) {
        handleUpdate(Unpooled.copiedBuffer(data, StandardCharsets.UTF_8));
        delegate.send(data);
    }

    @Override
    public void send(final String data, final Charset charset) {
        handleUpdate(Unpooled.copiedBuffer(data, StandardCharsets.UTF_8));
        delegate.send(data, charset);
    }

    @Override
    public void transferFrom(RandomAccessFile channel, IoCallback callback) {
        // Transfer never caches
        delegate.transferFrom(channel, callback);
    }

    @Override
    public void transferFrom(RandomAccessFile channel, long start, long length, IoCallback callback) {
        delegate.transferFrom(channel, start, length, callback);
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

    private void handleUpdate(final ByteBuf origSrc) {
        LimitedBufferSlicePool.PooledByteBuffer[] pooled = cacheEntry.buffers();
        for (int i = 0; i < pooled.length; i++) {
            int written = Math.min(pooled[i].buffer.writableBytes(), origSrc.writableBytes());
            this.written += written;
            pooled[i].buffer.writeBytes(origSrc, written);
        }
        if (written == length) {
            cacheEntry.enable();
        }
    }

    private void handleUpdate(final ByteBuf[] origSrc) {
        for (ByteBuf i : origSrc) {
            handleUpdate(i);
        }
    }
}
