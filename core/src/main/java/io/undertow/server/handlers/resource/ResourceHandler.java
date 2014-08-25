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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.server.handlers.encoding.ContentEncodedResource;
import io.undertow.server.handlers.encoding.ContentEncodedResourceManager;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.ETagUtils;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import io.undertow.util.RedirectBuilder;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
public class ResourceHandler implements HttpHandler {

    private final List<String> welcomeFiles = new CopyOnWriteArrayList<>(new String[]{"index.html", "index.htm", "default.html", "default.htm"});
    /**
     * If directory listing is enabled.
     */
    private volatile boolean directoryListingEnabled = false;
    /**
     * The mime mappings that are used to determine the content type.
     */
    private volatile MimeMappings mimeMappings = MimeMappings.DEFAULT;
    private volatile Predicate cachable = Predicates.truePredicate();
    private volatile Predicate allowed = Predicates.truePredicate();
    private volatile ResourceManager resourceManager;
    /**
     * If this is set this will be the maximum time the client will cache the resource.
     * <p/>
     * Note: Do not set this for private resources, as it will cause a Cache-Control: public
     * to be sent.
     * <p/>
     * TODO: make this more flexible
     * <p/>
     * This will only be used if the {@link #cachable} predicate returns true
     */
    private volatile Integer cacheTime;
    /**
     * we do not calculate a new expiry date every request. Instead calculate it once
     * and cache it until it is in the past.
     * <p/>
     * TODO: do we need this policy to be pluggable
     */
    private volatile long lastExpiryDate;
    private volatile String lastExpiryHeader;

    private volatile ContentEncodedResourceManager contentEncodedResourceManager;

    public ResourceHandler(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }


    /**
     * You should use {@link ResourceHandler(ResourceManager)} instead.
     */
    @Deprecated
    public ResourceHandler() {
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (exchange.getRequestMethod().equals(Methods.GET) ||
                exchange.getRequestMethod().equals(Methods.POST)) {
            serveResource(exchange, true);
        } else if (exchange.getRequestMethod().equals(Methods.HEAD)) {
            serveResource(exchange, false);
        } else {
            exchange.setResponseCode(405);
            exchange.endExchange();
        }
    }

