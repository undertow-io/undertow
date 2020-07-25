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
 * Exchange attribute that represents a fixed value
 *
 * @author Stuart Douglas
 */
public class ConstantExchangeAttribute implements ExchangeAttribute {

    private final String value;

    public ConstantExchangeAttribute(final String value) {
        this.value = value;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return value;
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("constant", newValue);
    }

    @Override
    public String toString() {
        return value;
    }
}
