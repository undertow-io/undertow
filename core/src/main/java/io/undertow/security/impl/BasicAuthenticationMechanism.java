/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.CODE_401;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.FlexBase64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Executor;

import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;

/**
 * The authentication handler responsible for BASIC authentication as described by RFC2617
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class BasicAuthenticationMechanism implements AuthenticationMechanism {

    private static Charset UTF_8 = Charset.forName("UTF-8");

    private final String name;
    private final String challenge;

    private static final String BASIC_PREFIX = BASIC + " ";
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final String COLON = ":";

    // TODO - Can we get the realm name from the IDM?
    public BasicAuthenticationMechanism(final String realmName) {
        this(realmName, "BASIC");
    }

    public BasicAuthenticationMechanism(final String realmName, final String mechanismName) {
        this.challenge = BASIC_PREFIX + "realm=\"" + realmName + "\"";
        this.name = mechanismName;
    }

    public String getName() {
        return name;
    }

    /**
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public IoFuture<AuthenticationMechanismOutcome> authenticate(HttpServerExchange exchange, SecurityContext securityContext,
            Executor handOffExecutor) {
        ConcreteIoFuture<AuthenticationMechanismOutcome> result = new ConcreteIoFuture<AuthenticationMechanismOutcome>();

        List<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.startsWith(BASIC_PREFIX)) {
                    String base64Challenge = current.substring(PREFIX_LENGTH);
                    String plainChallenge = null;
                    try {
                        ByteBuffer decode = FlexBase64.decode(base64Challenge);
                        plainChallenge = new String(decode.array(), decode.arrayOffset(), decode.limit(), UTF_8);
                    } catch (IOException e) {
                    }
                    int colonPos;
                    if (plainChallenge != null && (colonPos = plainChallenge.indexOf(COLON)) > -1) {
                        String userName = plainChallenge.substring(0, colonPos);
                        String password = plainChallenge.substring(colonPos + 1);
                        handOffExecutor.execute(new BasicRunnable(securityContext, result, userName, password.toCharArray()));

                        // The request has now potentially been dispatched to a different worker thread, the run method
                        // within BasicRunnable is now responsible for ensuring the request continues.
                        return result;
                    }

                    // By this point we had a header we should have been able to verify but for some reason
                    // it was not correctly structured.
                    result.setResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED);
                    return result;
                }
            }
        }

        // No suitable header has been found in this request,
        result.setResult(AuthenticationMechanismOutcome.NOT_ATTEMPTED);
        return result;
    }

    private final class BasicRunnable implements Runnable {

        private final SecurityContext securityContext;
        private final ConcreteIoFuture<AuthenticationMechanismOutcome> result;
        private final String userName;
        private final char[] password;

        private BasicRunnable(final SecurityContext securityContext,
                final ConcreteIoFuture<AuthenticationMechanismOutcome> result, final String userName, final char[] password) {
            this.securityContext = securityContext;
            this.result = result;
            this.userName = userName;
            this.password = password;
        }

        @Override
        public void run() {
            // To reach this point we must have been supplied a username and password.
            AuthenticationMechanismOutcome result = null;
            IdentityManager idm = securityContext.getIdentityManager();
            PasswordCredential credential = new PasswordCredential(password);
            try {
                Account account = idm.verify(userName, credential);
                if (account != null) {
                    securityContext.authenticationComplete(account, getName(), false);
                    result = AuthenticationMechanismOutcome.AUTHENTICATED;
                }
            } finally {
                this.result.setResult(result != null ? result : AuthenticationMechanismOutcome.NOT_AUTHENTICATED);

                for (int i = 0; i < password.length; i++) {
                    password[i] = 0x00;
                }
            }
        }
    }

    @Override
    public IoFuture<ChallengeResult> sendChallenge(HttpServerExchange exchange, SecurityContext securityContext,
            Executor handOffExecutor) {
        exchange.getResponseHeaders().add(WWW_AUTHENTICATE, challenge);
        return new FinishedIoFuture<AuthenticationMechanism.ChallengeResult>(new ChallengeResult(true, CODE_401));
    }

}
