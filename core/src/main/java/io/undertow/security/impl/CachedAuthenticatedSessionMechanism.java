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

import io.undertow.security.api.AuthenticatedSessionManager;
import io.undertow.security.api.AuthenticatedSessionManager.AuthenticatedSession;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;

/**
 * An {@link AuthenticationMechanism} which uses any cached {@link AuthenticatedSession}s.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class CachedAuthenticatedSessionMechanism implements AuthenticationMechanism {

    private final IdentityManager identityManager;

    public CachedAuthenticatedSessionMechanism() {
        this(null);
    }

    public CachedAuthenticatedSessionMechanism(final IdentityManager identityManager) {
        this.identityManager = identityManager;
    }

    @SuppressWarnings("deprecation")
    private IdentityManager getIdentityManager(SecurityContext securityContext) {
        return identityManager != null ? identityManager : securityContext.getIdentityManager();
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        AuthenticatedSessionManager sessionManager = exchange.getAttachment(AuthenticatedSessionManager.ATTACHMENT_KEY);
        if (sessionManager != null) {
            return runCached(exchange, securityContext, sessionManager);
        } else {
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }
    }

    public AuthenticationMechanismOutcome runCached(final HttpServerExchange exchange, final SecurityContext securityContext, final AuthenticatedSessionManager sessionManager) {
        AuthenticatedSession authSession = sessionManager.lookupSession(exchange);
        if (authSession != null) {
            Account account = getIdentityManager(securityContext).verify(authSession.getAccount());
            if (account != null) {
                securityContext.authenticationComplete(account, authSession.getMechanism(), false);
                return AuthenticationMechanismOutcome.AUTHENTICATED;
            } else {
                sessionManager.clearSession(exchange);
                // We know we had a previously authenticated account but for some reason the IdentityManager is no longer
                // accepting it, we now
                return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
            }
        } else {
            // It is possible an AuthenticatedSessionManager could have been available even if there was no chance of it
            // loading a session.
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        }

    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        // This mechanism can only use what is already available and can not send a challenge of it's own.
        return ChallengeResult.NOT_SENT;
    }

}
