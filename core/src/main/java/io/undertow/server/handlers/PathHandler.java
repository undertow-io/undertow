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

package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathMatcher;

/**
 * Handler that dispatches to a given handler based of a prefix match of the path.
 * <p/>
 * This only matches a single level of a request, e.g if you have a request that takes the form:
 * <p/>
 * /foo/bar
 * <p/>
 *
 * @author Stuart Douglas
 */
public class PathHandler implements HttpHandler {

    private final PathMatcher<HttpHandler> pathMatcher = new PathMatcher<HttpHandler>();

    public PathHandler(final HttpHandler defaultHandler) {
        pathMatcher.addPrefixPath("/", defaultHandler);
    }

    public PathHandler() {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final PathMatcher.PathMatch<HttpHandler> match = pathMatcher.match(exchange);
        if(match.getValue() == null) {
            ResponseCodeHandler.HANDLE_404.handleRequest(exchange);
            return;
        }
        exchange.setRelativePath(match.getRemaining());
        exchange.setResolvedPath(exchange.getRequestPath().substring(0, exchange.getRequestPath().length() - match.getRemaining().length()));
        match.getValue().handleRequest(exchange);
    }

    /**
     * Adds a path prefix and a handler for that path. If the path does not start
     * with a / then one will be prepended.
     * <p/>
     * The match is done on a prefix bases, so registering /foo will also match /bar. Exact
     * path matches are taken into account first.
     * <p/>
     * If / is specified as the path then it will replace the default handler.
     *
     * @param path    The path
     * @param handler The handler
     * @see #addPrefixPath(String, io.undertow.server.HttpHandler)
     * @deprecated
     */
    @Deprecated
    public synchronized PathHandler addPath(final String path, final HttpHandler handler) {
        return addPrefixPath(path, handler);
    }

    /**
     * Adds a path prefix and a handler for that path. If the path does not start
     * with a / then one will be prepended.
     * <p/>
     * The match is done on a prefix bases, so registering /foo will also match /bar. Exact
     * path matches are taken into account first.
     * <p/>
     * If / is specified as the path then it will replace the default handler.
     *
     * @param path    The path
     * @param handler The handler
     */
    public synchronized PathHandler addPrefixPath(final String path, final HttpHandler handler) {
        Handlers.handlerNotNull(handler);
        pathMatcher.addPrefixPath(path, handler);
        return this;
    }


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
