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

import java.net.InetSocketAddress;

/**
 * The remote Host address (if resolved)
 *
 * @author Stuart Douglas
 */
public class RemoteHostAttribute implements ExchangeAttribute {

    public static final String REMOTE_HOST_NAME_SHORT = "%h";
    public static final String REMOTE_HOST = "%{REMOTE_HOST}";

    public static final ExchangeAttribute INSTANCE = new RemoteHostAttribute();

    private RemoteHostAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        final InetSocketAddress sourceAddress = exchange.getSourceAddress();
        return sourceAddress.getHostString();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Remote host", newValue);
    }

    @Override
    public String toString() {
        return REMOTE_HOST;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Remote host";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REMOTE_HOST) || token.equals(REMOTE_HOST_NAME_SHORT)) {
                return RemoteHostAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
