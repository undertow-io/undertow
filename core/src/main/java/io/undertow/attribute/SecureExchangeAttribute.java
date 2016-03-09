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

package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public class SecureExchangeAttribute implements ExchangeAttribute {

    public static final String TOKEN = "${SECURE}";
    public static final ExchangeAttribute INSTANCE = new SecureExchangeAttribute();

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        return Boolean.toString(exchange.getProtocol().equalToString("https"));
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("secure", newValue);
    }

    public static class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Secure";
        }

        @Override
        public ExchangeAttribute build(String token) {
            if(token.equals(TOKEN)) {
                return INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