    private void serveResource(final HttpServerExchange exchange, final boolean sendContent) {

        if (DirectoryUtils.sendRequestedBlobs(exchange)) {
            return;
        }

        if (!allowed.resolve(exchange)) {
            exchange.setResponseCode(403);
            exchange.endExchange();
            return;
        }

        ResponseCache cache = exchange.getAttachment(ResponseCache.ATTACHMENT_KEY);
        final boolean cachable = this.cachable.resolve(exchange);

        //we set caching headers before we try and serve from the cache
        if (cachable && cacheTime != null) {
            exchange.getResponseHeaders().put(Headers.CACHE_CONTROL, "public, max-age=" + cacheTime);
            if (System.currentTimeMillis() > lastExpiryDate) {
                long date = System.currentTimeMillis();
                lastExpiryHeader = DateUtils.toDateString(new Date(date));
                lastExpiryDate = date;
            }
            exchange.getResponseHeaders().put(Headers.EXPIRES, lastExpiryHeader);
        }

        if (cache != null && cachable) {
            if (cache.tryServeResponse()) {
                return;
            }
        }


        //we now dispatch to a worker thread
        //as resource manager methods are potentially blocking
        exchange.dispatch(new Runnable() {
            @Override
            public void run() {
                Resource resource = null;
                try {
                    resource = resourceManager.getResource(exchange.getRelativePath());
                } catch (IOException e) {
                    UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                    exchange.setResponseCode(500);
                    exchange.endExchange();
                    return;
                }
                if (resource == null) {
                    exchange.setResponseCode(404);
                    exchange.endExchange();
                    return;
                }

                if (resource.isDirectory()) {
                    Resource indexResource = null;
                    try {
                        indexResource = getIndexFiles(resourceManager, resource.getPath(), welcomeFiles);
                    } catch (IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        exchange.setResponseCode(500);
                        exchange.endExchange();
                        return;
                    }
                    if (indexResource == null) {
                        if (directoryListingEnabled) {
                            DirectoryUtils.renderDirectoryListing(exchange, resource);
                            return;
                        } else {
                            exchange.setResponseCode(StatusCodes.FORBIDDEN);
                            exchange.endExchange();
                            return;
                        }
                    } else if (!exchange.getRequestPath().endsWith("/")) {
                        exchange.setResponseCode(302);
                        exchange.getResponseHeaders().put(Headers.LOCATION, RedirectBuilder.redirect(exchange, exchange.getRelativePath() + "/", true));
                        exchange.endExchange();
                        return;
                    }
                    resource = indexResource;
                }

                final ETag etag = resource.getETag();
                final Date lastModified = resource.getLastModified();
                if (!ETagUtils.handleIfMatch(exchange, etag, false) ||
                        !DateUtils.handleIfUnmodifiedSince(exchange, lastModified)) {
                    exchange.setResponseCode(412);
                    exchange.endExchange();
                    return;
                }
                if (!ETagUtils.handleIfNoneMatch(exchange, etag, true) ||
                        !DateUtils.handleIfModifiedSince(exchange, lastModified)) {
                    exchange.setResponseCode(304);
                    exchange.endExchange();
                    return;
                }
                //todo: handle range requests
                //we are going to proceed. Set the appropriate headers
                final String contentType = resource.getContentType(mimeMappings);

                if(!exchange.getResponseHeaders().contains(Headers.CONTENT_TYPE)) {
                    if (contentType != null) {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
                    } else {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
                    }
                }
                if (lastModified != null) {
                    exchange.getResponseHeaders().put(Headers.LAST_MODIFIED, resource.getLastModifiedString());
                }
                if (etag != null) {
                    exchange.getResponseHeaders().put(Headers.ETAG, etag.toString());
                }
                Long contentLength = resource.getContentLength();
                if (contentLength != null) {
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, contentLength.toString());
                }

                final ContentEncodedResourceManager contentEncodedResourceManager = ResourceHandler.this.contentEncodedResourceManager;
                if (contentEncodedResourceManager != null) {
                    try {
                        ContentEncodedResource encoded = contentEncodedResourceManager.getResource(resource, exchange);
                        if (encoded != null) {
                            exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, encoded.getContentEncoding());
                            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, encoded.getResource().getContentLength());
                            encoded.getResource().serve(exchange.getResponseSender(), exchange, IoCallback.END_EXCHANGE);
                            return;
                        }

                    } catch (IOException e) {
                        //TODO: should this be fatal
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        exchange.setResponseCode(500);
                        exchange.endExchange();
                        return;
                    }
                }

                if (!sendContent) {
                    exchange.endExchange();
                } else {
                    resource.serve(exchange.getResponseSender(), exchange, IoCallback.END_EXCHANGE);
                }
            }
        });


    }

    private Resource getIndexFiles(ResourceManager resourceManager, final String base, List<String> possible) throws IOException {
        String realBase;
        if (base.endsWith("/")) {
            realBase = base;
        } else {
            realBase = base + "/";
        }
        for (String possibility : possible) {
            Resource index = resourceManager.getResource(realBase + possibility);
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    public boolean isDirectoryListingEnabled() {
        return directoryListingEnabled;
    }

    public ResourceHandler setDirectoryListingEnabled(final boolean directoryListingEnabled) {
        this.directoryListingEnabled = directoryListingEnabled;
        return this;
    }

    public ResourceHandler addWelcomeFiles(String... files) {
        this.welcomeFiles.addAll(Arrays.asList(files));
        return this;
    }

    public ResourceHandler setWelcomeFiles(String... files) {
        this.welcomeFiles.clear();
        this.welcomeFiles.addAll(Arrays.asList(files));
        return this;
    }

    public MimeMappings getMimeMappings() {
        return mimeMappings;
    }

    public ResourceHandler setMimeMappings(final MimeMappings mimeMappings) {
        this.mimeMappings = mimeMappings;
        return this;
    }

    public Predicate getCachable() {
        return cachable;
    }

    public ResourceHandler setCachable(final Predicate cachable) {
        this.cachable = cachable;
        return this;
    }

    public Predicate getAllowed() {
        return allowed;
    }

    public ResourceHandler setAllowed(final Predicate allowed) {
        this.allowed = allowed;
        return this;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public ResourceHandler setResourceManager(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        return this;
    }

    public Integer getCacheTime() {
        return cacheTime;
    }

    public ResourceHandler setCacheTime(final Integer cacheTime) {
        this.cacheTime = cacheTime;
        return this;
    }

    public ContentEncodedResourceManager getContentEncodedResourceManager() {
        return contentEncodedResourceManager;
    }

    public ResourceHandler setContentEncodedResourceManager(ContentEncodedResourceManager contentEncodedResourceManager) {
        this.contentEncodedResourceManager = contentEncodedResourceManager;
        return this;
    }



    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "resource";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("location", String.class);
            params.put("allow-listing", boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("location");
        }

        @Override
        public String defaultParameter() {
            return "location";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((String)config.get("location"), (Boolean) config.get("allow-listing"));
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final String location;
        private final boolean allowDirectoryListing;

        private Wrapper(String location, boolean allowDirectoryListing) {
            this.location = location;
            this.allowDirectoryListing = allowDirectoryListing;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            ResourceManager rm = new FileResourceManager(new File(location), 1024);
            ResourceHandler resourceHandler = new ResourceHandler(rm);
            resourceHandler.setDirectoryListingEnabled(allowDirectoryListing);
            return resourceHandler;
        }
    }
}
