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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.undertow.UndertowMessages;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

/**
 * Class to handle ip-match ACL predicate
 * @author baranowb
 */
public class IPMatchPredicate extends IPMatchBase<IPMatchPredicate> implements Predicate{

    public IPMatchPredicate(boolean defaultAllow) {
        super(defaultAllow);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        final InetSocketAddress peer = exchange.getSourceAddress();
        return isAllowed(peer.getAddress());
    }

    @Override
    public String toString() {
        //ip-match( default-allow=false, acl={'127.0.0.* allow', '192.168.1.123 deny'})
        String predicate = "ip-match( default-allow=" + defaultAllow + ", acl={ ";
        List<PeerMatch> acl = new ArrayList<>();
        acl.addAll(super.ipv4Matches);
        acl.addAll(super.ipv6Matches);

        predicate += acl.stream().map(s -> "'" + s.toPredicateString() + "'").collect(Collectors.joining(", "));
        predicate += " }";
        predicate += " )";
        return predicate;
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "ip-match";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("acl", String[].class);
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
        public Predicate build(final Map<String, Object> config) {
            String[] acl = (String[]) config.get("acl");
            Boolean defaultAllow = (Boolean) config.get("default-allow");
            IPMatchPredicate predicate = new IPMatchPredicate(defaultAllow != null ? defaultAllow : false);
            for (String rule : acl) {
                String[] parts = rule.split(" ");
                if (parts.length != 2) {
                    throw UndertowMessages.MESSAGES.invalidAclRule(rule);
                }
                if (parts[1].trim().equals("allow")) {
                    predicate.addAllow(parts[0].trim());
                } else if (parts[1].trim().equals("deny")) {
                    predicate.addDeny(parts[0].trim());
                } else {
                    throw UndertowMessages.MESSAGES.invalidAclRule(rule);
                }
            }
            return predicate;
        }
    }
}
