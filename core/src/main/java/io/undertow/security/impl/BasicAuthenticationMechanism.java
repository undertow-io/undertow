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

import static io.undertow.UndertowMessages.MESSAGES;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.FlexBase64;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;

/**
 * The authentication handler responsible for BASIC authentication as described by RFC2617
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class BasicAuthenticationMechanism implements AuthenticationMechanism {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

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
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {

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
                        char[] password = plainChallenge.substring(colonPos + 1).toCharArray();

                        IdentityManager idm = securityContext.getIdentityManager();
                        PasswordCredential credential = new PasswordCredential(password);
                        try {
                            final AuthenticationMechanismOutcome result;
                            Account account = idm.verify(userName, credential);
                            if (account != null) {
                                securityContext.authenticationComplete(account, getName());
                                result = AuthenticationMechanismOutcome.AUTHENTICATED;
                            } else {
                                securityContext.authenticationFailed(MESSAGES.authenticationFailed(userName), getName());
                                result = AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                            }
                            return result;
                        } finally {
                            clear(password);
                        }
                    }

                    // By this point we had a header we should have been able to verify but for some reason
                    // it was not correctly structured.
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }
            }
        }

        // No suitable header has been found in this request,
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        exchange.getResponseHeaders().add(WWW_AUTHENTICATE, challenge);
        return new ChallengeResult(true, UNAUTHORIZED);
    }

    private static void clear(final char[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = 0x00;
        }
    }

}
