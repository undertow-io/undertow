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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Handler that sets the peer address to the value of the X-Forwarded-For header.
 * <p>
 * This should only be used behind a proxy that always sets this header, otherwise it
 * is possible for an attacker to forge their peer address;
 *
 * @author Stuart Douglas
 */
public class ProxyPeerAddressHandler implements HttpHandler {

    private static final Pattern IP4_EXACT = Pattern.compile(NetworkUtils.IP4_EXACT);

    private static final Pattern IP6_EXACT = Pattern.compile(NetworkUtils.IP6_EXACT);

    private final HttpHandler next;

    private static final boolean DEFAULT_CHANGE_LOCAL_ADDR_PORT = Boolean.getBoolean("io.undertow.forwarded.change-local-addr-port");

    private static final String CHANGE_LOCAL_ADDR_PORT  = "change-local-addr-port";

    private final boolean isChangeLocalAddrPort;

    public ProxyPeerAddressHandler(HttpHandler next) {
        this(next, DEFAULT_CHANGE_LOCAL_ADDR_PORT);
    }

    public ProxyPeerAddressHandler(HttpHandler next, boolean isChangeLocalAddrPort) {
        this.next = next;
        this.isChangeLocalAddrPort = isChangeLocalAddrPort;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String forwardedFor = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_FOR);
        if (forwardedFor != null) {
            String remoteClient = mostRecent(forwardedFor);
            //we have no way of knowing the port
            if (IP4_EXACT.matcher(remoteClient).matches()) {
                exchange.setSourceAddress(new InetSocketAddress(NetworkUtils.parseIpv4Address(remoteClient), 0));
            } else if (IP6_EXACT.matcher(remoteClient).matches()) {
                exchange.setSourceAddress(new InetSocketAddress(NetworkUtils.parseIpv6Address(remoteClient), 0));
            } else {
                exchange.setSourceAddress(InetSocketAddress.createUnresolved(remoteClient, 0));
            }
        }
        String forwardedProto = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PROTO);
        if (forwardedProto != null) {
            exchange.setRequestScheme(mostRecent(forwardedProto));
        }
        String forwardedHost = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_HOST);
        String forwardedPort = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PORT);
        if (forwardedHost != null) {
            String value = mostRecent(forwardedHost);
            if(value.startsWith("[")) {
                int end = value.lastIndexOf("]");
                if(end == -1 ) {
                    end = 0;
                }
                int index = value.indexOf(":", end);
                if(index != -1) {
                    forwardedPort = value.substring(index + 1);
                    value = value.substring(0, index);
                }
            } else {
                int index = value.lastIndexOf(":");
                if(index != -1) {
                    forwardedPort = value.substring(index + 1);
                    value = value.substring(0, index);
                }
            }
            int port = 0;
            String hostHeader = NetworkUtils.formatPossibleIpv6Address(value);
            if(forwardedPort != null) {
                try {
                    port = Integer.parseInt(mostRecent(forwardedPort));
                    if(port > 0) {
                        String scheme = exchange.getRequestScheme();

                        if (!standardPort(port, scheme)) {
                            hostHeader += ":" + port;
                        }
                    } else {
                        UndertowLogger.REQUEST_LOGGER.debugf("Ignoring negative port: %s", forwardedPort);
                    }
                } catch (NumberFormatException ignore) {
                    UndertowLogger.REQUEST_LOGGER.debugf("Cannot parse port: %s", forwardedPort);
                }
            }
            exchange.getRequestHeaders().put(Headers.HOST, hostHeader);
            if (isChangeLocalAddrPort) {
                exchange.setDestinationAddress(InetSocketAddress.createUnresolved(value, port));
            }
        }
        next.handleRequest(exchange);
    }

    private String mostRecent(String header) {
        int index = header.indexOf(',');
        if (index == -1) {
            return header;
        } else {
            return header.substring(0, index);
        }
    }

    private static boolean standardPort(int port, String scheme) {
        return (port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme));
    }

    @Override
    public String toString() {
        return "proxy-peer-address()";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "proxy-peer-address";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put(CHANGE_LOCAL_ADDR_PORT, boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return CHANGE_LOCAL_ADDR_PORT;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            Boolean isChangeLocalAddrPort = (Boolean) config.get(CHANGE_LOCAL_ADDR_PORT);
            return new Wrapper(isChangeLocalAddrPort == null ? DEFAULT_CHANGE_LOCAL_ADDR_PORT : isChangeLocalAddrPort);
        }

    }

    private static class Wrapper implements HandlerWrapper {
        private final boolean isChangeLocalAddrPort;

        private Wrapper(boolean isChangeLocalAddrPort) {
            this.isChangeLocalAddrPort = isChangeLocalAddrPort;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new ProxyPeerAddressHandler(handler, isChangeLocalAddrPort);
        }
    }
}
