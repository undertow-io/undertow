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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import io.undertow.UndertowMessages;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.NetworkUtils;
import io.undertow.util.StatusCodes;
import java.util.stream.Collectors;
import org.xnio.Bits;

/**
 * Handler that can accept or reject a request based on the IP address of the remote peer.
 *
 * @author Stuart Douglas
 */
public class IPAddressAccessControlHandler implements HttpHandler {

    /**
     * Standard IP address
     */
    private static final Pattern IP4_EXACT = Pattern.compile(NetworkUtils.IP4_EXACT);

    /**
     * Standard IP address, with some octets replaced by a '*'
     */
    private static final Pattern IP4_WILDCARD = Pattern.compile("(?:(?:\\d{1,3}|\\*)\\.){3}(?:\\d{1,3}|\\*)");

    /**
     * IPv4 address with subnet specified via slash notation
     */
    private static final Pattern IP4_SLASH = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}\\/\\d\\d?");

    /**
     * Standard full IPv6 address
     */
    private static final Pattern IP6_EXACT = Pattern.compile(NetworkUtils.IP6_EXACT);

    /**
     * Standard full IPv6 address, with some parts replaced by a '*'
     */
    private static final Pattern IP6_WILDCARD = Pattern.compile("(?:(?:[a-zA-Z0-9]{1,4}|\\*):){7}(?:[a-zA-Z0-9]{1,4}|\\*)");

    /**
     * Standard full IPv6 address with subnet specified via slash notation
     */
    private static final Pattern IP6_SLASH = Pattern.compile("(?:[a-zA-Z0-9]{1,4}:){7}[a-zA-Z0-9]{1,4}\\/\\d{1,3}");

    private volatile HttpHandler next;
    private volatile boolean defaultAllow = false;
    private final int denyResponseCode;
    private final List<PeerMatch> ipv6acl = new CopyOnWriteArrayList<>();
    private final List<PeerMatch> ipv4acl = new CopyOnWriteArrayList<>();
    private static final boolean traceEnabled;
    private static final boolean debugEnabled;

    static {
        traceEnabled = UndertowLogger.PREDICATE_LOGGER.isTraceEnabled();
        debugEnabled = UndertowLogger.PREDICATE_LOGGER.isDebugEnabled();
    }

    public IPAddressAccessControlHandler(final HttpHandler next) {
        this(next, StatusCodes.FORBIDDEN);
    }

    public IPAddressAccessControlHandler(final HttpHandler next, final int denyResponseCode) {
        this.next = next;
        this.denyResponseCode = denyResponseCode;
    }

    public IPAddressAccessControlHandler() {
        this.next = ResponseCodeHandler.HANDLE_404;
        this.denyResponseCode = StatusCodes.FORBIDDEN;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        InetSocketAddress peer = exchange.getSourceAddress();
        if (isAllowed(peer.getAddress())) {
            next.handleRequest(exchange);
        } else {
            if (debugEnabled) {
                UndertowLogger.PREDICATE_LOGGER.debugf("Access to [%s] blocked from %s.", exchange, peer.getHostString());
            }
            exchange.setStatusCode(denyResponseCode);
            exchange.endExchange();
        }
    }

    boolean isAllowed(InetAddress address) {
        if (address instanceof Inet4Address) {
            for (PeerMatch rule : ipv4acl) {
                if (traceEnabled) {
                    UndertowLogger.PREDICATE_LOGGER.tracef("Comparing rule [%s] to IPv4 address %s.", rule.toPredicateString(), address.getHostAddress());
                }
                if (rule.matches(address)) {
                    return !rule.isDeny();
                }
            }
        } else if (address instanceof Inet6Address) {
            for (PeerMatch rule : ipv6acl) {
                if (traceEnabled) {
                    UndertowLogger.PREDICATE_LOGGER.tracef("Comparing rule [%s] to IPv6 address %s.", rule.toPredicateString(), address.getHostAddress());
                }
                if (rule.matches(address)) {
                    return !rule.isDeny();
                }
            }
        }
        return defaultAllow;
    }

    public int getDenyResponseCode() {
        return denyResponseCode;
    }

    public boolean isDefaultAllow() {
        return defaultAllow;
    }

    public IPAddressAccessControlHandler setDefaultAllow(final boolean defaultAllow) {
        this.defaultAllow = defaultAllow;
        return this;
    }

    public HttpHandler getNext() {
        return next;
    }

    public IPAddressAccessControlHandler setNext(final HttpHandler next) {
        this.next = next;
        return this;
    }

    /**
     * Adds an allowed peer to the ACL list
     * <p>
     * Peer can take several forms:
     * <p>
     * a.b.c.d = Literal IPv4 Address
     * a:b:c:d:e:f:g:h = Literal IPv6 Address
     * a.b.* = Wildcard IPv4 Address
     * a:b:* = Wildcard IPv6 Address
     * a.b.c.0/24 = Classless wildcard IPv4 address
     * a:b:c:d:e:f:g:0/120 = Classless wildcard IPv6 address
     *
     * @param peer The peer to add to the ACL
     */
    public IPAddressAccessControlHandler addAllow(final String peer) {
        return addRule(peer, false);
    }

    /**
     * Adds an denied peer to the ACL list
     * <p>
     * Peer can take several forms:
     * <p>
     * a.b.c.d = Literal IPv4 Address
     * a:b:c:d:e:f:g:h = Literal IPv6 Address
     * a.b.* = Wildcard IPv4 Address
     * a:b:* = Wildcard IPv6 Address
     * a.b.c.0/24 = Classless wildcard IPv4 address
     * a:b:c:d:e:f:g:0/120 = Classless wildcard IPv6 address
     *
     * @param peer The peer to add to the ACL
     */
    public IPAddressAccessControlHandler addDeny(final String peer) {
        return addRule(peer, true);
    }

    public IPAddressAccessControlHandler clearRules() {
        this.ipv4acl.clear();
        this.ipv6acl.clear();
        return this;
    }

    private IPAddressAccessControlHandler addRule(final String peer, final boolean deny) {
        if (IP4_EXACT.matcher(peer).matches()) {
            addIpV4ExactMatch(peer, deny);
        } else if (IP4_WILDCARD.matcher(peer).matches()) {
            addIpV4WildcardMatch(peer, deny);
        } else if (IP4_SLASH.matcher(peer).matches()) {
            addIpV4SlashPrefix(peer, deny);
        } else if (IP6_EXACT.matcher(peer).matches()) {
            addIpV6ExactMatch(peer, deny);
        } else if (IP6_WILDCARD.matcher(peer).matches()) {
            addIpV6WildcardMatch(peer, deny);
        } else if (IP6_SLASH.matcher(peer).matches()) {
            addIpV6SlashPrefix(peer, deny);
        } else {
            throw UndertowMessages.MESSAGES.notAValidIpPattern(peer);
        }
        return this;
    }

    private void addIpV6SlashPrefix(final String peer, final boolean deny) {
        String[] components = peer.split("\\/");
        String[] parts = components[0].split("\\:");
        int maskLen = Integer.parseInt(components[1]);
        assert parts.length == 8;

        byte[] pattern = new byte[16];
        byte[] mask = new byte[16];

        for (int i = 0; i < 8; ++i) {
            int val = Integer.parseInt(parts[i], 16);
            pattern[i * 2] = (byte) (val >> 8);
            pattern[i * 2 + 1] = (byte) (val & 0xFF);
        }
        for (int i = 0; i < 16; ++i) {
            if (maskLen > 8) {
                mask[i] = (byte) (0xFF);
                maskLen -= 8;
            } else if (maskLen != 0) {
                mask[i] = (byte) (Bits.intBitMask(8 - maskLen, 7) & 0xFF);
                maskLen = 0;
            } else {
                break;
            }
        }
        ipv6acl.add(new PrefixIpV6PeerMatch(deny, peer, mask, pattern));
    }

    private void addIpV4SlashPrefix(final String peer, final boolean deny) {
        String[] components = peer.split("\\/");
        String[] parts = components[0].split("\\.");
        int maskLen = Integer.parseInt(components[1]);
        final int mask = Bits.intBitMask(32 - maskLen, 31);
        int prefix = 0;
        for (int i = 0; i < 4; ++i) {
            prefix <<= 8;
            String part = parts[i];
            int no = Integer.parseInt(part);
            prefix |= no;
        }
        prefix &= mask;
        ipv4acl.add(new PrefixIpV4PeerMatch(deny, peer, mask, prefix));
    }

    private void addIpV6WildcardMatch(final String peer, final boolean deny) {
        byte[] pattern = new byte[16];
        byte[] mask = new byte[16];
        String[] parts = peer.split("\\:");
        assert parts.length == 8;
        for (int i = 0; i < 8; ++i) {
            if (!parts[i].equals("*")) {
                int val = Integer.parseInt(parts[i], 16);
                pattern[i * 2] = (byte) (val >> 8);
                pattern[i * 2 + 1] = (byte) (val & 0xFF);
                mask[i * 2] = (byte) (0xFF);
                mask[i * 2 + 1] = (byte) (0xFF);
            }
        }
        ipv6acl.add(new PrefixIpV6PeerMatch(deny, peer, mask, pattern));
    }

    private void addIpV4WildcardMatch(final String peer, final boolean deny) {
        String[] parts = peer.split("\\.");
        int mask = 0;
        int prefix = 0;
        for (int i = 0; i < 4; ++i) {
            mask <<= 8;
            prefix <<= 8;
            String part = parts[i];
            if (!part.equals("*")) {
                int no = Integer.parseInt(part);
                mask |= 0xFF;
                prefix |= no;
            }
        }
        ipv4acl.add(new PrefixIpV4PeerMatch(deny, peer, mask, prefix));
    }

    private void addIpV6ExactMatch(final String peer, final boolean deny) {
        byte[] bytes;
        try {
            bytes = NetworkUtils.parseIpv6AddressToBytes(peer);
            ipv6acl.add(new ExactIpV6PeerMatch(deny, peer, bytes));
        } catch (IOException e) {
            throw UndertowMessages.MESSAGES.invalidACLAddress(e);
        }
    }

    private void addIpV4ExactMatch(final String peer, final boolean deny) {
        String[] parts = peer.split("\\.");
        byte[] bytes = {(byte) Integer.parseInt(parts[0]), (byte) Integer.parseInt(parts[1]), (byte) Integer.parseInt(parts[2]), (byte) Integer.parseInt(parts[3])};
        ipv4acl.add(new ExactIpV4PeerMatch(deny, peer, bytes));
    }

    @Override
    public String toString() {
        //ip-access-control( default-allow=false, acl={'127.0.0.* allow', '192.168.1.123 deny'}, failure-status=404 )
        String predicate = "ip-access-control( default-allow=" + defaultAllow + ", acl={ ";
        List<PeerMatch> acl = new ArrayList<>();
        acl.addAll(ipv4acl);
        acl.addAll(ipv6acl);

        predicate += acl.stream().map(s -> "'" + s.toPredicateString() + "'").collect(Collectors.joining(", "));
        predicate += " }";
        if (denyResponseCode != StatusCodes.FORBIDDEN) {
            predicate += ", failure-status=" + denyResponseCode;
        }
        predicate += " )";
        return predicate;
    }

    abstract static class PeerMatch {

        private final boolean deny;
        private final String pattern;

        protected PeerMatch(final boolean deny, final String pattern) {
            this.deny = deny;
            this.pattern = pattern;
        }

        abstract boolean matches(final InetAddress address);

        boolean isDeny() {
            return deny;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{"
                    + "deny=" + deny
                    + ", pattern='" + pattern + '\''
                    + '}';
        }

        public String toPredicateString() {
            return pattern + " " + (deny ? "deny" : "allow");
        }
    }

    static class ExactIpV4PeerMatch extends PeerMatch {

        private final byte[] address;

        protected ExactIpV4PeerMatch(final boolean deny, final String pattern, final byte[] address) {
            super(deny, pattern);
            this.address = address;
        }

        @Override
        boolean matches(final InetAddress address) {
            return Arrays.equals(address.getAddress(), this.address);
        }
    }

    static class ExactIpV6PeerMatch extends PeerMatch {

        private final byte[] address;

        protected ExactIpV6PeerMatch(final boolean deny, final String pattern, final byte[] address) {
            super(deny, pattern);
            this.address = address;
        }

        @Override
        boolean matches(final InetAddress address) {
            return Arrays.equals(address.getAddress(), this.address);
        }
    }

    private static class PrefixIpV4PeerMatch extends PeerMatch {

        private final int mask;
        private final int prefix;

        protected PrefixIpV4PeerMatch(final boolean deny, final String pattern, final int mask, final int prefix) {
            super(deny, pattern);
            this.mask = mask;
            this.prefix = prefix;
        }

        @Override
        boolean matches(final InetAddress address) {
            byte[] bytes = address.getAddress();
            if (bytes == null) {
                return false;
            }
            int addressInt = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
            return (addressInt & mask) == prefix;
        }
    }

    static class PrefixIpV6PeerMatch extends PeerMatch {

        private final byte[] mask;
        private final byte[] prefix;

        protected PrefixIpV6PeerMatch(final boolean deny, final String pattern, final byte[] mask, final byte[] prefix) {
            super(deny, pattern);
            this.mask = mask;
            this.prefix = prefix;
            assert mask.length == prefix.length;
        }

        @Override
        boolean matches(final InetAddress address) {
            byte[] bytes = address.getAddress();
            if (bytes == null) {
                return false;
            }
            if (bytes.length != mask.length) {
                return false;
            }
            for (int i = 0; i < mask.length; ++i) {
                if ((bytes[i] & mask[i]) != prefix[i]) {
                    return false;
                }
            }
            return true;
        }
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
