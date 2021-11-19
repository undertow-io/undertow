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

import org.xnio.BufferAllocator;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A limited buffer pooled allocator.  This pool uses a series of buffer regions to back the
 * returned pooled buffers.  When the buffer is no longer needed, it should be freed back into the pool; failure
 * to do so will cause the corresponding buffer area to be unavailable until the buffer is garbage-collected.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Jason T. Greene
 */
public final class LimitedBufferSlicePool {

    private static final AtomicIntegerFieldUpdater regionUpdater = AtomicIntegerFieldUpdater.newUpdater(LimitedBufferSlicePool.class, "regionsUsed");
    private final Queue<Slice> sliceQueue = new ConcurrentLinkedQueue<>();
    private final BufferAllocator<ByteBuffer> allocator;
    private final int bufferSize;
    private final int buffersPerRegion;
    private final int maxRegions;
    private volatile int regionsUsed;


    /**
     * Construct a new instance.
     *
     * @param allocator the buffer allocator to use
     * @param bufferSize the size of each buffer
     * @param maxRegionSize the maximum region size for each backing buffer
     * @param maxRegions the maximum regions to create, zero for unlimited
     */
    public LimitedBufferSlicePool(final BufferAllocator<ByteBuffer> allocator, final int bufferSize, final int maxRegionSize, final int maxRegions) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero");
        }
        if (maxRegionSize < bufferSize) {
            throw new IllegalArgumentException("Maximum region size must be greater than or equal to the buffer size");
        }
        buffersPerRegion = maxRegionSize / bufferSize;
        this.bufferSize = bufferSize;
        this.allocator = allocator;
        this.maxRegions = maxRegions;
    }

     /**
     * Construct a new instance.
     *
     * @param allocator the buffer allocator to use
     * @param bufferSize the size of each buffer
     * @param maxRegionSize the maximum region size for each backing buffer
     */
    public LimitedBufferSlicePool(BufferAllocator<ByteBuffer> allocator, int bufferSize, int maxRegionSize) {
        this(allocator, bufferSize, maxRegionSize, 0);
    }


    /**
     * Construct a new instance, using a direct buffer allocator.
     *
     * @param bufferSize the size of each buffer
     * @param maxRegionSize the maximum region size for each backing buffer
     */
    public LimitedBufferSlicePool(final int bufferSize, final int maxRegionSize) {
        this(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, bufferSize, maxRegionSize);
    }

    /**
     * Allocates a new byte buffer if possible
     *
     * @return new buffer or null if none available
     **/
    public PooledByteBuffer allocate() {
        final Queue<Slice> sliceQueue = this.sliceQueue;
        final Slice slice = sliceQueue.poll();
        if (slice == null && (maxRegions <= 0 || regionUpdater.getAndIncrement(this) < maxRegions)) {
            final int bufferSize = this.bufferSize;
            final int buffersPerRegion = this.buffersPerRegion;
            final ByteBuffer region = allocator.allocate(buffersPerRegion * bufferSize);
            int idx = bufferSize;
            for (int i = 1; i < buffersPerRegion; i ++) {
                sliceQueue.add(new Slice(region, idx, bufferSize));
                idx += bufferSize;
            }
            final Slice newSlice = new Slice(region, 0, bufferSize);
            return new PooledByteBuffer(newSlice, newSlice.slice(), sliceQueue);
        }
        if (slice == null) {
            return null;
        }
        return new PooledByteBuffer(slice, slice.slice(), sliceQueue);
    }

    public boolean canAllocate(int slices) {
        if (regionsUsed < maxRegions)
            return true;

        if (sliceQueue.isEmpty())
            return false;

        Iterator iterator = sliceQueue.iterator();
        for (int i = 0; i < slices; i++) {
            if (! iterator.hasNext()) {
                return false;
            }
            try {
                iterator.next();
            } catch (NoSuchElementException e) {
                return false;
            }
        }

        return true;
    }

    public static final class PooledByteBuffer {
        private final Slice region;
        private final Queue<Slice> slices;
        volatile ByteBuffer buffer;

        private static final AtomicReferenceFieldUpdater<PooledByteBuffer, ByteBuffer> bufferUpdater = AtomicReferenceFieldUpdater.newUpdater(PooledByteBuffer.class, ByteBuffer.class, "buffer");

        private PooledByteBuffer(final Slice region, final ByteBuffer buffer, final Queue<Slice> slices) {
            this.region = region;
            this.buffer = buffer;
            this.slices = slices;
        }

        public void free() {
            if (bufferUpdater.getAndSet(this, null) != null) {
                // trust the user, repool the buffer
                slices.add(region);
            }
        }

        public ByteBuffer getBuffer() {
            final ByteBuffer buffer = this.buffer;
            if (buffer == null) {
                throw new IllegalStateException();
            }
            return buffer;
        }

        public String toString() {
            return "Pooled buffer " + buffer;
        }
    }

    private static final class Slice {
        private final ByteBuffer parent;
        private final int start;
        private final int size;

        private Slice(final ByteBuffer parent, final int start, final int size) {
            this.parent = parent;
            this.start = start;
            this.size = size;
        }

        ByteBuffer slice() {
            return ((ByteBuffer)parent.duplicate().position(start).limit(start+size)).slice();
        }
    }
}
