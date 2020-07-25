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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributeParser;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.attribute.NullAttribute;
import io.undertow.attribute.ReadOnlyAttributeException;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;
import io.undertow.server.handlers.builder.HandlerBuilder;

/**
 * Handler that can set an arbitrary attribute on the exchange. Both the attribute and the
 * value to set are expressed as exchange attributes.
 *
 *
 * @author Stuart Douglas
 */
public class SetAttributeHandler implements HttpHandler {

    private final HttpHandler next;
    private final ExchangeAttribute attribute;
    private final ExchangeAttribute value;
    private final boolean preCommit;

    public SetAttributeHandler(HttpHandler next, ExchangeAttribute attribute, ExchangeAttribute value) {
        this(next, attribute, value, false);
    }

    public SetAttributeHandler(HttpHandler next, final String attribute, final String value) {
        this.next = next;
        ExchangeAttributeParser parser = ExchangeAttributes.parser(getClass().getClassLoader());
        this.attribute = parser.parseSingleToken(attribute);
        this.value = parser.parse(value);
        this.preCommit = false;
    }

    public SetAttributeHandler(HttpHandler next, final String attribute, final String value, final ClassLoader classLoader) {
        this.next = next;
        ExchangeAttributeParser parser = ExchangeAttributes.parser(classLoader);
        this.attribute = parser.parseSingleToken(attribute);
        this.value = parser.parse(value);
        this.preCommit = false;
    }

    public SetAttributeHandler(HttpHandler next, ExchangeAttribute attribute, ExchangeAttribute value, boolean preCommit) {
        this.next = next;
        this.attribute = attribute;
        this.value = value;
        this.preCommit = preCommit;
    }

    public SetAttributeHandler(HttpHandler next, final String attribute, final String value, boolean preCommit) {
        this.next = next;
        this.preCommit = preCommit;
        ExchangeAttributeParser parser = ExchangeAttributes.parser(getClass().getClassLoader());
        this.attribute = parser.parseSingleToken(attribute);
        this.value = parser.parse(value);
    }

    public SetAttributeHandler(HttpHandler next, final String attribute, final String value, final ClassLoader classLoader, boolean preCommit) {
        this.next = next;
        this.preCommit = preCommit;
        ExchangeAttributeParser parser = ExchangeAttributes.parser(classLoader);
        this.attribute = parser.parseSingleToken(attribute);
        this.value = parser.parse(value);
    }

    public ExchangeAttribute getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "set( attribute='" + attribute.toString() + "', value='" + value.toString() + "' )";
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if(preCommit) {
            exchange.addResponseCommitListener(new ResponseCommitListener() {
                @Override
                public void beforeCommit(HttpServerExchange exchange) {
                    try {
                        attribute.writeAttribute(exchange, value.readAttribute(exchange));
                    } catch (ReadOnlyAttributeException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } else {
            attribute.writeAttribute(exchange, value.readAttribute(exchange));
        }
        next.handleRequest(exchange);
    }

    public static class Builder implements HandlerBuilder {
        @Override
        public String name() {
            return "set";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> parameters = new HashMap<>();
            parameters.put("value", ExchangeAttribute.class);
            parameters.put("attribute", ExchangeAttribute.class);
            parameters.put("pre-commit", Boolean.class);

            return parameters;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> req = new HashSet<>();
            req.add("value");
            req.add("attribute");
            return req;
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(final Map<String, Object> config) {
            final ExchangeAttribute value = (ExchangeAttribute) config.get("value");
            final ExchangeAttribute attribute = (ExchangeAttribute) config.get("attribute");
            final Boolean preCommit = (Boolean) config.get("pre-commit");

            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler handler) {
                    return new SetAttributeHandler(handler, attribute, value, preCommit == null ? false : preCommit);
                }
            };
        }
    }

    public static class ClearBuilder implements HandlerBuilder {
        @Override
        public String name() {
            return "clear";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> parameters = new HashMap<>();
            parameters.put("attribute", ExchangeAttribute.class);
            parameters.put("pre-commit", Boolean.class);
            return parameters;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> req = new HashSet<>();
            req.add("attribute");
            return req;
        }

        @Override
        public String defaultParameter() {
            return "attribute";
        }

        @Override
        public HandlerWrapper build(final Map<String, Object> config) {
            final ExchangeAttribute attribute = (ExchangeAttribute) config.get("attribute");
            final Boolean preCommit = (Boolean) config.get("pre-commit");

            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler handler) {
                    return new SetAttributeHandler(handler, attribute, NullAttribute.INSTANCE, preCommit == null ? false : preCommit);
                }
            };
        }
    }

}
