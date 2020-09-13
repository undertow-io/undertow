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

import java.net.InetSocketAddress;

import io.undertow.server.HttpServerExchange;

/**
 * The local IP address
 *
 * @author Stuart Douglas
 */
public class LocalIPAttribute implements ExchangeAttribute {

    public static final String LOCAL_IP = "%{LOCAL_IP}";
    public static final String LOCAL_IP_SHORT = "%A";

    public static final ExchangeAttribute INSTANCE = new LocalIPAttribute();

    private LocalIPAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        InetSocketAddress localAddress = (InetSocketAddress) exchange.getConnection().getLocalAddress();
        return localAddress.getAddress().getHostAddress();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Local IP", newValue);
    }

    @Override
    public String toString() {
        return LOCAL_IP;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Local IP";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(LOCAL_IP) || token.equals(LOCAL_IP_SHORT)) {
                return LocalIPAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
