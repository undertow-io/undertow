/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package io.undertow.predicate.ip;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.xnio.Bits;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.util.NetworkUtils;

/**
 * Base class for IP related matching.
 *
 * @author Stuart Douglas
 * @author baranowb
 */
public abstract class IPMatchBase<T extends IPMatchBase>{
    /**
     * Standard IP address
     */
    protected static final Pattern IP4_EXACT = Pattern.compile(NetworkUtils.IP4_EXACT);

    /**
     * Standard IP address, with some octets replaced by a '*'
     */
    protected static final Pattern IP4_WILDCARD = Pattern.compile("(?:(?:\\d{1,3}|\\*)\\.){3}(?:\\d{1,3}|\\*)");

    /**
     * IPv4 address with subnet specified via slash notation
     */
    protected static final Pattern IP4_SLASH = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}\\/\\d\\d?");

    /**
     * Standard full IPv6 address
     */
    protected static final Pattern IP6_EXACT = Pattern.compile(NetworkUtils.IP6_EXACT);

    /**
     * Standard full IPv6 address, with some parts replaced by a '*'
     */
    protected static final Pattern IP6_WILDCARD = Pattern.compile("(?:(?:[a-zA-Z0-9]{1,4}|\\*):){7}(?:[a-zA-Z0-9]{1,4}|\\*)");

    /**
     * Standard full IPv6 address with subnet specified via slash notation
     */
    protected static final Pattern IP6_SLASH = Pattern.compile("(?:[a-zA-Z0-9]{1,4}:){7}[a-zA-Z0-9]{1,4}\\/\\d{1,3}");

    protected static final boolean traceEnabled;
    protected static final boolean debugEnabled;

    static {
        traceEnabled = UndertowLogger.PREDICATE_LOGGER.isTraceEnabled();
        debugEnabled = UndertowLogger.PREDICATE_LOGGER.isDebugEnabled();
    }

    protected final List<PeerMatch> ipv6Matches = new CopyOnWriteArrayList<>();
    protected final List<PeerMatch> ipv4Matches = new CopyOnWriteArrayList<>();
    protected volatile boolean defaultAllow = false;

    public IPMatchBase(final boolean defaultAllow) {
        super();
        this.defaultAllow = defaultAllow;
    }

    public boolean isAllowed(InetAddress address) {
        if (address instanceof Inet4Address) {
            for (PeerMatch rule : ipv4Matches) {
                if (traceEnabled) {
                    UndertowLogger.PREDICATE_LOGGER.tracef("Comparing rule [%s] to IPv4 address %s.", rule.toPredicateString(), address.getHostAddress());
                }
                if (rule.matches(address)) {
                    return !rule.isDeny();
                }
            }
        } else if (address instanceof Inet6Address) {
            for (PeerMatch rule : ipv6Matches) {
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

    protected T addRule(final String peer, final boolean deny) {
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
        return (T) this;
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
    public T addAllow(final String peer) {
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
    public T addDeny(final String peer) {
        return addRule(peer, true);
    }

    public T clearRules() {
        this.ipv4Matches.clear();
        this.ipv6Matches.clear();
        return (T) this;
    }
    public T setDefaultAllow(final boolean defaultAllow) {
        this.defaultAllow = defaultAllow;
        return (T) this;
    }

    public boolean isDefaultAllow() {
        return defaultAllow;
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
        ipv6Matches.add(new PrefixIpV6PeerMatch(peer, mask, pattern, deny));
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

        ipv4Matches.add(new PrefixIpV4PeerMatch(peer, mask, prefix, deny));

    }

    private void addIpV6WildcardMatch(final String peer, boolean deny) {
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
        ipv6Matches.add(new PrefixIpV6PeerMatch(peer, mask, pattern, deny));

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
        ipv4Matches.add(new PrefixIpV4PeerMatch(peer, mask, prefix, deny));
    }

    private void addIpV6ExactMatch(final String peer, final boolean deny) {
        byte[] bytes;
        try {
            bytes = NetworkUtils.parseIpv6AddressToBytes(peer);
            ipv6Matches.add(new ExactIpV6PeerMatch(peer, bytes, deny));
        } catch (IOException e) {
            throw UndertowMessages.MESSAGES.invalidACLAddress(e);
        }
    }

    private void addIpV4ExactMatch(final String peer, final boolean deny) {
        String[] parts = peer.split("\\.");
        byte[] bytes = {(byte) Integer.parseInt(parts[0]), (byte) Integer.parseInt(parts[1]), (byte) Integer.parseInt(parts[2]), (byte) Integer.parseInt(parts[3])};
        ipv4Matches.add(new ExactIpV4PeerMatch(peer, bytes, deny));
    }

    protected abstract static class PeerMatch {

        private final String pattern;
        protected final boolean deny;
        protected PeerMatch(final String pattern, final boolean deny) {
            this.pattern = pattern;
            this.deny = deny;
        }

        public abstract boolean matches(InetAddress address);

        public boolean isDeny() {
            return this.deny;
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

    protected static class ExactIpV4PeerMatch extends PeerMatch {

        private final byte[] address;

        protected ExactIpV4PeerMatch(final String pattern, final byte[] address, final boolean deny) {
            super(pattern, deny);
            this.address = address;
        }

        @Override
        public boolean matches(final InetAddress address) {
            return Arrays.equals(address.getAddress(), this.address);
        }
    }

    protected static class ExactIpV6PeerMatch extends PeerMatch {

        private final byte[] address;

        protected ExactIpV6PeerMatch(final String pattern, final byte[] address, final boolean deny){
            super(pattern, deny);
            this.address = address;
        }

        @Override
        public boolean matches(final InetAddress address) {
            return Arrays.equals(address.getAddress(), this.address);
        }
    }

    protected static class PrefixIpV4PeerMatch extends PeerMatch {

        private final int mask;
        private final int prefix;

        protected PrefixIpV4PeerMatch(final String pattern, final int mask, final int prefix, final boolean deny) {
            super(pattern, deny);
            this.mask = mask;
            this.prefix = prefix;
        }

        @Override
        public boolean matches(final InetAddress address) {
            byte[] bytes = address.getAddress();
            if (bytes == null) {
                return false;
            }
            int addressInt = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
            return (addressInt & mask) == prefix;
        }
    }

    protected static class PrefixIpV6PeerMatch extends PeerMatch {

        private final byte[] mask;
        private final byte[] prefix;

        protected PrefixIpV6PeerMatch(final String pattern, final byte[] mask, final byte[] prefix, final boolean deny) {
            super(pattern, deny);
            this.mask = mask;
            this.prefix = prefix;
            assert mask.length == prefix.length;
        }

        @Override
        public boolean matches(final InetAddress address) {
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

}
