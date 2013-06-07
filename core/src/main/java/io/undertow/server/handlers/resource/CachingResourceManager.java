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

import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.LRUCache;

/**
 * @author Stuart Douglas
 */
public class CachingResourceManager implements ResourceManager {

    private static final Object NO_RESOURCE = new Object();

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

    public CachingResourceManager(final int metadataCacheSize, final long maxFileSize, final DirectBufferCache dataCache, final ResourceManager underlyingResourceManager, final int metadataCacheMaxAge) {
        this.maxFileSize = maxFileSize;
        this.underlyingResourceManager = underlyingResourceManager;
        this.dataCache = dataCache;
        this.cache = new LRUCache<String, Object>(metadataCacheSize, metadataCacheMaxAge);
    }

    @Override
    public Resource getResource(final String path) throws IOException {
        Object res = cache.get(path);
        if (res == NO_RESOURCE) {
            return null;
        } else if (res != null) {
            return (Resource) res;
        }
        final Resource underlying = underlyingResourceManager.getResource(path);
        if(underlying == null) {
            cache.add(path, NO_RESOURCE);
            return null;
        }
        final CachedResource resource = new CachedResource(this, underlying, path);
        cache.add(path, resource);
        return resource;
    }

    DirectBufferCache getDataCache() {
        return dataCache;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }
}
