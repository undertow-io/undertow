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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributeParser;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * A redirect handler that redirects to the specified location via a 302 redirect.
 * <p>
 * The location is specified as an exchange attribute string.
 *
 * @author Stuart Douglas
 * @see ExchangeAttributes
 */
public class RedirectHandler implements HttpHandler {

    private final ExchangeAttribute attribute;

    public RedirectHandler(final String location) {
        ExchangeAttributeParser parser = ExchangeAttributes.parser(getClass().getClassLoader());
        attribute = parser.parse(location);
    }

    public RedirectHandler(final String location, final ClassLoader classLoader) {
        ExchangeAttributeParser parser = ExchangeAttributes.parser(classLoader);
        attribute = parser.parse(location);
    }

    public RedirectHandler(ExchangeAttribute attribute) {
        this.attribute = attribute;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.setStatusCode(StatusCodes.FOUND);
        exchange.getResponseHeaders().put(Headers.LOCATION, attribute.readAttribute(exchange));
        exchange.endExchange();
    }

    @Override
    public String toString() {
        return "redirect( '" + attribute.toString() + "' )";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "redirect";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("value", ExchangeAttribute.class);

            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("value");
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((ExchangeAttribute) config.get("value"));
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final ExchangeAttribute value;

        private Wrapper(ExchangeAttribute value) {
            this.value = value;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new RedirectHandler(value);
        }
    }

}
