/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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
import io.undertow.util.NetworkUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * The remote IP address
 *
 * @author Stuart Douglas
 */
public class RemoteObfuscatedIPAttribute implements ExchangeAttribute {

    public static final String REMOTE_OBFUSCATED_IP_SHORT = "%o";
    public static final String REMOTE_OBFUSCATED_IP = "%{REMOTE_OBFUSCATED_IP}";

    public static final ExchangeAttribute INSTANCE = new RemoteObfuscatedIPAttribute();

    private RemoteObfuscatedIPAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        final InetSocketAddress sourceAddress = exchange.getSourceAddress();
        InetAddress address = sourceAddress.getAddress();
        if (address == null) {
            //this can happen when we have an unresolved X-forwarded-for address
            //in this case we just return the IP of the balancer
            address = ((InetSocketAddress) exchange.getConnection().getPeerAddress()).getAddress();
        }
        if(address == null) {
            return null;
        }
        return NetworkUtils.toObfuscatedString(address);
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Remote Obfuscated IP", newValue);
    }

    @Override
    public String toString() {
        return REMOTE_OBFUSCATED_IP;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Remote Obfuscated IP";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REMOTE_OBFUSCATED_IP) || token.equals(REMOTE_OBFUSCATED_IP_SHORT)) {
                return RemoteObfuscatedIPAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
