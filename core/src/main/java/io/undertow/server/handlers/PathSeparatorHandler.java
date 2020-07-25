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

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static io.undertow.util.CanonicalPathUtils.canonicalize;

/**
 * A handler that translates non slash separator characters in the URL into a slash.
 *
 * In general this will translate backslash into slash on windows systems.
 *
 * @author Stuart Douglas
 */
public class PathSeparatorHandler implements HttpHandler {

    private final HttpHandler next;

    public PathSeparatorHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        boolean handlingRequired = File.separatorChar != '/';
        if (handlingRequired) {
            exchange.setRequestPath(canonicalize(exchange.getRequestPath().replace(File.separatorChar, '/')));
            exchange.setRelativePath(canonicalize(exchange.getRelativePath().replace(File.separatorChar, '/')));
            exchange.setResolvedPath(canonicalize(exchange.getResolvedPath().replace(File.separatorChar, '/')));
        }
        next.handleRequest(exchange);
    }

    @Override
    public String toString() {
        return "path-separator()";
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "path-separator";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper();
        }

    }

    private static class Wrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new PathSeparatorHandler(handler);
        }
    }
}
