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
 * The bytes sent
 *
 * @author Filipe Ferraz
 */
public class BytesSentAttribute implements ExchangeAttribute {

    public static final String BYTES_SENT_SHORT_UPPER = "%B";
    public static final String BYTES_SENT_SHORT_LOWER = "%b";
    public static final String BYTES_SENT = "%{BYTES_SENT}";

    private final String attribute;

    public BytesSentAttribute(final String attribute) {
        this.attribute = attribute;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        if (attribute.equals(BYTES_SENT_SHORT_LOWER))  {
            long bytesSent = exchange.getResponseBytesSent();
            return bytesSent == 0 ? "-" : Long.toString(bytesSent);
        } else {
            return Long.toString(exchange.getResponseBytesSent());
        }
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Bytes sent", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Bytes Sent";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(BYTES_SENT) || token.equals(BYTES_SENT_SHORT_UPPER) || token.equals(BYTES_SENT_SHORT_LOWER)) {
                return new BytesSentAttribute(token);
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
