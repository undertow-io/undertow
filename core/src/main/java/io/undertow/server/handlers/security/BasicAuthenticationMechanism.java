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
package io.undertow.server.handlers.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.Arrays;
import java.util.Deque;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.FlexBase64;
import org.xnio.IoFuture;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.CODE_401;
import static io.undertow.util.WorkerDispatcher.dispatch;

/**
 * The authentication handler responsible for BASIC authentication as described by RFC2617
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class BasicAuthenticationMechanism implements AuthenticationMechanism {

    private static Charset UTF_8 = Charset.forName("UTF-8");

    private final String challenge;
    private final CallbackHandler callbackHandler;

    private static final String BASIC_PREFIX = BASIC + " ";
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final String COLON = ":";

    public BasicAuthenticationMechanism(final String realmName, final CallbackHandler callbackHandler) {
        this.challenge = BASIC_PREFIX + "realm=\"" + realmName + "\"";
        this.callbackHandler = callbackHandler;
    }

    /**
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange,
     *      io.undertow.server.HttpCompletionHandler)
     */
    @Override
    public IoFuture<AuthenticationResult> authenticate(final HttpServerExchange exchange) {
        ConcreteIoFuture<AuthenticationResult> result = new ConcreteIoFuture<AuthenticationResult>();

        Deque<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
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
                        dispatch(exchange, new BasicRunnable(result, userName, password.toCharArray()));

                        // The request has now potentially been dispatched to a different worker thread, the run method
                        // within BasicRunnable is now responsible for ensuring the request continues.
                        return result;
                    }

                    // By this point we had a header we should have been able to verify but for some reason
                    // it was not correctly structured.
                    result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
                    return result;
                }
            }
        }

        // No suitable header has been found in this request,
        result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_ATTEMPTED, null));
        return result;
    }

    private final class BasicRunnable implements Runnable {

        private final ConcreteIoFuture<AuthenticationResult> result;
        private final String userName;
        private char[] password;

        private BasicRunnable(ConcreteIoFuture<AuthenticationResult> result, final String userName, final char[] password) {
            this.result = result;
            this.userName = userName;
            this.password = password;
        }

        @Override
        public void run() {
            // To reach this point we must have been supplied a username and password.

            // TODO - This section will be re-worked to plug in a more appropriate identity repo style API / SPI.
            NameCallback ncb = new NameCallback("Username", userName);
            PasswordCallback pcp = new PasswordCallback("Password", false);
            RoleCallback rcb = new RoleCallback();

            try {
                callbackHandler.handle(new Callback[] { ncb, pcp, rcb});

                if (Arrays.equals(password, pcp.getPassword())) {

                    Principal principal = (new Principal() {

                        @Override
                        public String getName() {
                            return userName;
                        }
                    });
                    result.setResult(new AuthenticationResult(principal, AuthenticationOutcome.AUTHENTICATED, rcb.getRoles()));
                }

            } catch (IOException e) {
            } catch (UnsupportedCallbackException e) {
            }
            result.setResult(new AuthenticationResult(null, AuthenticationOutcome.NOT_AUTHENTICATED, null));
        }
    }

    public void handleComplete(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        if (Util.shouldChallenge(exchange)) {
            exchange.getResponseHeaders().add(WWW_AUTHENTICATE, challenge);
            exchange.setResponseCode(CODE_401.getCode());
        }

        completionHandler.handleComplete();
    }

}
