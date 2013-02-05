/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.security.impl;

import io.undertow.security.api.AuthenticatedSessionManager;
import io.undertow.security.api.AuthenticatedSessionManager.AuthenticatedSession;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcreteIoFuture;

import java.util.concurrent.Executor;

import org.xnio.IoFuture;

/**
 * An {@link AuthenticationMechanism} which uses any cached {@link AuthenticationSession}s.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class CachedAuthenticatedSessionMechanism implements AuthenticationMechanism {

    private static final String NAME = "CACHED";

    @Override
    public String getName() {
        // TODO - The API changes probably mean we do not need to be able to return a name anymore.
        return NAME;
    }

    @Override
    public IoFuture<AuthenticationMechanismOutcome> authenticate(HttpServerExchange exchange, SecurityContext securityContext,
            Executor handOffExecutor) {
        ConcreteIoFuture<AuthenticationMechanismOutcome> result = new ConcreteIoFuture<AuthenticationMechanismOutcome>();

        AuthenticatedSessionManager sessionManager = exchange.getAttachment(AuthenticatedSessionManager.ATTACHMENT_KEY);
        if (sessionManager != null) {
            handOffExecutor.execute(new CachedAuthenticationRunnable(result, exchange, securityContext, sessionManager));
        } else {
            result.setResult(AuthenticationMechanismOutcome.NOT_ATTEMPTED);
        }

        return result;
    }

    private static class CachedAuthenticationRunnable implements Runnable {

        private final ConcreteIoFuture<AuthenticationMechanismOutcome> result;
        private final HttpServerExchange exchange;
        private final SecurityContext securityContext;
        private final AuthenticatedSessionManager sessionManager;

        private CachedAuthenticationRunnable(final ConcreteIoFuture<AuthenticationMechanismOutcome> result,
                final HttpServerExchange exchange, final SecurityContext securityContext,
                final AuthenticatedSessionManager sessionManager) {
            this.result = result;
            this.exchange = exchange;
            this.securityContext = securityContext;
            this.sessionManager = sessionManager;
        }

        @Override
        public void run() {
            AuthenticatedSession authSession = sessionManager.lookupSession(exchange);
            if (authSession != null) {
                Account account = securityContext.getIdentityManager().verify(authSession.getAccount());
                if (account != null) {
                    // This is based on a previously cached account so re-use the mechanism and allow to be cached again.
                    securityContext.authenticationComplete(account, authSession.getMechanism(), true);
                    result.setResult(AuthenticationMechanismOutcome.AUTHENTICATED);
                } else {
                    // We know we had a previously authenticated account but for some reason the IdentityManager is no longer
                    // accepting it, safer to mark as a failed authentication.
                    result.setResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED);
                }
            } else {
                // It is possible an AuthenticatedSessionManager could have been available even if there was no chance of it
                // loading a session.
                result.setResult(AuthenticationMechanismOutcome.NOT_ATTEMPTED);
            }

        }

    }

    @Override
    public IoFuture<ChallengeResult> sendChallenge(HttpServerExchange exchange, SecurityContext securityContext,
            Executor handOffExecutor) {
        // This mechanism can only use what is already available and can not send a challenge of it's own.
        ConcreteIoFuture<ChallengeResult> result = new ConcreteIoFuture<ChallengeResult>();
        result.setResult(new ChallengeResult(false));

        return result;
    }

}
