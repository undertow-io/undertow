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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
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
    public ResponseCachingStreamSinkConduit(final StreamSinkConduit next, final DirectBufferCache.CacheEntry cacheEntry, final long length) {
        super(next);
        this.cacheEntry = cacheEntry;
        this.length = length;
        for(LimitedBufferSlicePool.PooledByteBuffer buffer: cacheEntry.buffers()) {
            buffer.getBuffer().clear();
        }
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
        ByteBuffer origSrc = src.duplicate();
        int totalWritten =  super.write(src);
        if(totalWritten > 0)  {
            LimitedBufferSlicePool.PooledByteBuffer[] pooled = cacheEntry.buffers();
            ByteBuffer[] buffers = new ByteBuffer[pooled.length];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = pooled[i].getBuffer();
            }
            origSrc.limit(origSrc.position() + totalWritten);
            written += Buffers.copy(buffers, 0, buffers.length, origSrc);
            if (written == length) {
                for (ByteBuffer buffer : buffers) {
                    //prepare buffers for reading
                    buffer.flip();
                }
                cacheEntry.enable();
            }
        }
        return totalWritten;
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offs, final int len) throws IOException {

        ByteBuffer[] origSrc = new ByteBuffer[srcs.length];
        for (int i = 0; i < srcs.length; i++) {
            origSrc[i] = srcs[i].duplicate();
        }
        long totalWritten =  super.write(srcs, offs, len);
        if(totalWritten > 0)  {
            LimitedBufferSlicePool.PooledByteBuffer[] pooled = cacheEntry.buffers();
            ByteBuffer[] buffers = new ByteBuffer[pooled.length];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = pooled[i].getBuffer();
            }
            long leftToCopy = totalWritten;
            for(int i = 0; i < len; ++i) {
                ByteBuffer buf = origSrc[offs + i];
                if(buf.remaining() > leftToCopy) {
                    buf.limit((int) (buf.position() + leftToCopy));
                }
                leftToCopy -= buf.remaining();
                Buffers.copy(buffers, 0, buffers.length, buf);
                if(leftToCopy == 0) {
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
        return totalWritten;
    }

    @Override
    public void terminateWrites() throws IOException {
        if (written != length) {
            cacheEntry.disable();
            cacheEntry.dereference();
        }
        super.terminateWrites();
    }

    @Override
    public void truncateWrites() throws IOException {
        if (written != length) {
            cacheEntry.disable();
            cacheEntry.dereference();
        }
        super.truncateWrites();
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }
}
