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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.predicate.ip.IPMatchBase;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.StatusCodes;

/**
 * Handler that can accept or reject a request based on the IP address of the remote peer.
 *
 * @author Stuart Douglas
 */
public class IPAddressAccessControlHandler extends IPMatchBase<IPAddressAccessControlHandler> implements HttpHandler {

    protected volatile HttpHandler next;
    private volatile int denyResponseCode;

    public IPAddressAccessControlHandler(final HttpHandler next) {
        super(false);
        this.denyResponseCode = StatusCodes.FORBIDDEN;
        this.next = next;
    }

    public IPAddressAccessControlHandler(final HttpHandler next, final int denyResponseCode, final boolean defaultAllow) {
        super(defaultAllow);
        this.denyResponseCode = denyResponseCode;
        this.next = next;
    }

    public IPAddressAccessControlHandler(final HttpHandler next, final int denyResponseCode) {
        super(false);
        this.denyResponseCode = denyResponseCode;
        this.next = next;
    }

    public IPAddressAccessControlHandler() {
        super(false);
        this.denyResponseCode = StatusCodes.FORBIDDEN;
        this.next = ResponseCodeHandler.HANDLE_404;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final InetSocketAddress peer = exchange.getSourceAddress();
        if (isAllowed(peer.getAddress())) {
            next.handleRequest(exchange);
        } else {
            if (debugEnabled) {
                UndertowLogger.PREDICATE_LOGGER.debugf("Access to [%s] blocked from %s.", exchange, peer.getHostString());
            }
            exchange.setStatusCode(this.denyResponseCode);
            exchange.endExchange();
        }
    }

    public int getDenyResponseCode() {
        return denyResponseCode;
    }

    public IPAddressAccessControlHandler setDenyResponseCode(final int denyResponseCode) {
        this.denyResponseCode = denyResponseCode;
        return this;
    }

    public HttpHandler getNext() {
        return next;
    }

    public IPAddressAccessControlHandler setNext(final HttpHandler next) {
        this.next = next;
        return this;
    }

    @Override
    public String toString() {
        //ip-access-control( default-allow=false, acl={'127.0.0.* allow', '192.168.1.123 deny'}, failure-status=404 )
        String predicate = "ip-access-control( default-allow=" + defaultAllow + ", acl={ ";
        List<PeerMatch> acl = new ArrayList<>();
        acl.addAll(super.ipv4Matches);
        acl.addAll(super.ipv6Matches);

        predicate += acl.stream().map(s -> "'" + s.toPredicateString() + "'").collect(Collectors.joining(", "));
        predicate += " }";
        if (denyResponseCode != StatusCodes.FORBIDDEN) {
            predicate += ", failure-status=" + denyResponseCode;
        }
        predicate += " )";
        return predicate;
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "ip-access-control";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("acl", String[].class);
            params.put("failure-status", int.class);
            params.put("default-allow", boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("acl");
        }

        @Override
        public String defaultParameter() {
            return "acl";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {

            String[] acl = (String[]) config.get("acl");
            Boolean defaultAllow = (Boolean) config.get("default-allow");
            Integer failureStatus = (Integer) config.get("failure-status");

            List<Holder> peerMatches = new ArrayList<>();
            for (String rule : acl) {
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
            return new Wrapper(peerMatches, defaultAllow == null ? false : defaultAllow, failureStatus == null ? StatusCodes.FORBIDDEN : failureStatus);
        }

        @Override
        public int priority() {
            return 0;
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final List<Holder> peerMatches;
        private final boolean defaultAllow;
        private final int failureStatus;

        private Wrapper(List<Holder> peerMatches, boolean defaultAllow, int failureStatus) {
            this.peerMatches = peerMatches;
            this.defaultAllow = defaultAllow;
            this.failureStatus = failureStatus;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            IPAddressAccessControlHandler res = new IPAddressAccessControlHandler(handler, failureStatus);
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
