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

package io.undertow.server;

import io.undertow.UndertowMessages;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * A byte buffer pool that supports reference counted pools.
 *
 * @author Stuart Douglas
 */
// TODO: move this somewhere more appropriate
public class DefaultByteBufferPool implements ByteBufferPool {

    private final ThreadLocalCache threadLocalCache = new ThreadLocalCache();
    // Access requires synchronization on the threadLocalDataList instance
    private final List<WeakReference<ThreadLocalData>> threadLocalDataList = new ArrayList<>();

    private final ConcurrentLinkedQueue<ByteBuffer>[] queues;
    private final int queueCount;
    private final int perQueueMax;

    private final boolean direct;
    private final int bufferSize;
    private final int maximumPoolSize;
    private final int threadLocalCacheSize;
    private final int leakDectionPercent;
    private int count; //racily updated count used in leak detection

    private final AtomicIntegerArray currentQueueLengths;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int reclaimedThreadLocals = 0;
    private static final AtomicIntegerFieldUpdater<DefaultByteBufferPool> reclaimedThreadLocalsUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultByteBufferPool.class, "reclaimedThreadLocals");

    private volatile boolean closed;

    private final DefaultByteBufferPool arrayBackedPool;


    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     */
    public DefaultByteBufferPool(boolean direct, int bufferSize) {
        this(direct, bufferSize, -1, 12, 0);
    }
    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     * @param maximumPoolSize      The maximum pool size, in number of buffers, it does not include buffers in thread local caches
     * @param threadLocalCacheSize The maximum number of buffers that can be stored in a thread local cache
     */
    public DefaultByteBufferPool(boolean direct, int bufferSize, int maximumPoolSize, int threadLocalCacheSize, int leakDecetionPercent) {
        this(direct, bufferSize, maximumPoolSize, threadLocalCacheSize, leakDecetionPercent,
                Runtime.getRuntime().availableProcessors() * 2);
    }

    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     * @param maximumPoolSize      The maximum pool size, in number of buffers, it does not include buffers in thread local caches
     * @param threadLocalCacheSize The maximum number of buffers that can be stored in a thread local cache
     * @param leakDecetionPercent  The percentage of allocations that should track leaks
     * @param queueCount           Number of queues to use for reduced contention
     */
    @SuppressWarnings("unchecked")
    public DefaultByteBufferPool(boolean direct, int bufferSize, int maximumPoolSize, int threadLocalCacheSize,
                                 int leakDecetionPercent, int queueCount) {
        this.direct = direct;
        this.bufferSize = bufferSize;
        this.maximumPoolSize = maximumPoolSize;
        this.threadLocalCacheSize = threadLocalCacheSize;
        this.leakDectionPercent = leakDecetionPercent;
        this.queueCount = Math.max(1, queueCount);

        // Calculate per-queue maximum. Minimum size of 1
        this.perQueueMax = maximumPoolSize > 0 ? Math.max(1, maximumPoolSize / this.queueCount) : Integer.MAX_VALUE;

        this.queues = new ConcurrentLinkedQueue[this.queueCount];
        this.currentQueueLengths = new AtomicIntegerArray(this.queueCount);
        for (int i = 0; i < this.queueCount; i++) {
            this.queues[i] = new ConcurrentLinkedQueue<>();
        }

        if(direct) {
            arrayBackedPool = new DefaultByteBufferPool(false, bufferSize, maximumPoolSize, 0, leakDecetionPercent, this.queueCount);
        } else {
            arrayBackedPool = this;
        }
    }


    /**
     * @param direct               If this implementation should use direct buffers
     * @param bufferSize           The buffer size to use
     * @param maximumPoolSize      The maximum pool size, in number of buffers, it does not include buffers in thread local caches
     * @param threadLocalCacheSize The maximum number of buffers that can be stored in a thread local cache
     */
    public DefaultByteBufferPool(boolean direct, int bufferSize, int maximumPoolSize, int threadLocalCacheSize) {
        this(direct, bufferSize, maximumPoolSize, threadLocalCacheSize, 0,
                Runtime.getRuntime().availableProcessors() * 2);
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public boolean isDirect() {
        return direct;
    }

    private int getQueueIndex() {
        long threadId = Thread.currentThread().getId();
        return (int)(threadId % queueCount);
    }

    @Override
    public PooledByteBuffer allocate() {
        if (closed) {
            throw UndertowMessages.MESSAGES.poolIsClosed();
        }
        ByteBuffer buffer = null;
        ThreadLocalData local = null;
        if(threadLocalCacheSize > 0) {
            local = threadLocalCache.get();
            if (local != null) {
                buffer = local.buffers.poll();
            } else {
                local = new ThreadLocalData();
                synchronized (threadLocalDataList) {
                    if (closed) {
                        throw UndertowMessages.MESSAGES.poolIsClosed();
                    }
                    cleanupThreadLocalData();
                    threadLocalDataList.add(new WeakReference<>(local));
                    threadLocalCache.set(local);
                }

            }
        }
        if (buffer == null) {
            int queueIdx = getQueueIndex();
            buffer = queues[queueIdx].poll();
            if (buffer != null) {
                currentQueueLengths.decrementAndGet(queueIdx);
            }
        }
        if (buffer == null) {
            if (direct) {
                buffer = ByteBuffer.allocateDirect(bufferSize);
            } else {
                buffer = ByteBuffer.allocate(bufferSize);
            }
        }
        if(local != null) {
            if(local.allocationDepth < threadLocalCacheSize) { //prevent overflow if the thread only allocates and never frees
                local.allocationDepth++;
            }
        }
        buffer.clear();
        return new DefaultPooledBuffer(this, buffer, leakDectionPercent == 0 ? false : (++count % 100 < leakDectionPercent));
    }

    @Override
    public ByteBufferPool getArrayBackedPool() {
        return arrayBackedPool;
    }

    private void cleanupThreadLocalData() {
        // Called under lock, and only when at least quarter of the capacity has been collected.

        final int size = threadLocalDataList.size();

        if (reclaimedThreadLocals > (size / 4)) {
            int j = 0;
            for (int i = 0; i < size; i++) {
                WeakReference<ThreadLocalData> ref = threadLocalDataList.get(i);
                if (ref.get() != null) {
                    threadLocalDataList.set(j++, ref);
                }
            }
            for (int i = size - 1; i >= j; i--) {
                // A tail remove is inlined to a range change check and a decrement
                threadLocalDataList.remove(i);
            }
            reclaimedThreadLocalsUpdater.addAndGet(this, -1 * (size - j));
        }
    }

    private void freeInternal(ByteBuffer buffer) {
        if (closed) {
            DirectByteBufferDeallocator.free(buffer);
            return; //GC will take care of it
        }
        final ThreadLocalData local = threadLocalCache.get();
        if(local != null) {
            if(local.allocationDepth > 0) {
                local.allocationDepth--;
                if (local.buffers.size() < threadLocalCacheSize) {
                    local.buffers.add(buffer);
                    return;
                }
            }
        }
        queueIfUnderMax(buffer);
    }

    private void queueIfUnderMax(ByteBuffer buffer) {
        int size;
        int queueIdx = getQueueIndex();
        do {
            size = currentQueueLengths.get(queueIdx);
            if (maximumPoolSize > 0 && size >= perQueueMax) {
                DirectByteBufferDeallocator.free(buffer);
                return;
            }
        } while (!currentQueueLengths.compareAndSet(queueIdx, size, size + 1));

        queues[queueIdx].add(buffer);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        for (int i = 0; i < queueCount; i++) {
            queues[i].clear();
            currentQueueLengths.set(i, 0);
        }

        synchronized (threadLocalDataList) {
            for (WeakReference<ThreadLocalData> ref : threadLocalDataList) {
                final ThreadLocalData local = ref.get();
                ref.clear();
                if (local != null) {
                    local.buffers.clear();
                    threadLocalCache.remove(local);
                }
            }
            threadLocalDataList.clear();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    private static class DefaultPooledBuffer implements PooledByteBuffer {

        private final DefaultByteBufferPool pool;
        private final LeakDetector leakDetector;
        private ByteBuffer buffer;

        private volatile int referenceCount = 1;
        private static final AtomicIntegerFieldUpdater<DefaultPooledBuffer> referenceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(DefaultPooledBuffer.class, "referenceCount");

        DefaultPooledBuffer(DefaultByteBufferPool pool, ByteBuffer buffer, boolean detectLeaks) {
            this.pool = pool;
            this.buffer = buffer;
            this.leakDetector = detectLeaks ? new LeakDetector() : null;
        }

        @Override
        public ByteBuffer getBuffer() {
            final ByteBuffer tmp = this.buffer;
            //UNDERTOW-2072
            if (referenceCount == 0 || tmp == null) {
                throw UndertowMessages.MESSAGES.bufferAlreadyFreed();
            }
            return tmp;
        }

        @Override
        public void close() {
            final ByteBuffer tmp = this.buffer;
            if (referenceCountUpdater.compareAndSet(this, 1, 0)) {
                this.buffer = null;
                if (leakDetector != null) {
                    leakDetector.closed = true;
                }
                pool.freeInternal(tmp);
            }
        }

        @Override
        public boolean isOpen() {
            return referenceCount > 0;
        }

        @Override
        public String toString() {
            return "DefaultPooledBuffer{" +
                    "buffer=" + buffer +
                    ", referenceCount=" + referenceCount +
                    '}';
        }
    }

    private class ThreadLocalData {
        final ArrayDeque<ByteBuffer> buffers = new ArrayDeque<>(threadLocalCacheSize);
        int allocationDepth = 0;

        @Override
        protected void finalize() throws Throwable {
            try {
                reclaimedThreadLocalsUpdater.incrementAndGet(DefaultByteBufferPool.this);
                if (buffers != null) {
                    // Recycle them
                    ByteBuffer buffer;
                    while ((buffer = buffers.poll()) != null) {
                        queueIfUnderMax(buffer);
                    }
                }
            } finally {
                super.finalize();
            }
        }
    }

    private static class LeakDetector {

        volatile boolean closed = false;
        private final Throwable allocationPoint;

        private LeakDetector() {
            this.allocationPoint = new Throwable("Buffer leak detected");
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if(!closed) {
                    allocationPoint.printStackTrace();
                }
            } finally {
                super.finalize();
            }
        }
    }

    // This is used instead of Java ThreadLocal class. Unlike in the ThreadLocal class, the remove() method in this
    // class can be called by a different thread than the one that initialized the data.
    private static class ThreadLocalCache {

        final Map<Thread, ThreadLocalData> localsByThread = Collections.synchronizedMap(new WeakHashMap<>());

        ThreadLocalData get() {
            return localsByThread.get(Thread.currentThread());
        }

        void set(ThreadLocalData threadLocalData) {
            localsByThread.put(Thread.currentThread(), threadLocalData);
        }

        void remove(ThreadLocalData threadLocalData) {
            // Find the entry containing given data instance and remove it from the map.
            for (Map.Entry<Thread, ThreadLocalData> entry: localsByThread.entrySet()) {
                if (threadLocalData.equals(entry.getValue())) {
                    localsByThread.remove(entry.getKey(), entry.getValue());
                    break;
                }
            }
        }
    }

}
