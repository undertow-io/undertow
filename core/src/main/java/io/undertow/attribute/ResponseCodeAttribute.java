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
 * The request status code
 *
 * @author Stuart Douglas
 */
public class ResponseCodeAttribute implements ExchangeAttribute {

    public static final String RESPONSE_CODE_SHORT = "%s";
    public static final String RESPONSE_CODE = "%{RESPONSE_CODE}";

    public static final ExchangeAttribute INSTANCE = new ResponseCodeAttribute();

    private ResponseCodeAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return Integer.toString(exchange.getStatusCode());
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.setStatusCode(Integer.parseInt(newValue));
    }

    @Override
    public String toString() {
        return RESPONSE_CODE;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Response code";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(RESPONSE_CODE) || token.equals(RESPONSE_CODE_SHORT)) {
                return ResponseCodeAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
