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

import io.undertow.UndertowLogger;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.Headers;
import io.undertow.util.NetworkUtils;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Handler that sets the peer address to the value of the X-Forwarded-For header.
 * <p>
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
            //we have no way of knowing the port
            exchange.setSourceAddress(InetSocketAddress.createUnresolved(value, 0));
        }
        String forwardedProto = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PROTO);
        if (forwardedProto != null) {
            exchange.setRequestScheme(forwardedProto);
        }
        String forwardedHost = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_HOST);
        String forwardedPort = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PORT);
        if (forwardedHost != null) {
            int index = forwardedHost.indexOf(',');
            String value;
            if (index == -1) {
                value = forwardedHost;
            } else {
                value = forwardedHost.substring(0, index);
            }
            if(value.startsWith("[")) {
                int end = value.lastIndexOf("]");
                if(end == -1 ) {
                    end = 0;
                }
                index = value.indexOf(":", end);
                if(index != -1) {
                    forwardedPort = value.substring(index + 1);
                    value = value.substring(0, index);
                }
            } else {
                index = value.lastIndexOf(":");
                if(index != -1) {
                    forwardedPort = value.substring(index + 1);
                    value = value.substring(0, index);
                }
            }
            int port = 80;
            if(forwardedPort != null) {
                try {
                    port = Integer.parseInt(forwardedPort);
                } catch (NumberFormatException ignore) {
                    UndertowLogger.REQUEST_LOGGER.debugf("Cannot parse port: %s", forwardedPort);
                }
            }
            exchange.setDestinationAddress(InetSocketAddress.createUnresolved(value, port));
            exchange.getRequestHeaders().put(Headers.HOST, NetworkUtils.formatPossibleIpv6Address(value) + ":" + port);
        }
        next.handleRequest(exchange);
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "proxy-peer-address";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper();
        }

    }

    private static class Wrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new ProxyPeerAddressHandler(handler);
        }
    }
}
