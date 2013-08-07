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

    private final String cacheKey;
    private final CachingResourceManager cachingResourceManager;
    private final Resource underlyingResource;
    private final String path;
    private final Long contentLength;
    private final boolean directory;
    private final Date lastModifiedDate;
    private final String lastModifiedDateString;
    private final ETag eTag;
    private final String name;
    private volatile long nextMaxAgeCheck;

    public CachedResource(final CachingResourceManager cachingResourceManager, final Resource underlyingResource, final String path) {
        this.cachingResourceManager = cachingResourceManager;
        this.underlyingResource = underlyingResource;
        this.contentLength = underlyingResource.getContentLength();
        this.directory = underlyingResource.isDirectory();
        this.lastModifiedDate = underlyingResource.getLastModified();
        if (lastModifiedDate != null) {
            this.lastModifiedDateString = DateUtils.toDateString(lastModifiedDate);
        } else {
            this.lastModifiedDateString = null;
        }
        this.eTag = underlyingResource.getETag();
        this.name = underlyingResource.getName();
        if (this.directory && !path.endsWith("/")) {
            this.path = path + "/";
        } else {
            this.path = path;
        }
        this.cacheKey = underlyingResource.getCacheKey();
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
        cachingResourceManager.getDataCache().remove(cacheKey);
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
        final Long length = getContentLength();
        //if it is not eligable to be served from the cache
        if (length == null || length > cachingResourceManager.getMaxFileSize()) {
            underlyingResource.serve(sender, exchange, completionCallback);
            return;
        }


        final DirectBufferCache dataCache = cachingResourceManager.getDataCache();
        if (dataCache == null) {
            underlyingResource.serve(sender, exchange, completionCallback);
            return;
        }
        final DirectBufferCache.CacheEntry existing = dataCache.get(cacheKey);
        //it is not cached yet, install a wrapper to grab the data
        if (existing == null || !existing.enabled() || !existing.reference()) {
            Sender newSender = sender;

            final DirectBufferCache.CacheEntry entry;
            if (existing == null) {
                entry = dataCache.add(cacheKey, length.intValue());
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
        return contentLength;
    }

    @Override
    public String getCacheKey() {
        return cacheKey;
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


}
