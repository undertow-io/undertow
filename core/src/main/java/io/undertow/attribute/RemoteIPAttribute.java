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

import java.net.InetAddress;
import java.net.InetSocketAddress;

import io.undertow.server.HttpServerExchange;

/**
 * The remote IP address
 *
 * @author Stuart Douglas
 */
public class RemoteIPAttribute implements ExchangeAttribute {

    public static final String REMOTE_IP_SHORT = "%a";
    public static final String REMOTE_IP = "%{REMOTE_IP}";

    public static final ExchangeAttribute INSTANCE = new RemoteIPAttribute();

    private RemoteIPAttribute() {

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
        return address.getHostAddress();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Remote IP", newValue);
    }

    @Override
    public String toString() {
        return REMOTE_IP;
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Remote IP";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REMOTE_IP) || token.equals(REMOTE_IP_SHORT)) {
                return RemoteIPAttribute.INSTANCE;
            }
            return null;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
