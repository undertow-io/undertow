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
 * The thread name
 *
 * @author Stuart Douglas
 */
public class ThreadNameAttribute implements ExchangeAttribute {

    public static final String THREAD_NAME_SHORT = "%I";
    public static final String THREAD_NAME = "%{THREAD_NAME}";

    public static final ExchangeAttribute INSTANCE = new ThreadNameAttribute();

    private ThreadNameAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return Thread.currentThread().getName();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Thread name", newValue);
    }

    @Override
    public String toString() {
        return THREAD_NAME;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Thread name";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(THREAD_NAME) || token.equals(THREAD_NAME_SHORT)) {
                return ThreadNameAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
