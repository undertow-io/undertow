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

import io.undertow.UndertowMessages;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.StatusCodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Handler that can accept or reject a request based on an attribute of the remote peer
 *
 * todo: should we support non-regex values for performance reasons?
 * @author Stuart Douglas
 * @author Andre Dietisheim
 */
public class AccessControlListHandler implements HttpHandler {

    private volatile HttpHandler next;
    private volatile boolean defaultAllow = false;
    private final ExchangeAttribute attribute;
    private final List<AclMatch> acl = new CopyOnWriteArrayList<>();

    public AccessControlListHandler(final HttpHandler next, ExchangeAttribute attribute) {
        this.next = next;
        this.attribute = attribute;
    }

    public AccessControlListHandler(ExchangeAttribute attribute) {
        this.attribute = attribute;
        this.next = ResponseCodeHandler.HANDLE_404;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        String attribute = this.attribute.readAttribute(exchange);
        if (isAllowed(attribute)) {
            next.handleRequest(exchange);
        } else {
            exchange.setStatusCode(StatusCodes.FORBIDDEN);
            exchange.endExchange();
        }
    }

    //package private for unit tests
    boolean isAllowed(String attribute) {
        if (attribute != null) {
            for (AclMatch rule : acl) {
                if (rule.matches(attribute)) {
                    return !rule.isDeny();
                }
            }
        }
        return defaultAllow;
    }

    public boolean isDefaultAllow() {
        return defaultAllow;
    }

    public AccessControlListHandler setDefaultAllow(final boolean defaultAllow) {
        this.defaultAllow = defaultAllow;
        return this;
    }

    public HttpHandler getNext() {
        return next;
    }

    public AccessControlListHandler setNext(final HttpHandler next) {
        this.next = next;
        return this;
    }

    /**
     * Adds an allowed user agent peer to the ACL list
     * <p>
     * User agent may be given as regex
     *
     * @param pattern The pattern to add to the ACL
     */
    public AccessControlListHandler addAllow(final String pattern) {
        return addRule(pattern, false);
    }

    /**
     * Adds an denied user agent to the ACL list
     * <p>
     * User agent may be given as regex
     *
     * @param pattern The user agent to add to the ACL
     */
    public AccessControlListHandler addDeny(final String pattern) {
        return addRule(pattern, true);
    }

    public AccessControlListHandler clearRules() {
        this.acl.clear();
        return this;
    }

    private AccessControlListHandler addRule(final String userAgent, final boolean deny) {
        this.acl.add(new AclMatch(deny, userAgent));
        return this;
    }

    static class AclMatch {

        private final boolean deny;
        private final Pattern pattern;

        protected AclMatch(final boolean deny, final String pattern) {
            this.deny = deny;
            this.pattern = createPattern(pattern);
        }

        private Pattern createPattern(final String pattern) {
            try {
                return Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw UndertowMessages.MESSAGES.notAValidRegularExpressionPattern(pattern);
            }
        }

        boolean matches(final String attribute) {
            return pattern.matcher(attribute).matches();
        }

        boolean isDeny() {
            return deny;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "{"
                    + "deny=" + deny
                    + ", pattern='" + pattern + '\''
                    + '}';
        }
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "access-control";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("acl", String[].class);
            params.put("default-allow", boolean.class);
            params.put("attribute", ExchangeAttribute.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final HashSet<String> ret = new HashSet<>();
            ret.add("acl");
            ret.add("attribute");
            return ret;
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {

            String[] acl = (String[]) config.get("acl");
            Boolean defaultAllow = (Boolean) config.get("default-allow");
            ExchangeAttribute attribute = (ExchangeAttribute) config.get("attribute");

            List<AclMatch> peerMatches = new ArrayList<>();
            for(String rule :acl) {
                String[] parts = rule.split(" ");
                if(parts.length != 2) {
                    throw UndertowMessages.MESSAGES.invalidAclRule(rule);
                }
                if(parts[1].trim().equals("allow")) {
                    peerMatches.add(new AclMatch(false, parts[0].trim()));
                } else if(parts[1].trim().equals("deny")) {
                    peerMatches.add(new AclMatch(true, parts[0].trim()));
                } else {
                    throw UndertowMessages.MESSAGES.invalidAclRule(rule);
                }
            }
            return new Wrapper(peerMatches, defaultAllow == null ? false : defaultAllow, attribute);
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final List<AclMatch> peerMatches;
        private final boolean defaultAllow;
        private final ExchangeAttribute attribute;


        private Wrapper(List<AclMatch> peerMatches, boolean defaultAllow, ExchangeAttribute attribute) {
            this.peerMatches = peerMatches;
            this.defaultAllow = defaultAllow;
            this.attribute = attribute;
        }


        @Override
        public HttpHandler wrap(HttpHandler handler) {
            AccessControlListHandler res = new AccessControlListHandler(handler, attribute);
            for(AclMatch match: peerMatches) {
                if(match.deny) {
                    res.addDeny(match.pattern.pattern());
                } else {
                    res.addAllow(match.pattern.pattern());
                }
            }
            res.setDefaultAllow(defaultAllow);
            return res;
        }
    }
}
