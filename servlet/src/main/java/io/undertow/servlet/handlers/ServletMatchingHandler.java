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


    private volatile int cacheSize = 1024;

    private volatile ServletPathMatches paths;

    /**
     * Caches an exact match that does not return an error code, to allow for faster matching of paths.
     * <p/>
     * If cache size is set to zero this is not used
     */
    private volatile ConcurrentMap<String, HttpHandler> cache = new BoundedConcurrentHashMap<String, HttpHandler>(cacheSize);

    public ServletMatchingHandler(final ServletPathMatches paths) {
        this.paths = paths;
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
        invokeAndCache(path, paths.getServletHandler(path), exchange, completionHandler);
    }


    private void invokeAndCache(final String path, final HttpHandler handler, final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        if (cacheSize != 0) {
            cache.put(path, handler);
        }
        HttpHandlers.executeHandler(handler, exchange, completionHandler);
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(final int cacheSize) {
        this.cache = new BoundedConcurrentHashMap<String, HttpHandler>(cacheSize);
        this.cacheSize = cacheSize;
    }

    public ServletPathMatches getPaths() {
        return paths;
    }

    public void setPaths(final ServletPathMatches paths) {
        this.paths = paths;
    }

}
