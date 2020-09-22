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
import io.undertow.server.SSLSessionInfo;
import io.undertow.util.HexConverter;

/**
 * @author Stuart Douglas
 */
public class SslSessionIdAttribute implements ExchangeAttribute {

    public static final SslSessionIdAttribute INSTANCE = new SslSessionIdAttribute();

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        SSLSessionInfo ssl = exchange.getConnection().getSslSessionInfo();
        if(ssl == null || ssl.getSessionId() == null) {
            return null;
        }
        return HexConverter.convertToHexString(ssl.getSessionId());
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("SSL Session ID", newValue);
    }

    @Override
    public String toString() {
        return "%{SSL_SESSION_ID}";
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "SSL Session ID";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals("%{SSL_SESSION_ID}")) {
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
