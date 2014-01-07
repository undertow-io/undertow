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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.LimitedBufferSlicePool;
import io.undertow.server.handlers.cache.ResponseCachingSender;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;

/**
 * @author Stuart Douglas
 */
public class CachedResource implements Resource {

    private final CacheKey cacheKey;
    private final CachingResourceManager cachingResourceManager;
    private final Resource underlyingResource;
    private final boolean directory;
    private final Date lastModifiedDate;
    private final String lastModifiedDateString;
    private final ETag eTag;
    private final String name;
    private volatile long nextMaxAgeCheck;

    public CachedResource(final CachingResourceManager cachingResourceManager, final Resource underlyingResource, final String path) {
        this.cachingResourceManager = cachingResourceManager;
        this.underlyingResource = underlyingResource;
        this.directory = underlyingResource.isDirectory();
        this.lastModifiedDate = underlyingResource.getLastModified();
        if (lastModifiedDate != null) {
            this.lastModifiedDateString = DateUtils.toDateString(lastModifiedDate);
        } else {
            this.lastModifiedDateString = null;
        }
        this.eTag = underlyingResource.getETag();
        this.name = underlyingResource.getName();
        this.cacheKey = new CacheKey(cachingResourceManager, underlyingResource.getCacheKey());
        if (cachingResourceManager.getMaxAge() > 0) {
            nextMaxAgeCheck = System.currentTimeMillis() + cachingResourceManager.getMaxAge();
        } else {
            nextMaxAgeCheck = -1;
        }
    }

    @Override
    public String getPath() {
        return underlyingResource.getPath();
    }

    @Override
    public Date getLastModified() {
        return lastModifiedDate;
    }

    @Override
    public String getLastModifiedString() {
        return lastModifiedDateString;
    }

    @Override
    public ETag getETag() {
        return eTag;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public List<Resource> list() {
        return underlyingResource.list();
    }

    @Override
    public String getContentType(final MimeMappings mimeMappings) {
        return underlyingResource.getContentType(mimeMappings);
    }

    public void invalidate() {
        final DirectBufferCache dataCache = cachingResourceManager.getDataCache();
        if(dataCache != null) {
            dataCache.remove(cacheKey);
        }
    }

    public boolean checkStillValid() {
        if (nextMaxAgeCheck > 0) {
            long time = System.currentTimeMillis();
            if (time > nextMaxAgeCheck) {
                nextMaxAgeCheck = time + cachingResourceManager.getMaxAge();
                if (!underlyingResource.getLastModified().equals(lastModifiedDate)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void serve(final Sender sender, final HttpServerExchange exchange, final IoCallback completionCallback) {
        final DirectBufferCache dataCache = cachingResourceManager.getDataCache();
        if(dataCache == null) {
            underlyingResource.serve(sender, exchange, completionCallback);
            return;
        }

        final DirectBufferCache.CacheEntry existing = dataCache.get(cacheKey);
        final Long length = getContentLength();
        //if it is not eligible to be served from the cache
        if (length == null || length > cachingResourceManager.getMaxFileSize()) {
            underlyingResource.serve(sender, exchange, completionCallback);
            return;
        }
        //it is not cached yet, install a wrapper to grab the data
        if (existing == null || !existing.enabled() || !existing.reference()) {
            Sender newSender = sender;

            final DirectBufferCache.CacheEntry entry;
            if (existing == null) {
                entry = dataCache.add(cacheKey, length.intValue(), cachingResourceManager.getMaxAge());
            } else {
                entry = existing;
            }

            if (entry != null && entry.buffers().length != 0 && entry.claimEnable()) {
                if (entry.reference()) {
                    newSender = new ResponseCachingSender(sender, entry, length);
                } else {
                    entry.disable();
                }
            }
            underlyingResource.serve(newSender, exchange, completionCallback);
        } else {
            //serve straight from the cache
            ByteBuffer[] buffers;
            boolean ok = false;
            try {
                LimitedBufferSlicePool.PooledByteBuffer[] pooled = existing.buffers();
                buffers = new ByteBuffer[pooled.length];
                for (int i = 0; i < buffers.length; i++) {
                    // Keep position from mutating
                    buffers[i] = pooled[i].getResource().duplicate();
                }
                ok = true;
            } finally {
                if (!ok) {
                    existing.dereference();
                }
            }
            sender.send(buffers, new DereferenceCallback(existing, completionCallback));
        }
    }

    @Override
    public Long getContentLength() {
        //we always use the underlying size unless the data is cached in the buffer cache
        //to prevent a mis-match between size on disk and cached size
        final DirectBufferCache dataCache = cachingResourceManager.getDataCache();
        if(dataCache == null) {
            return underlyingResource.getContentLength();
        }
        final DirectBufferCache.CacheEntry existing = dataCache.get(cacheKey);
        if(existing == null || !existing.enabled()) {
            return underlyingResource.getContentLength();
        }
        //we only return the
        return (long)existing.size();
    }

    @Override
    public String getCacheKey() {
        return cacheKey.cacheKey;
    }

    @Override
    public File getFile() {
        return underlyingResource.getFile();
    }

    @Override
    public File getResourceManagerRoot() {
        return underlyingResource.getResourceManagerRoot();
    }

    @Override
    public URL getUrl() {
        return underlyingResource.getUrl();
    }


    private static class DereferenceCallback implements IoCallback {

        private final DirectBufferCache.CacheEntry cache;
        private final IoCallback callback;

        public DereferenceCallback(DirectBufferCache.CacheEntry cache, final IoCallback callback) {
            this.cache = cache;
            this.callback = callback;
        }

        @Override
        public void onComplete(final HttpServerExchange exchange, final Sender sender) {
            try {
                cache.dereference();
            } finally {
                callback.onComplete(exchange, sender);
            }
        }

        @Override
        public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
            try {
                cache.dereference();
            } finally {
                callback.onException(exchange, sender, exception);
            }
        }
    }


    static final class CacheKey {
        final CachingResourceManager manager;
        final String cacheKey;

        CacheKey(CachingResourceManager manager, String cacheKey) {
            this.manager = manager;
            this.cacheKey = cacheKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey1 = (CacheKey) o;

            if (cacheKey != null ? !cacheKey.equals(cacheKey1.cacheKey) : cacheKey1.cacheKey != null) return false;
            if (manager != null ? !manager.equals(cacheKey1.manager) : cacheKey1.manager != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = manager != null ? manager.hashCode() : 0;
            result = 31 * result + (cacheKey != null ? cacheKey.hashCode() : 0);
            return result;
        }
    }

}
