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

package io.undertow.server.handlers.resource;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.undertow.UndertowLogger;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.DirectBufferCache.CacheEntry;
import io.undertow.server.handlers.cache.LRUCache;

/**
 * @author Stuart Douglas
 */
public class CachingResourceManager implements ResourceManager {

    /**
     * Max age 0, indicating that entries expire upon creation and are not retained;
     */
    public static final int MAX_AGE_NO_CACHING = LRUCache.MAX_AGE_NO_CACHING;
    /**
     * Mage age -1, this force manager to retain entries until underlying resource manager indicate that entries expired/changed
     */
    public static final int MAX_AGE_NO_EXPIRY = LRUCache.MAX_AGE_NO_EXPIRY;
    /**
     * The biggest file size we cache
     */
    private final long maxFileSize;

    /**
     * The underlying resource manager
     */
    private final ResourceManager underlyingResourceManager;

    /**
     * A cache of byte buffers
     */
    private final DirectBufferCache dataCache;

    /**
     * A cache of file metadata, such as if a file exists or not
     */
    private LRUCache<String, Object> cache;

    private final ReentrantReadWriteLock cacheAccessLock = new ReentrantReadWriteLock();

    private int maxAge;

    public CachingResourceManager(final int metadataCacheSize, final long maxFileSize, final DirectBufferCache dataCache, final ResourceManager underlyingResourceManager, final int maxAge) {
        this.maxFileSize = maxFileSize;
        this.underlyingResourceManager = underlyingResourceManager;
        this.dataCache = dataCache;

        if(maxAge > 0 || maxAge == MAX_AGE_NO_CACHING || maxAge == MAX_AGE_NO_EXPIRY) {
            this.maxAge = maxAge;
        } else {
            UndertowLogger.ROOT_LOGGER.wrongCacheTTLValue(maxAge, MAX_AGE_NO_CACHING);
            this.maxAge = MAX_AGE_NO_CACHING;
        }

        this.cache = new LRUCache<>(metadataCacheSize, maxAge);
        if(underlyingResourceManager.isResourceChangeListenerSupported()) {
            try {
                underlyingResourceManager.registerResourceChangeListener(new ResourceChangeListener() {
                    @Override
                    public void handleChanges(Collection<ResourceChangeEvent> changes) {
                        for(ResourceChangeEvent change : changes) {
                            invalidate(change.getResource());
                        }
                    }
                });
            } catch (Exception e) {
                int errorMaxAge = this.maxAge;
                if(!(this.maxAge > 0 || this.maxAge == CachingResourceManager.MAX_AGE_NO_CACHING)) {
                    errorMaxAge = CachingResourceManager.MAX_AGE_NO_CACHING;
                }
                UndertowLogger.ROOT_LOGGER.failedToRegisterChangeListener(this.maxAge,errorMaxAge,e);
                this.maxAge = errorMaxAge;
            }
        }
    }

