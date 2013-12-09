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

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;

import io.undertow.Handlers;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;

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

    private volatile HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_404;
    private final ConcurrentMap<String, HttpHandler> paths = new CopyOnWriteMap<String, HttpHandler>();
    private final ConcurrentMap<String, HttpHandler> exactPathMatches = new CopyOnWriteMap<String, HttpHandler>();

    /**
     * lengths of all registered paths
     */
    private volatile int[] lengths = {};

    public PathHandler(final HttpHandler defaultHandler) {
        this.defaultHandler = defaultHandler;
    }

    public PathHandler() {
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final String path = exchange.getRelativePath();
        if (!exactPathMatches.isEmpty()) {
            HttpHandler match = exactPathMatches.get(path);
            if (match != null) {
                exchange.setRelativePath("");
                exchange.setResolvedPath(exchange.getResolvedPath() + path);
                match.handleRequest(exchange);
                return;
            }
        }

        int length = path.length();
        final int[] lengths = this.lengths;
        for (int i = 0; i < lengths.length; ++i) {
            int pathLength = lengths[i];
            if (pathLength == length) {
                HttpHandler next = paths.get(path);
                if (next != null) {
                    exchange.setRelativePath(path.substring(pathLength));
                    exchange.setResolvedPath(exchange.getResolvedPath() + path);
                    next.handleRequest(exchange);
                    return;
                }
            } else if (pathLength < length) {
                char c = path.charAt(pathLength);
                if (c == '/') {
                    String part = path.substring(0, pathLength);
                    HttpHandler next = paths.get(part);
                    if (next != null) {
                        exchange.setRelativePath(path.substring(pathLength));
                        exchange.setResolvedPath(exchange.getResolvedPath() + part);
                        next.handleRequest(exchange);
                        return;
                    }
                }
            }
        }
        defaultHandler.handleRequest(exchange);
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
        if (path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }
        if (path.equals("/")) {
            this.defaultHandler = handler;
            return this;
        }
        if (path.charAt(0) != '/') {
            paths.put("/" + path, handler);
        } else {
            paths.put(path, handler);
        }
        buildLengths();
        return this;
    }


    public synchronized PathHandler addExactPath(final String path, final HttpHandler handler) {
        Handlers.handlerNotNull(handler);
        if (path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }
        if (path.charAt(0) != '/') {
            exactPathMatches.put("/" + path, handler);
        } else {
            exactPathMatches.put(path, handler);
        }
        return this;
    }

    private void buildLengths() {
        final Set<Integer> lengths = new TreeSet<Integer>(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -o1.compareTo(o2);
            }
        });
        for (String p : paths.keySet()) {
            lengths.add(p.length());
        }

        int[] lengthArray = new int[lengths.size()];
        int pos = 0;
        for (int i : lengths) {
            lengthArray[pos++] = i;
        }
        this.lengths = lengthArray;
    }

    @Deprecated
    public synchronized PathHandler removePath(final String path) {
        return removePrefixPath(path);
    }

    public synchronized PathHandler removePrefixPath(final String path) {
        if (path == null || path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }

        if (path.equals("/")) {
            defaultHandler = ResponseCodeHandler.HANDLE_404;
            return this;
        }

        if (path.charAt(0) != '/') {
            paths.remove("/" + path);
        } else {
            paths.remove(path);
        }
        buildLengths();
        return this;
    }

    public synchronized PathHandler removeExactPath(final String path) {
        if (path == null || path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }
        if (path.charAt(0) != '/') {
            exactPathMatches.remove("/" + path);
        } else {
            exactPathMatches.remove(path);
        }
        return this;
    }

    public synchronized PathHandler clearPaths() {
        paths.clear();
        exactPathMatches.clear();
        this.lengths = new int[0];
        defaultHandler = ResponseCodeHandler.HANDLE_404;
        return this;
    }

    public Map<String, HttpHandler> getPaths() {
        return Collections.unmodifiableMap(paths);
    }
}
