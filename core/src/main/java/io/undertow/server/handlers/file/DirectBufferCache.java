/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.file;

import static io.undertow.server.handlers.file.LimitedBufferSlicePool.PooledByteBuffer;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.undertow.util.SecureHashMap;
import org.xnio.BufferAllocator;

/**
 * A non-blocking buffer cache where entries are indexed by a path and are made up of a
 * subsequence of blocks in a fixed large direct buffer. An ideal application is
 * a file system cache, where the path corresponds to a file location.
 *
 * <p>To reduce contention, entry allocation and eviction execute in a sampling
 * fashion (entry hits modulo N). Eviction follows an LRU approach (oldest sampled
 * entries are removed first) when the cache is out of capacity</p>
 *
 * <p>In order to expedite reclamation, cache entries are reference counted as
 * opposed to garbage collected.</p>
 *
 * @author Jason T. Greene
 */
public class DirectBufferCache {
    private static final int SAMPLE_INTERVAL = 5;

    private final LimitedBufferSlicePool pool;
    private final SecureHashMap<String, CacheEntry> cache;
    private final ConcurrentDirectDeque<CacheEntry> accessQueue;
    private final int sliceSize;

    public DirectBufferCache(int sliceSize, int max) {
        this.sliceSize = sliceSize;
        this.pool = new LimitedBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, sliceSize, max, 1);
        this.cache = new SecureHashMap<String, CacheEntry>(16);
        ConcurrentDirectDeque<CacheEntry> accessQueue;
        try {
            accessQueue = new FastConcurrentDirectDeque<CacheEntry>();
        } catch (Throwable t) {
            accessQueue = new PortableConcurrentDirectDeque<CacheEntry>();
        }
        this.accessQueue = accessQueue;
    }

    public CacheEntry add(String path, int size) {
        CacheEntry value = cache.get(path);
        if (value == null) {
            value = new CacheEntry(path, size, this);
            CacheEntry result = cache.putIfAbsent(path, value);
            if (result != null) {
                value = result;
            } else {
                bumpAccess(value);
            }
        }

        return value;
    }

    public CacheEntry get(String path) {
        CacheEntry cacheEntry = cache.get(path);
        if (cacheEntry == null) {
            return null;
        }

        if (cacheEntry.hit() % SAMPLE_INTERVAL == 0) {
            bumpAccess(cacheEntry);

            if (! cacheEntry.allocate()) {
                // Try and make room
                int reclaimSize = cacheEntry.size();
                for (CacheEntry oldest : accessQueue) {
                    if (oldest == cacheEntry) {
                        continue;
                    }

                    if (oldest.buffers().length > 0) {
                        reclaimSize -= oldest.size();
                    }

                    this.remove(oldest.path());

                    if (reclaimSize <= 0) {
                        break;
                    }
                }

                // Maybe lucky?
                cacheEntry.allocate();
            }
        }

        return cacheEntry;
    }

    private void bumpAccess(CacheEntry cacheEntry) {
        Object prevToken = cacheEntry.claimToken();
        if (prevToken != Boolean.FALSE) {
            if (prevToken != null) {
                accessQueue.removeToken(prevToken);
            }

            Object token = null;
            try {
                token = accessQueue.offerLastAndReturnToken(cacheEntry);
            } catch (Throwable t) {
                // In case of disaster (OOME), we need to release the claim, so leave it aas null
            }

            if (! cacheEntry.setToken(token) && token != null) { // Always set if null
                accessQueue.removeToken(token);
            }
        }
    }


    public void remove(String path) {
        CacheEntry remove = cache.remove(path);
        if (remove != null) {
            Object old = remove.clearToken();
            if (old != null) {
                accessQueue.removeToken(old);
            }
            remove.dereference();
        }
    }

    public static final class CacheEntry {
        private static final PooledByteBuffer[] EMPTY_BUFFERS = new PooledByteBuffer[0];
        private static final PooledByteBuffer[] INIT_BUFFERS = new PooledByteBuffer[0];
        private static final Object CLAIM_TOKEN = new Object();

        private static final AtomicIntegerFieldUpdater<CacheEntry> hitsUpdater = AtomicIntegerFieldUpdater.newUpdater(CacheEntry.class, "hits");
        private static final AtomicIntegerFieldUpdater<CacheEntry> refsUpdater = AtomicIntegerFieldUpdater.newUpdater(CacheEntry.class, "refs");
        private static final AtomicIntegerFieldUpdater<CacheEntry> enabledUpdator = AtomicIntegerFieldUpdater.newUpdater(CacheEntry.class, "enabled");

        private static final AtomicReferenceFieldUpdater<CacheEntry, PooledByteBuffer[]> bufsUpdater = AtomicReferenceFieldUpdater.newUpdater(CacheEntry.class, PooledByteBuffer[].class, "buffers");
        private static final AtomicReferenceFieldUpdater<CacheEntry, Object> tokenUpdator = AtomicReferenceFieldUpdater.newUpdater(CacheEntry.class, Object.class, "accessToken");

        private final String path;
        private final int size;
        private final DirectBufferCache cache;
        private volatile PooledByteBuffer[] buffers = INIT_BUFFERS;
        private volatile int refs = 1;
        private volatile int hits = 1;
        private volatile Object accessToken;
        private volatile int enabled;

        private CacheEntry(String path, int size, DirectBufferCache cache) {
            this.path = path;
            this.size = size;
            this.cache = cache;
        }

        public int size() {
            return size;
        }

        public PooledByteBuffer[] buffers() {
            return buffers;
        }

        public int hit() {
            for (;;) {
                int i = hits;

                if (hitsUpdater.weakCompareAndSet(this, i, ++i)) {
                    return i;
                }

            }
        }

        public String path() {
            return path;
        }

        public boolean enabled() {
            return enabled == 2;
        }

        public void enable() {
            this.enabled = 2;
        }

        public void disable() {
            this.enabled = 0;
        }

        public boolean claimEnable() {
            return enabledUpdator.compareAndSet(this, 0, 1);
        }

        public boolean reference() {
            for(;;) {
                int refs = this.refs;
                if (refs < 1) {
                    return false; // destroying
                }

                if (refsUpdater.compareAndSet(this, refs++, refs)) {
                    return true;
                }
            }
        }

        public boolean dereference() {
            for(;;) {
                int refs = this.refs;
                if (refs < 1) {
                    return false;  // destroying
                }

                if (refsUpdater.compareAndSet(this, refs--, refs)) {
                    if (refs == 0) {
                        destroy();
                    }
                    return true;
                }
            }
        }

        public boolean allocate() {
            if (buffers.length > 0)
                return true;

            if (! bufsUpdater.compareAndSet(this, INIT_BUFFERS, EMPTY_BUFFERS)) {
                return true;
            }

            int reserveSize = size;
            int n = 1;
            DirectBufferCache bufferCache = cache;
            while ((reserveSize -= bufferCache.sliceSize) > 0) {
                n++;
            }

            // Try to avoid mutations
            LimitedBufferSlicePool slicePool = bufferCache.pool;
            if (! slicePool.canAllocate(n)) {
                this.buffers = INIT_BUFFERS;
                return false;
            }

            PooledByteBuffer[] buffers = new PooledByteBuffer[n];
            for (int i = 0; i < n; i++) {
                PooledByteBuffer allocate = slicePool.allocate();
                if (allocate == null) {
                    while (--i >= 0) {
                        buffers[i].free();
                    }

                    this.buffers = INIT_BUFFERS;
                    return false;
                }

                buffers[i] = allocate;
            }

            this.buffers = buffers;
            return true;
        }

        private void destroy() {
            this.buffers = EMPTY_BUFFERS;
            for (PooledByteBuffer buffer : buffers) {
                buffer.free();
            }
        }

        Object claimToken() {
            for (;;) {
                Object current = this.accessToken;
                if (current == CLAIM_TOKEN) {
                    return Boolean.FALSE;
                }

                if (tokenUpdator.compareAndSet(this, current, CLAIM_TOKEN)) {
                    return current;
                }
            }
        }

        boolean setToken(Object token) {
            return tokenUpdator.compareAndSet(this, CLAIM_TOKEN, token);
        }

        Object clearToken() {
            Object old = tokenUpdator.getAndSet(this, null);
            return old == CLAIM_TOKEN ? null : old;
        }

    }
}