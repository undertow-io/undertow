/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
 * The bytes read
 *
 * @author baranowb
 */
public class BytesReadAttribute implements ExchangeAttribute {

    public static final String BYTES_READ_SHORT_UPPER = "%X";
    public static final String BYTES_READ_SHORT_LOWER = "%x";
    public static final String BYTES_READ = "%{BYTES_READ}";

    private final boolean dashIfZero;

    public BytesReadAttribute(boolean dashIfZero) {
        this.dashIfZero = dashIfZero;
    }


    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        if (dashIfZero )  {
            long bytesSent = exchange.getRequestBytesRead();
            return bytesSent == 0 ? "-" : Long.toString(bytesSent);
        } else {
            return Long.toString(exchange.getRequestBytesRead());
        }
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Bytes read", newValue);
    }

    @Override
    public String toString() {
        return BYTES_READ;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Bytes Read";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if(token.equals(BYTES_READ_SHORT_LOWER)) {
                return new BytesReadAttribute(true);
            }
            if (token.equals(BYTES_READ) || token.equals(BYTES_READ_SHORT_UPPER)) {
                return new BytesReadAttribute(false);
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
