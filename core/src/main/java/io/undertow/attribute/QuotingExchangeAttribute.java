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
 * Exchange attribute that wraps string attributes in quotes.
 *
 * This is mostly used
 *
 * @author Stuart Douglas
 */
public class QuotingExchangeAttribute implements ExchangeAttribute {

    private final ExchangeAttribute exchangeAttribute;

    public static final ExchangeAttributeWrapper WRAPPER = new Wrapper();

    public QuotingExchangeAttribute(ExchangeAttribute exchangeAttribute) {
        this.exchangeAttribute = exchangeAttribute;
    }

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        String svalue = exchangeAttribute.readAttribute(exchange);
        // Does the value contain a " ? If so must encode it
        if (svalue == null || "-".equals(svalue) || svalue.isEmpty()) {
            return "-";
        }

        /* Wrap all quotes in double quotes. */
        StringBuilder buffer = new StringBuilder(svalue.length() + 2);
        buffer.append('\'');
        int i = 0;
        while (i < svalue.length()) {
            int j = svalue.indexOf('\'', i);
            if (j == -1) {
                buffer.append(svalue.substring(i));
                i = svalue.length();
            } else {
                buffer.append(svalue.substring(i, j + 1));
                buffer.append('"');
                i = j + 2;
            }
        }

        buffer.append('\'');
        return buffer.toString();
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException();
    }

    @Override
    public String toString() {
        return "\"" + exchangeAttribute.toString() + "\"";
    }

    public static class Wrapper implements ExchangeAttributeWrapper {

        @Override
        public ExchangeAttribute wrap(final ExchangeAttribute attribute) {
            return new QuotingExchangeAttribute(attribute);
        }
    }
}
