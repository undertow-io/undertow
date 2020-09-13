/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A handler that sets response code but continues the exchange so the servlet's
 * error page can be returned.
 *
 * @author Brad Wood
 */
public class SetErrorHandler implements HttpHandler {

    private final int responseCode;
    private final HttpHandler next;

    /**
     * Construct a new instance.
     *
     * @param responseCode the response code to set
     */
    public  SetErrorHandler(HttpHandler next, final int responseCode) {
        this.next = next;
        this.responseCode = responseCode;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.setStatusCode(responseCode);
        next.handleRequest(exchange);
    }

    @Override
    public String toString() {
        return "set-error( " + responseCode + " )";
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "set-error";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("response-code", Integer.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> req = new HashSet<>();
            req.add("response-code");
            return req;
        }

        @Override
        public String defaultParameter() {
            return "response-code";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((Integer) config.get("response-code"));
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final Integer responseCode;

        private Wrapper(Integer responseCode) {
            this.responseCode = responseCode;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new  SetErrorHandler(handler, responseCode);
        }
    }

}