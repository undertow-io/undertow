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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;

/**
 * A {@link HttpHandler} that initiates a blocking request. If the thread is currently running
 * in the io thread it will be dispatched.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BlockingHandler implements HttpHandler {

    private volatile HttpHandler handler;

    public BlockingHandler(final HttpHandler handler) {
        this.handler = handler;
    }

    public BlockingHandler() {
        this(null);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {

        exchange.startBlocking();
        if (exchange.isInIoThread()) {
            exchange.dispatch(handler);
        } else {
            handler.handleRequest(exchange);
        }
    }

    public HttpHandler getHandler() {
        return handler;
    }

    public BlockingHandler setRootHandler(final HttpHandler rootHandler) {
        this.handler = rootHandler;
        return this;
    }

    @Override
    public String toString() {
        return "blocking()";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "blocking";
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
            return new BlockingHandler(handler);
        }
    }
}
