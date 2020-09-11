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

package io.undertow.security.impl;

import io.undertow.UndertowMessages;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HttpString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome.AUTHENTICATED;
import static io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_ATTEMPTED;
import static io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_AUTHENTICATED;

/**
 * A authentication mechanism that requires the presence of two headers in the request. One of these will be used as a
 * principal and the other as a password credential.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class GenericHeaderAuthenticationMechanism implements AuthenticationMechanism {

    public static final AuthenticationMechanismFactory FACTORY = new Factory();

    public static final String NAME = "GENERIC_HEADER";
    public static final String IDENTITY_HEADER = "identity-header";
    public static final String SESSION_HEADER = "session-header";

    private final String mechanismName;
    private final List<HttpString> identityHeaders;
    private final List<String> sessionCookieNames;
    private final IdentityManager identityManager;

    public GenericHeaderAuthenticationMechanism(String mechanismName, List<HttpString> identityHeaders, List<String> sessionCookieNames, IdentityManager identityManager) {
        this.mechanismName = mechanismName;
        this.identityHeaders = identityHeaders;
        this.sessionCookieNames = sessionCookieNames;
        this.identityManager = identityManager;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        String principal = getPrincipal(exchange);
        if(principal == null) {
            return NOT_ATTEMPTED;
        }
        String session = getSession(exchange);
        if(session == null) {
            return NOT_ATTEMPTED;
        }
        Account account = identityManager.verify(principal, new PasswordCredential(session.toCharArray()));
        if(account == null) {
            securityContext.authenticationFailed(UndertowMessages.MESSAGES.authenticationFailed(principal), mechanismName);
            return NOT_AUTHENTICATED;
        }
        securityContext.authenticationComplete(account, mechanismName, false);
        return AUTHENTICATED;
    }

    private String getSession(HttpServerExchange exchange) {
        for (String header : sessionCookieNames) {
            for (Cookie cookie : exchange.requestCookies()) {
                if (header.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String getPrincipal(HttpServerExchange exchange) {
        for(HttpString header : identityHeaders) {
            String res = exchange.getRequestHeaders().getFirst(header);
            if(res != null) {
                return res;
            }
        }
        return null;
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        return ChallengeResult.NOT_SENT;
    }


    public static class Factory implements AuthenticationMechanismFactory {

        @Deprecated
        public Factory(IdentityManager identityManager) {
        }

        public Factory() {

        }

        @Override
        public AuthenticationMechanism create(String mechanismName, IdentityManager identityManager, FormParserFactory formParserFactory, Map<String, String> properties) {
            String identity = properties.get(IDENTITY_HEADER);
            if(identity == null) {
                throw UndertowMessages.MESSAGES.authenticationPropertyNotSet(mechanismName, IDENTITY_HEADER);
            }
            String session = properties.get(SESSION_HEADER);
            if(session == null) {
                throw UndertowMessages.MESSAGES.authenticationPropertyNotSet(mechanismName, SESSION_HEADER);
            }
            List<HttpString> ids = new ArrayList<>();
            for(String s : identity.split(",")) {
                ids.add(new HttpString(s));
            }
            List<String> sessions = new ArrayList<>();
            for(String s : session.split(",")) {
                sessions.add(s);
            }
            return new GenericHeaderAuthenticationMechanism(mechanismName, ids, sessions, identityManager);
        }
    }
}
