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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.server.handlers.encoding.ContentEncodedResource;
import io.undertow.server.handlers.encoding.ContentEncodedResourceManager;
import io.undertow.util.ByteRange;
import io.undertow.util.CanonicalPathUtils;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.ETagUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.MimeMappings;
import io.undertow.util.RedirectBuilder;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
public class ResourceHandler extends AbstractResourceHandler implements HttpHandler {

    /**
     * Set of methods prescribed by HTTP 1.1. If request method is not one of those, handler will
     * return NOT_IMPLEMENTED.
     */
    private static final Set<HttpString> KNOWN_METHODS = new HashSet<>();

    static {
        KNOWN_METHODS.add(Methods.OPTIONS);
        KNOWN_METHODS.add(Methods.GET);
        KNOWN_METHODS.add(Methods.HEAD);
        KNOWN_METHODS.add(Methods.POST);
        KNOWN_METHODS.add(Methods.PUT);
        KNOWN_METHODS.add(Methods.DELETE);
        KNOWN_METHODS.add(Methods.TRACE);
        KNOWN_METHODS.add(Methods.CONNECT);
    }

    private final List<String> welcomeFiles = new CopyOnWriteArrayList<>(new String[]{"index.html", "index.htm", "default.html", "default.htm"});

    private volatile ResourceManager resourceManager;

    /**
     * Handler that is called if no resource is found
     */
    private final HttpHandler next;

    public ResourceHandler(ResourceManager resourceManager) {
        this(resourceManager, ResponseCodeHandler.HANDLE_404);
    }

    public ResourceHandler(ResourceManager resourceManager, HttpHandler next) {
        this.resourceManager = resourceManager;
        this.next = next;
    }


    /**
     * You should use {@link ResourceHandler(ResourceManager)} instead.
     */
    @Deprecated
    public ResourceHandler() {
        this.next = ResponseCodeHandler.HANDLE_404;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (exchange.getRequestMethod().equals(Methods.GET) ||
                exchange.getRequestMethod().equals(Methods.POST)) {
            serveResource(exchange, true);
        } else if (exchange.getRequestMethod().equals(Methods.HEAD)) {
            serveResource(exchange, false);
        } else {
            if (KNOWN_METHODS.contains(exchange.getRequestMethod())) {
                exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
                exchange.getResponseHeaders().add(Headers.ALLOW,
                        String.join(", ", Methods.GET_STRING, Methods.HEAD_STRING, Methods.POST_STRING));
            } else {
                exchange.setStatusCode(StatusCodes.NOT_IMPLEMENTED);
            }
            exchange.endExchange();
        }
    }

    @Override
    public ResourceHandler setDirectoryListingEnabled(final boolean directoryListingEnabled) {
        super.setDirectoryListingEnabled(directoryListingEnabled);
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

    @Override
    public ResourceHandler setMimeMappings(final MimeMappings mimeMappings) {
        super.setMimeMappings(mimeMappings);
        return this;
    }

    @Override
    public ResourceHandler setCachable(final Predicate cachable) {
        super.setCachable(cachable);
        return this;
    }

    @Override
    public ResourceHandler setAllowed(final Predicate allowed) {
        super.setAllowed(allowed);
        return this;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public ResourceHandler setResourceManager(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        return this;
    }

    @Override
    public ResourceHandler setCacheTime(final Integer cacheTime) {
        super.setCacheTime(cacheTime);
        return this;
    }

    @Override
    public ResourceHandler setContentEncodedResourceManager(ContentEncodedResourceManager contentEncodedResourceManager) {
        super.setContentEncodedResourceManager(contentEncodedResourceManager);
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
            ResourceManager rm = new PathResourceManager(Paths.get(location), 1024);
            ResourceHandler resourceHandler = new ResourceHandler(rm);
            resourceHandler.setDirectoryListingEnabled(allowDirectoryListing);
            return resourceHandler;
        }
    }

    @Override
    protected Resource resolveResource(HttpServerExchange exchange, String path) throws IOException {
        return resourceManager.getResource(path);
    }

    @Override
    protected HttpHandler getNext() {
        return next;
    }

    @Override
    protected Resource getIndexFiles(final String base) throws IOException {
        String realBase;
        if (base.endsWith("/")) {
            realBase = base;
        } else {
            realBase = base + "/";
        }
        for (String possibility : welcomeFiles) {
            Resource index = resourceManager.getResource(canonicalize(realBase + possibility));
            if (index != null) {
                return index;
            }
        }
        return null;
    }
}