    @Override
    public CachedResource getResource(final String p) throws IOException {
        if( p == null ) {
            return null;
        }
        final String path;
        //base always ends with a /
        if (p.startsWith("/")) {
            path = p.substring(1);
        } else {
            path = p;
        }
        // ReadLock will allow multiple reads and changes, guarded by WriteLock for purge
        final Lock readLock = this.cacheAccessLock.readLock();
        final Lock writeLock = this.cacheAccessLock.writeLock();
        Object res = null;
        try {
            readLock.lock();
            res = cache.get(path);
        } finally {
            readLock.unlock();
        }
        if (res instanceof NoResourceMarker) {
            NoResourceMarker marker = (NoResourceMarker) res;
            long nextCheck = marker.getNextCheckTime();
            if (nextCheck > 0) {
                long time = System.currentTimeMillis();
                if (time > nextCheck) {
                    marker.setNextCheckTime(time + maxAge);
                    if (underlyingResourceManager.getResource(path) != null) {
                        try {
                            writeLock.lock();
                            cache.remove(path);
                        } finally {
                            writeLock.unlock();
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else if (res != null) {
            CachedResource resource = (CachedResource) res;
            if (resource.checkStillValid()) {
                return resource;
            } else {
                try {
                    writeLock.lock();
                    invalidate(this.cache, path);
                } finally {
                    writeLock.unlock();
                }
            }
        }
        final Resource underlying = underlyingResourceManager.getResource(path);
        if (underlying == null) {
            if (this.maxAge != MAX_AGE_NO_CACHING) {
                try {
                    writeLock.lock();
                    cache.add(path, new NoResourceMarker(maxAge > 0 ? System.currentTimeMillis() + maxAge : -1));
                } finally {
                    writeLock.unlock();
                }
            }
            return null;
            // NOTE; in case of purge, no need to clear this, if there was resource, now there is none
            // invalidete() will purge potential dataCache entry
        }
        CachedResource resource = null;
        try {
            writeLock.lock();
            resource = new CachedResource(this, underlying, path);
            final DirectBufferCache dataCache = getDataCache();
            if (dataCache != null) {
                final CacheEntry o = dataCache.get(resource.getCacheKey());
                if (o != null) {
                    // lazy remove, #invalidate() might have not get to this point.
                    dataCache.remove(o.key());
                }
            }

            cache.add(path, resource);
        } finally {
            writeLock.unlock();
        }
        return resource;

    }

    @Override
    public boolean isResourceChangeListenerSupported() {
        return underlyingResourceManager.isResourceChangeListenerSupported();
    }

    @Override
    public void registerResourceChangeListener(ResourceChangeListener listener) {
        underlyingResourceManager.registerResourceChangeListener(listener);
    }

    @Override
    public void removeResourceChangeListener(ResourceChangeListener listener) {
        underlyingResourceManager.removeResourceChangeListener(listener);
    }

    public void invalidate(String path) {
        final Lock writeLock = this.cacheAccessLock.writeLock();
        writeLock.lock();
        try {
            this.invalidate(this.cache, path);
        } finally {
            writeLock.unlock();
        }
    }

    public void invalidate() {
        final Lock writeLock = this.cacheAccessLock.writeLock();
        final LRUCache<String, Object> localCopy = this.cache;
        writeLock.lock();
        try {
            this.cache = new LRUCache(localCopy.getMaxEntries(),localCopy.getMaxAge());
        } finally {
            writeLock.unlock();
        }
        for(String key:localCopy.keySet()) {
            //clear dataCache while new entries are made. This can potentially
            //remove dataCache entries, but those can be recreated.
            this.invalidate(localCopy, key);
        }
        //just in case
        localCopy.clear();
    }

    private void invalidate(LRUCache<String, Object> localCopy, String path) {
        if(path.startsWith("/")) {
            path = path.substring(1);
        }
        Object entry = localCopy.remove(path);
        if (entry instanceof CachedResource) {
            ((CachedResource) entry).invalidate();
        }
    }

    DirectBufferCache getDataCache() {
        return dataCache;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public int getMaxAge() {
        return maxAge;
    }

    @Override
    public void close() throws IOException {
        try {
            //clear all cached data on close
            if(dataCache != null) {
                Set<Object> keys = dataCache.getAllKeys();
                for(final Object key : keys) {
                    if(key instanceof CachedResource.CacheKey) {
                        if(((CachedResource.CacheKey) key).manager == this) {
                            dataCache.remove(key);
                        }
                    }
                }
            }
        } finally {
            underlyingResourceManager.close();
        }
    }

    private static final class NoResourceMarker {

        volatile long nextCheckTime;

        private NoResourceMarker(long nextCheckTime) {
            this.nextCheckTime = nextCheckTime;
        }

        public long getNextCheckTime() {
            return nextCheckTime;
        }

        public void setNextCheckTime(long nextCheckTime) {
            this.nextCheckTime = nextCheckTime;
        }
    }
}
