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

import javax.net.ssl.SSLSession;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.util.HeaderValues;

public class SecureProtocolAttribute implements ExchangeAttribute {

    public static final SecureProtocolAttribute INSTANCE = new SecureProtocolAttribute();

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        String secureProtocol = null;
        String transportProtocol = exchange.getConnection().getTransportProtocol();
        if ("ajp".equals(transportProtocol)) {
            // TODO: wrong
            HeaderValues headerValues = exchange.getRequestHeaders().get("AJP_SSL_PROTOCOL");
            if (headerValues != null && !headerValues.isEmpty()) {
                secureProtocol = headerValues.getFirst();
            }
        } else {
            SSLSessionInfo ssl = exchange.getConnection().getSslSessionInfo();
            if (ssl == null) {
                return null;
            }
            SSLSession session = ssl.getSSLSession();
            if (session != null) {
                secureProtocol = session.getProtocol();
            }
        }

        return secureProtocol;
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Secure Protocol", newValue);
    }

    @Override
    public String toString() {
        return "%{SECURE_PROTOCOL}";
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Secure Protocol";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals("%{SECURE_PROTOCOL}")) {
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
