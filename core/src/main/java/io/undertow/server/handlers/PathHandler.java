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

package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.LRUCache;
import io.undertow.util.PathMatcher;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handler that dispatches to a given handler based of a prefix match of the path.
 * <p>
 * This only matches a single level of a request, e.g if you have a request that takes the form:
 * <p>
 * /foo/bar
 * <p>
 *
 * @author Stuart Douglas
 */
public class PathHandler implements HttpHandler {

    private final PathMatcher<HttpHandler> pathMatcher = new PathMatcher<>();

    private final LRUCache<String, PathMatcher.PathMatch<HttpHandler>> cache;

    public PathHandler(final HttpHandler defaultHandler) {
        this(0);
        pathMatcher.addPrefixPath("/", defaultHandler);
    }

    public PathHandler(final HttpHandler defaultHandler, int cacheSize) {
        this(cacheSize);
        pathMatcher.addPrefixPath("/", defaultHandler);
    }

    public PathHandler() {
        this(0);
    }

    public PathHandler(int cacheSize) {
        if(cacheSize > 0) {
            cache = new LRUCache<>(cacheSize, -1, true);
        } else {
            cache = null;
        }
    }

    @Override
    public String toString() {
        Set<Entry<String,HttpHandler>> paths = pathMatcher.getPaths().entrySet();
        if (paths.size() == 1) {
            return "path( " + paths.toArray()[0] + " )";
        } else {
            return "path( {" + paths.stream().map(s -> s.getValue().toString()).collect(Collectors.joining(", ")) + "} )";
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        PathMatcher.PathMatch<HttpHandler> match = null;
        boolean hit = false;
        if(cache != null) {
            match = cache.get(exchange.getRelativePath());
            hit = true;
        }
        if(match == null) {
            match = pathMatcher.match(exchange.getRelativePath());
        }
        if (match.getValue() == null) {
            ResponseCodeHandler.HANDLE_404.handleRequest(exchange);
            return;
        }
        if(hit) {
            cache.add(exchange.getRelativePath(), match);
        }
        exchange.setRelativePath(match.getRemaining());
        if(exchange.getResolvedPath().isEmpty()) {
            //first path handler, we can just use the matched part
            exchange.setResolvedPath(match.getMatched());
        } else {
            //already something in the resolved path
            exchange.setResolvedPath(exchange.getResolvedPath() + match.getMatched());
        }
        match.getValue().handleRequest(exchange);
    }

    /**
     * Adds a path prefix and a handler for that path. If the path does not start
     * with a / then one will be prepended.
     * <p>
     * The match is done on a prefix bases, so registering /foo will also match /foo/bar. Exact
     * path matches are taken into account first.
     * <p>
     * If / is specified as the path then it will replace the default handler.
     *
     * @param path    The path
     * @param handler The handler
     * @see #addPrefixPath(String, io.undertow.server.HttpHandler)
     * @deprecated Superseded by {@link #addPrefixPath(String, io.undertow.server.HttpHandler)}.
     */
    @Deprecated(since="1.0.0", forRemoval=true)
    public synchronized PathHandler addPath(final String path, final HttpHandler handler) {
        return addPrefixPath(path, handler);
    }

    /**
     * Adds a path prefix and a handler for that path. If the path does not start
     * with a / then one will be prepended.
     * <p>
     * The match is done on a prefix bases, so registering /foo will also match /foo/bar.
     * Though exact path matches are taken into account before prefix path matches. So
     * if an exact path match exists its handler will be triggered.
     * <p>
     * If / is specified as the path then it will replace the default handler.
     *
     * @param path    If the request contains this prefix, run handler.
     * @param handler The handler which is activated upon match.
     * @return The resulting PathHandler after this path has been added to it.
     */
    public synchronized PathHandler addPrefixPath(final String path, final HttpHandler handler) {
        Handlers.handlerNotNull(handler);
        pathMatcher.addPrefixPath(path, handler);
        return this;
    }

    /**
     * If the request path is exactly equal to the given path, run the handler.
     * <p>
     * Exact paths are prioritized higher than prefix paths.
     *
     * @param path If the request path is exactly this, run handler.
     * @param handler Handler run upon exact path match.
     * @return The resulting PathHandler after this path has been added to it.
     */
    public synchronized PathHandler addExactPath(final String path, final HttpHandler handler) {
        Handlers.handlerNotNull(handler);
        pathMatcher.addExactPath(path, handler);
        return this;
    }

    @Deprecated
    public synchronized PathHandler removePath(final String path) {
        return removePrefixPath(path);
    }

    public synchronized PathHandler removePrefixPath(final String path) {
        pathMatcher.removePrefixPath(path);
        return this;
    }

    public synchronized PathHandler removeExactPath(final String path) {
        pathMatcher.removeExactPath(path);
        return this;
    }

    public synchronized PathHandler clearPaths() {
        pathMatcher.clearPaths();
        return this;
    }
}
