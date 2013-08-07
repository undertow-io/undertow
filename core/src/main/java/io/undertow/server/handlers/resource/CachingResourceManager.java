/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers.resource;

import java.io.IOException;

import io.undertow.UndertowLogger;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.LRUCache;

/**
 * @author Stuart Douglas
 */
public class CachingResourceManager implements ResourceManager {

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

    private final int maxAge;

    public CachingResourceManager(final int metadataCacheSize, final long maxFileSize, final DirectBufferCache dataCache, final ResourceManager underlyingResourceManager, final int maxAge) {
        this.maxFileSize = maxFileSize;
        this.underlyingResourceManager = underlyingResourceManager;
        this.dataCache = dataCache;
        this.cache = new LRUCache<String, Object>(metadataCacheSize, maxAge);
        this.maxAge = maxAge;
    }

    @Override
    public Resource getResource(final String path) throws IOException {
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
                return (Resource) res;
            } else {
                invalidate(path);
            }
        }
        final Resource underlying = underlyingResourceManager.getResource(path);
        if (underlying == null) {
            cache.add(path, new NoResourceMarker(maxAge > 0 ? System.currentTimeMillis() + maxAge : -1));
            return null;
        }
        final CachedResource resource = new CachedResource(this, underlying, path);
        cache.add(path, resource);
        return resource;
    }

    public void invalidate(final String path) {
        try {
            CachedResource resource = (CachedResource) getResource(path);
            if (resource != null) {
                resource.invalidate();
            }
        } catch (IOException e) {
            UndertowLogger.ROOT_LOGGER.debugf(e, "Exception invalidating cached resource %s", path);
        }
        cache.remove(path);
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
