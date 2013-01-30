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
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

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
 * This handler can only match a single part of this request (namely /foo). To match the full path
 * two of these handlers must be chained together.
 *
 * Note that
 *
 * @author Stuart Douglas
 */
public class PathHandler implements HttpHandler {

    private volatile HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_404;
    private final ConcurrentMap<String, HttpHandler> paths = new CopyOnWriteMap<String, HttpHandler>();

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        int pos = 0;
        final String path = exchange.getRelativePath();
        final int length = path.length();

        while (pos < length) {
            if(path.charAt(pos) == '/' && pos != 0) {
                break;
            }
            ++pos;
        }
        final String part = path.substring(0, pos);
        final HttpHandler next = paths.get(part);
        if(next != null) {
            exchange.setRelativePath(path.substring(pos));
            exchange.setResolvedPath(exchange.getResolvedPath() + part);
            HttpHandlers.executeHandler(next, exchange);
        } else {
            HttpHandlers.executeHandler(defaultHandler, exchange);
        }
    }

    public HttpHandler getDefaultHandler() {
        return defaultHandler;
    }

    public void setDefaultHandler(HttpHandler defaultHandler) {
        HttpHandlers.handlerNotNull(defaultHandler);
        this.defaultHandler = defaultHandler;
    }

    /**
     * Adds a path and a handler for that path. If the path does not start
     * with a / then one will be prepended
     * @param path The path
     * @param handler The handler
     */
    public void addPath(final String path, final HttpHandler handler) {
        HttpHandlers.handlerNotNull(handler);
        if(path == null || path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }
        if(path.charAt(0) != '/') {
            paths.put("/" + path, handler);
        } else {
            paths.put(path, handler);
        }
    }

    public void removePath(final String path) {
        if(path == null || path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }
        if(path.charAt(0) != '/') {
            paths.remove("/" + path);
        } else {
            paths.remove(path);
        }
    }

    public void clearPaths() {
        paths.clear();
    }

    public Map<String, HttpHandler> getPaths() {
        return Collections.unmodifiableMap(paths);
    }
}
