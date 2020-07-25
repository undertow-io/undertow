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

import io.undertow.UndertowMessages;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.HttpString;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Set a fixed response header.
 *
 * @author Stuart Douglas
 */
public class SetHeaderHandler implements HttpHandler {

    private final HttpString header;
    private final ExchangeAttribute value;
    private final HttpHandler next;

    public SetHeaderHandler(final String header, final String value) {
        if(value == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("value");
        }
        if(header == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("header");
        }
        this.next = ResponseCodeHandler.HANDLE_404;
        this.value = ExchangeAttributes.constant(value);
        this.header = new HttpString(header);
    }

    public SetHeaderHandler(final HttpHandler next, final String header, final ExchangeAttribute value) {
        if(value == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("value");
        }
        if(header == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("header");
        }
        if(next == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("next");
        }
        this.next = next;
        this.value = value;
        this.header = new HttpString(header);
    }

    public SetHeaderHandler(final HttpHandler next, final String header, final String value) {
        if(value == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("value");
        }
        if(header == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("header");
        }
        if(next == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("next");
        }
        this.next = next;
        this.value = ExchangeAttributes.constant(value);
        this.header = new HttpString(header);
    }
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().put(header, value.readAttribute(exchange));
        next.handleRequest(exchange);
    }

    public ExchangeAttribute getValue() {
        return value;
    }

    public HttpString getHeader() {
        return header;
    }

    @Override
    public String toString() {
        return "set( header='" + header.toString() + "', value='" + value.toString() + "' )";
    }

    public static class Builder implements HandlerBuilder {
        @Override
        public String name() {
            return "header";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> parameters = new HashMap<>();
            parameters.put("header", String.class);
            parameters.put("value", ExchangeAttribute.class);

            return parameters;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> req = new HashSet<>();
            req.add("value");
            req.add("header");
            return req;
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(final Map<String, Object> config) {
            final ExchangeAttribute value = (ExchangeAttribute) config.get("value");
            final String header = (String) config.get("header");

            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler handler) {
                    return new SetHeaderHandler(handler, header, value);
                }
            };
        }
    }
}
