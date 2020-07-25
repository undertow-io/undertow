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
 * The local server name
 *
 * @author Stuart Douglas
 */
public class LocalServerNameAttribute implements ExchangeAttribute {

    public static final String LOCAL_SERVER_NAME_SHORT = "%v";
    public static final String LOCAL_SERVER_NAME = "%{LOCAL_SERVER_NAME}";

    public static final ExchangeAttribute INSTANCE = new LocalServerNameAttribute();

    private LocalServerNameAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getHostName();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Local server name", newValue);
    }

    @Override
    public String toString() {
        return LOCAL_SERVER_NAME;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Local server name";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(LOCAL_SERVER_NAME) || token.equals(LOCAL_SERVER_NAME_SHORT)) {
                return LocalServerNameAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
