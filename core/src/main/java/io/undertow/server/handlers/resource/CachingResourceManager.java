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

import io.undertow.UndertowLogger;
import io.undertow.server.handlers.cache.DirectBufferCache;
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
    private final LRUCache<String, Object> cache;

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
        Object res = cache.get(path);
        if (res instanceof NoResourceMarker) {
            NoResourceMarker marker = (NoResourceMarker) res;
            long nextCheck = marker.getNextCheckTime();
            if(nextCheck > 0) {
                long time = System.currentTimeMillis();
                if(time > nextCheck) {
                    marker.setNextCheckTime(time + maxAge);
                    if(underlyingResourceManager.getResource(path) != null) {
                        cache.remove(path);
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
                invalidate(path);
            }
        }
        final Resource underlying = underlyingResourceManager.getResource(path);
        if (underlying == null) {
            if(this.maxAge != MAX_AGE_NO_CACHING) {
                cache.add(path, new NoResourceMarker(maxAge > 0 ? System.currentTimeMillis() + maxAge : -1));
            }
            return null;
        }
        final CachedResource resource = new CachedResource(this, underlying, path);
        cache.add(path, resource);
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
        if(path.startsWith("/")) {
            path = path.substring(1);
        }
        Object entry = cache.remove(path);
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
