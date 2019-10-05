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
import io.undertow.UndertowMessages;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.Headers;
import io.undertow.util.NetworkUtils;
import io.undertow.util.PeerMatcher;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler that sets the peer address to the value of the X-Forwarded-For header.
 * <p>
 * This should only be used behind a proxy that always sets this header, otherwise it
 * is possible for an attacker to forge their peer address;
 *
 * It's possible to add rules, that either allow or deny X-Forwarded-* headers from ips,
 * using allowed-proxy-addresses and default-allow parameters.
 *
 * @author Stuart Douglas
 */
public class ProxyPeerAddressHandler implements HttpHandler {

    private final PeerMatcher peerMatcher = new PeerMatcher();
    private final HttpHandler next;

    public ProxyPeerAddressHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        SocketAddress peer = exchange.getConnection().getPeerAddress();
        if (peerMatcher.isAllowed(peer)) {
            String forwardedFor = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_FOR);
            if (forwardedFor != null) {
                String remoteClient = mostRecent(forwardedFor);
                exchange.setSourceAddress(ForwardedHandler.parseAddress(remoteClient));
            }
            String forwardedProto = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PROTO);
            if (forwardedProto != null) {
                exchange.setRequestScheme(mostRecent(forwardedProto));
            }
            String forwardedHost = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_HOST);
            String forwardedPort = exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PORT);
            if (forwardedHost != null) {
                String value = mostRecent(forwardedHost);
                if (value.startsWith("[")) {
                    int end = value.lastIndexOf("]");
                    if (end == -1) {
                        end = 0;
                    }
                    int index = value.indexOf(":", end);
                    if (index != -1) {
                        forwardedPort = value.substring(index + 1);
                        value = value.substring(0, index);
                    }
                } else {
                    int index = value.lastIndexOf(":");
                    if (index != -1) {
                        forwardedPort = value.substring(index + 1);
                        value = value.substring(0, index);
                    }
                }
                int port = 80;
                String hostHeader = NetworkUtils.formatPossibleIpv6Address(value);
                if (forwardedPort != null) {
                    try {
                        port = Integer.parseInt(mostRecent(forwardedPort));
                        if (port > 0) {
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

    void addAllow(String peer) {
        peerMatcher.addRule(peer, false);
    }

    void addDeny(String peer) {
        peerMatcher.addRule(peer, true);
    }

    public ProxyPeerAddressHandler clearRules() {
        peerMatcher.clearRules();
        return this;
    }

    ProxyPeerAddressHandler setDefaultAllow(final boolean defaultAllow) {
        peerMatcher.setDefaultAllow(defaultAllow);
        return this;
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "proxy-peer-address";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("allowed-proxy-addresses", String[].class);
            params.put("default-allow", boolean.class);
            return params;
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
            String[] allowedProxyAddresses = (String[]) config.get("allowed-proxy-addresses");
            Boolean defaultAllow = (Boolean) config.get("default-allow");

            List<Holder> peerMatches = null;
            if (allowedProxyAddresses != null) {
                peerMatches = new ArrayList<>();
                for (String rule : allowedProxyAddresses) {
                    String[] parts = rule.split(" ");
                    if (parts.length != 2) {
                        throw UndertowMessages.MESSAGES.invalidAclRule(rule);
                    }
                    if (parts[1].trim().equals("allow")) {
                        peerMatches.add(new Holder(parts[0].trim(), false));
                    } else if (parts[1].trim().equals("deny")) {
                        peerMatches.add(new Holder(parts[0].trim(), true));
                    } else {
                        throw UndertowMessages.MESSAGES.invalidAclRule(rule);
                    }
                }
                if (defaultAllow == null && peerMatches.size() > 0) {
                    defaultAllow = false;
                }
            }
            return new Wrapper(peerMatches, defaultAllow == null ? true : defaultAllow);
        }

    }

    private static class Wrapper implements HandlerWrapper {
        private final List<Holder> peerMatches;
        private final boolean defaultAllow;

        private Wrapper(List<Holder> peerMatches, boolean defaultAllow) {
            this.peerMatches = peerMatches;
            this.defaultAllow = defaultAllow;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            ProxyPeerAddressHandler res = new ProxyPeerAddressHandler(handler);
            for (Holder match : peerMatches) {
                if (match.deny) {
                    res.addDeny(match.rule);
                } else {
                    res.addAllow(match.rule);
                }
            }
            res.setDefaultAllow(defaultAllow);
            return res;
        }
    }

    private static class Holder {
        final String rule;
        final boolean deny;

        private Holder(String rule, boolean deny) {
            this.rule = rule;
            this.deny = deny;
        }
    }
}
