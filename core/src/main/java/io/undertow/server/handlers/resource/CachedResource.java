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
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.LimitedBufferSlicePool;
import io.undertow.server.handlers.cache.ResponseCachingStreamSinkConduit;
import io.undertow.util.ConduitFactory;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import org.xnio.conduits.StreamSinkConduit;

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

    public CachedResource(final CachingResourceManager cachingResourceManager, final Resource underlyingResource, final String path) {
        this.cachingResourceManager = cachingResourceManager;
        this.underlyingResource = underlyingResource;
        this.contentLength = underlyingResource.getContentLength();
        this.directory = underlyingResource.isDirectory();
        this.lastModifiedDate = underlyingResource.getLastModified();
        if(lastModifiedDate != null) {
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

    @Override
    public void serve(final HttpServerExchange exchange) {
        final Long length = getContentLength();
        //if it is not eligable to be served from the cache
        if (length == null || length > cachingResourceManager.getMaxFileSize()) {
            underlyingResource.serve(exchange);
            return;
        }


        final DirectBufferCache dataCache = cachingResourceManager.getDataCache();
        if(dataCache == null) {
            underlyingResource.serve(exchange);
            return;
        }
        DirectBufferCache.CacheEntry existing = dataCache.get(cacheKey);
        //it is not cached yet, install a wrapper to grab the data
        if (existing == null || !existing.enabled() || !existing.reference()) {
            exchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
                @Override
                public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {

                    final DirectBufferCache.CacheEntry entry = dataCache.add(cacheKey, length.intValue());

                    if (entry == null || entry.buffers().length == 0 || !entry.claimEnable()) {
                        return factory.create();
                    }

                    if (!entry.reference()) {
                        entry.disable();
                        return factory.create();
                    }

                    return new ResponseCachingStreamSinkConduit(factory.create(), entry, length);
                }
            });
            underlyingResource.serve(exchange);
            return;
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
            exchange.getResponseSender().send(buffers, new DereferenceCallback(existing));
        }
    }

    @Override
    public Long getContentLength() {
        return contentLength;
    }

    @Override
    public Resource getIndexResource(final List<String> possible) {
        for (final String p : possible) {
            try {
                Resource res = cachingResourceManager.getResource(this.path + p);
                if (res != null) {
                    return res;
                }
            } catch (IOException e) {
                UndertowLogger.ROOT_LOGGER.debugf(e, "Exception getting resource %s", this.path + p);
            }
        }
        return null;
    }

    @Override
    public String getCacheKey() {
        return cacheKey;
    }

    @Override
    public Path getFile() {
        return underlyingResource.getFile();
    }

    @Override
    public URL getUrl() {
        return underlyingResource.getUrl();
    }


    private static class DereferenceCallback implements IoCallback {
        private final DirectBufferCache.CacheEntry cache;

        public DereferenceCallback(DirectBufferCache.CacheEntry cache) {
            this.cache = cache;
        }

        @Override
        public void onComplete(final HttpServerExchange exchange, final Sender sender) {
            cache.dereference();
        }

        @Override
        public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
            cache.dereference();
        }
    }
}
