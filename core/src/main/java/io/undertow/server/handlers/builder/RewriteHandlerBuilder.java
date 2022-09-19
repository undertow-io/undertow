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

package io.undertow.server.handlers.builder;

import io.undertow.attribute.ConstantExchangeAttribute;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.attribute.ReadOnlyAttributeException;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.SetAttributeHandler;
import io.undertow.UndertowLogger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class RewriteHandlerBuilder implements HandlerBuilder {
    @Override
    public String name() {
        return "rewrite";
    }

    @Override
    public Map<String, Class<?>> parameters() {
        return Collections.<String, Class<?>>singletonMap("value", ExchangeAttribute.class);
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
    public HandlerWrapper build(final Map<String, Object> config) {
        final ExchangeAttribute configValue = (ExchangeAttribute) config.get("value");
        ExchangeAttribute tmpValue = null;
        if(configValue instanceof ConstantExchangeAttribute) {
            tmpValue = new ConstantExchangeAttribute(normalize(configValue.readAttribute(null)));
        } else {
            tmpValue = new ExchangeAttribute() {

                @Override
                public String readAttribute(HttpServerExchange exchange) {
                    return normalize(configValue.readAttribute(exchange));
                }

                @Override
                public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
                    throw new ReadOnlyAttributeException("constant", newValue);
                }

                public String toString() {
                    return "NDA";
                };
            };
        }
        final ExchangeAttribute value = tmpValue;

        return new HandlerWrapper() {
            @Override
            public HttpHandler wrap(HttpHandler handler) {
                return new SetAttributeHandler(handler, ExchangeAttributes.relativePath(), value){
                   @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        UndertowLogger.PREDICATE_LOGGER.debugf("Request rewritten to [%s] for %s.", getValue().readAttribute(exchange), exchange);
                        super.handleRequest(exchange);
                    }

                   @Override
                    public String toString() {
                        return "rewrite( '" + getValue().toString() + "' )";
                    }

                };
            }

        };
    }

    private String normalize(final String readAttribute) {
        if (readAttribute.startsWith("/")) {
            return readAttribute;
        } else {
            return "/" + readAttribute;
        }
    }
}
