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

package io.undertow.servlet.handlers;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Handler that marks a request as secure, regardless of the underlying protocol.
 *
 * @author Stuart Douglas
 */
public class MarkSecureHandler implements HttpHandler  {

    public static final HandlerWrapper WRAPPER = new Wrapper();

    private final HttpHandler next;

    public MarkSecureHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.putAttachment(HttpServerExchange.SECURE_REQUEST, Boolean.TRUE);
        next.handleRequest(exchange);
    }

    public static class Wrapper implements HandlerWrapper {

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new MarkSecureHandler(handler);
        }
    }

    @Override
    public String toString() {
        return "mark-secure()";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "mark-secure";
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
            return WRAPPER;
        }
    }
}
