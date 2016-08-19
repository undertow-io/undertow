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

import static io.undertow.server.handlers.cache.LimitedBufferSlicePool.PooledByteBuffer;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.ConcurrentHashMap;

import io.undertow.util.ConcurrentDirectDeque;
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
    private final ConcurrentMap<Object, CacheEntry> cache;
    private final ConcurrentDirectDeque<CacheEntry> accessQueue;
    private final int sliceSize;
    private final int maxAge;

    public DirectBufferCache(int sliceSize, int slicesPerPage, int maxMemory) {
        this(sliceSize, slicesPerPage, maxMemory, BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR);
    }

    public DirectBufferCache(int sliceSize, int slicesPerPage, int maxMemory, final BufferAllocator<ByteBuffer> bufferAllocator) {
        this(sliceSize, slicesPerPage, maxMemory, bufferAllocator, -1);
    }

    public DirectBufferCache(int sliceSize, int slicesPerPage, int maxMemory, final BufferAllocator<ByteBuffer> bufferAllocator, int maxAge) {
        this.sliceSize = sliceSize;
        this.pool = new LimitedBufferSlicePool(bufferAllocator, sliceSize, sliceSize * slicesPerPage, maxMemory / (sliceSize * slicesPerPage));
        this.cache = new ConcurrentHashMap<>(16);
        this.accessQueue = ConcurrentDirectDeque.newInstance();
        this.maxAge = maxAge;
    }

    public CacheEntry add(Object key, int size) {
        return add(key, size, maxAge);
    }

    public CacheEntry add(Object key, int size, int maxAge) {
        CacheEntry value = cache.get(key);
        if (value == null) {
            value = new CacheEntry(key, size, this, maxAge);
            CacheEntry result = cache.putIfAbsent(key, value);
            if (result != null) {
                value = result;
            } else {
                bumpAccess(value);
            }
        }

        return value;
    }

    public CacheEntry get(Object key) {
        CacheEntry cacheEntry = cache.get(key);
        if (cacheEntry == null) {
            return null;
        }

        long expires = cacheEntry.getExpires();
        if(expires != -1) {
            if(System.currentTimeMillis() > expires) {
                remove(key);
                return null;
            }
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

                    this.remove(oldest.key());

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

    /**
     * Returns a set of all the keys in the cache. This is a copy of the
     * key set at the time of method invocation.
     *
     * @return all the keys in this cache
     */
    public Set<Object> getAllKeys() {
        return new HashSet<>(cache.keySet());
    }

    private void bumpAccess(CacheEntry cacheEntry) {
        Object prevToken = cacheEntry.claimToken();
        if (!Boolean.FALSE.equals(prevToken)) {
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


    public void remove(Object key) {
        CacheEntry remove = cache.remove(key);
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

        private final Object key;
        private final int size;
        private final DirectBufferCache cache;
        private final int maxAge;
        private volatile PooledByteBuffer[] buffers = INIT_BUFFERS;
        private volatile int refs = 1;
        private volatile int hits = 1;
        private volatile Object accessToken;
        private volatile int enabled;
        private volatile long expires = -1;

        private CacheEntry(Object key, int size, DirectBufferCache cache, final int maxAge) {
            this.key = key;
            this.size = size;
            this.cache = cache;
            this.maxAge = maxAge;
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

        public Object key() {
            return key;
        }

        public boolean enabled() {
            return enabled == 2;
        }

        public void enable() {
            if(maxAge == -1) {
                this.expires = -1;
            } else {
                this.expires = System.currentTimeMillis() + maxAge;
            }
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

        long getExpires() {
            return expires;
        }
    }
}
