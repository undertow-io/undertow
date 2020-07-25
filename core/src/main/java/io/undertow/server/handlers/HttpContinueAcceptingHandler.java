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

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Handler that provides support for HTTP/1.1 continue responses.
 * <p>
 * If the provided predicate returns <code>true</code> then the request will be
 * accepted, otherwise it will be rejected.
 *
 * If no predicate is supplied then all requests will be accepted.
 *
 * @see io.undertow.server.protocol.http.HttpContinue
 * @author Stuart Douglas
 */
public class HttpContinueAcceptingHandler implements HttpHandler {

    private final HttpHandler next;
    private final Predicate accept;

    public HttpContinueAcceptingHandler(HttpHandler next, Predicate accept) {
        this.next = next;
        this.accept = accept;
    }

    public HttpContinueAcceptingHandler(HttpHandler next) {
        this(next, Predicates.truePredicate());
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if(HttpContinue.requiresContinueResponse(exchange)) {
            if(accept.resolve(exchange)) {
                HttpContinue.sendContinueResponse(exchange, new IoCallback() {
                    @Override
                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                        exchange.dispatch(next);
                    }

                    @Override
                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                        exchange.endExchange();
                    }
                });

            } else {
                HttpContinue.rejectExchange(exchange);
            }
        } else {
            next.handleRequest(exchange);
        }
    }

    public static final class Wrapper implements HandlerWrapper {

        private final Predicate predicate;

        public Wrapper(Predicate predicate) {
            this.predicate = predicate;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new HttpContinueAcceptingHandler(handler, predicate);
        }
    }

    @Override
    public String toString() {
        return "http-continue-accept()";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "http-continue-accept";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return null;
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper(Predicates.truePredicate());
        }
    }
}
