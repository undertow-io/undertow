/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.handlers;

import java.util.concurrent.ConcurrentMap;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.BoundedConcurrentHashMap;
import io.undertow.util.CopyOnWriteMap;

/**
 * Handler that resolves servlet paths
 *
 * @author Stuart Douglas
 */
public class ServletMatchingHandler implements HttpHandler {

    private final ConcurrentMap<String, PathMatch> exactPathMatches = new CopyOnWriteMap<String, PathMatch>();

    private final ConcurrentMap<String, PathMatch> prefixMatches = new CopyOnWriteMap<String, PathMatch>();

    private volatile int cacheSize = 1024;

    /**
     * Caches an exact match that does not return an error code, to allow for faster matching of paths.
     * <p/>
     * If cache size is set to zero this is not used
     */
    private volatile ConcurrentMap<String, HttpHandler> cache = new BoundedConcurrentHashMap<String, HttpHandler>(cacheSize);

    private volatile HttpHandler defaultHandler;

    public ServletMatchingHandler(final HttpHandler defaultHandler) {
        this.defaultHandler = defaultHandler;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final boolean cacheEnabled = cacheSize > 0;
        if (cacheEnabled) {
            final HttpHandler handler = cache.get(exchange.getRelativePath());
            if (handler != null) {
                HttpHandlers.executeHandler(handler, exchange, completionHandler);
                return;
            }
        }
        final String path = exchange.getRelativePath();
        PathMatch match = exactPathMatches.get(path);
        if (match != null) {
            handleMatch(exchange, completionHandler, path, match);
            return;
        }
        match = prefixMatches.get(path);
        if (match != null) {
            handleMatch(exchange, completionHandler, path, match);
            return;
        }
        for (int i = path.length() -1; i >= 0; --i) {
            if (path.charAt(i) == '/') {
                final String part = path.substring(0, i);
                match = prefixMatches.get(part);
                if (match != null) {
                    handleMatch(exchange, completionHandler, path, match);
                    return;
                }
            }
        }
        HttpHandlers.executeHandler(defaultHandler, exchange, completionHandler);
    }

    private void handleMatch(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler, final String path, final PathMatch match) {
        if (match.extensionMatches.isEmpty()) {
            invokeAndCache(path, match.defaultHandler, exchange, completionHandler);
        } else {
            int c = path.lastIndexOf('.');
            if (c == -1) {
                invokeAndCache(path, match.defaultHandler, exchange, completionHandler);
            } else {
                final String ext = path.substring(c + 1, path.length());
                HttpHandler handler = match.extensionMatches.get(ext);
                if (handler != null) {
                    invokeAndCache(path, handler, exchange, completionHandler);
                } else {
                    invokeAndCache(path, match.defaultHandler, exchange, completionHandler);
                }
            }
        }
    }

    private void invokeAndCache(final String path, final HttpHandler handler, final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        if (cacheSize != 0) {
            cache.put(path, handler);
        }
        if (handler == null) {
            HttpHandlers.executeHandler(defaultHandler, exchange, completionHandler);
        } else {
            HttpHandlers.executeHandler(handler, exchange, completionHandler);
        }
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(final int cacheSize) {
        this.cache = new BoundedConcurrentHashMap<String, HttpHandler>(cacheSize);
        this.cacheSize = cacheSize;
    }

    public HttpHandler getDefaultHandler() {
        return defaultHandler;
    }

    public void setDefaultHandler(final HttpHandler defaultHandler) {
        HttpHandlers.handlerNotNull(defaultHandler);
        this.defaultHandler = defaultHandler;
    }

    public ConcurrentMap<String, PathMatch> getPrefixMatches() {
        return prefixMatches;
    }

    public ConcurrentMap<String, PathMatch> getExactPathMatches() {
        return exactPathMatches;
    }

    public static class PathMatch {

        private final ConcurrentMap<String, HttpHandler> extensionMatches = new CopyOnWriteMap<String, HttpHandler>();
        private volatile HttpHandler defaultHandler;

        public PathMatch(final HttpHandler defaultHandler) {
            this.defaultHandler = defaultHandler;
        }

        public ConcurrentMap<String, HttpHandler> getExtensionMatches() {
            return extensionMatches;
        }

        public HttpHandler getDefaultHandler() {
            return defaultHandler;
        }

    }

}
