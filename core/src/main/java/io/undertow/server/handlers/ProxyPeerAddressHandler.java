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

package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Handler that sets the peer address to the value of the X-Forwarded-For header.
 * <p/>
 * This should only be used behind a proxy that always sets this header, otherwise it
 * is possible for an attacker to forge their peer address;
 *
 * @author Stuart Douglas
 */
public class ProxyPeerAddressHandler implements HttpHandler {

    private final HttpHandler next;

    public ProxyPeerAddressHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String forwardedFor = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_FOR);
        if (forwardedFor != null) {
            int index = forwardedFor.indexOf(',');
            final String value;
            if (index == -1) {
                value = forwardedFor;
            } else {
                value = forwardedFor.substring(0, index);
            }
            InetAddress address = InetAddress.getByName(value);
            //we have no way of knowing the port
            exchange.setSourceAddress(new InetSocketAddress(address, 0));
        }
        String forwardedProto = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PROTO);
        if (forwardedProto != null) {
            exchange.setRequestScheme(forwardedProto);
        }
        next.handleRequest(exchange);
    }
}
