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
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

/**
 * A handler that handles HTTP trace requests
 *
 * @author Stuart Douglas
 */
public class HttpTraceHandler implements HttpHandler {

    private final HttpHandler handler;

    public HttpTraceHandler(final HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if(exchange.getRequestMethod().equals(Methods.TRACE)) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "message/http");
            StringBuilder body = new StringBuilder("TRACE ");
            body.append(exchange.getRequestURI());
            if(!exchange.getQueryString().isEmpty()) {
                body.append('?');
                body.append(exchange.getQueryString());
            }
            body.append(' ');
            body.append(exchange.getProtocol().toString());
            body.append("\r\n");
            for(HeaderValues header : exchange.getRequestHeaders()) {
                for(String value : header) {
                    body.append(header.getHeaderName());
                    body.append(": ");
                    body.append(value);
                    body.append("\r\n");
                }
            }
            body.append("\r\n");
            exchange.getResponseSender().send(body.toString());
        } else {
            handler.handleRequest(exchange);
        }
    }

    @Override
    public String toString() {
        return "trace()";
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "trace";
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
            return new HttpTraceHandler(handler);
        }
    }
}
