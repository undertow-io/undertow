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

import static io.undertow.util.Base64.base64Decode;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.CODE_401;
import static io.undertow.util.WorkerDispatcher.dispatch;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Deque;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 * The authentication handler responsible for BASIC authentication as described by RFC2617
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class BasicAuthenticationHandler implements HttpHandler {

    private final HttpHandler next;
    private final String challenge;
    private final CallbackHandler callbackHandler;

    private static final String BASIC_PREFIX = BASIC + " ";
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final String COLON = ":";

    public BasicAuthenticationHandler(final HttpHandler next, final String realmName, final CallbackHandler callbackHandler) {
        this.next = next;
        challenge = BASIC_PREFIX + "realm=\"" + realmName + "\"";
        this.callbackHandler = callbackHandler;
    }

    /**
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange,
     *      io.undertow.server.HttpCompletionHandler)
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        HttpCompletionHandler wrapperCompletionHandler = new BasicCompletionHandler(exchange, completionHandler);
        SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        AuthenticationState authState = context.getAuthenticationState();

        if (authState == AuthenticationState.REQUIRED || authState == AuthenticationState.NOT_REQUIRED) {
            Deque<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
            if (authHeaders != null) {
                for (String current : authHeaders) {
                    if (current.startsWith(BASIC_PREFIX)) {
                        String base64Challenge = current.substring(PREFIX_LENGTH);
                        String plainChallenge = base64Decode(base64Challenge);
                        int colonPos = plainChallenge.indexOf(COLON);
                        if (colonPos > -1) {
                            String userName = plainChallenge.substring(0, colonPos);
                            String password = plainChallenge.substring(colonPos + 1);
                            dispatch(exchange,
                                    new BasicRunnable(exchange, wrapperCompletionHandler, userName, password.toCharArray()));

                            // The request has now potentially been dispatched to a different worker thread, the run method
                            // within BasicRunnable is now responsible for ensuring the request continues.
                            return;
                        }

                        // By this point we had a header we should have been able to verify but for some reason
                        // it was not correctly structured.
                        context.setAuthenticationState(AuthenticationState.FAILED);
                    }
                }
            }
        }

        // Either an authentication attempt has already occurred or no suitable header has been found in this request,
        // either way let the call continue for the final decision to be made in the SecurityEndHandler.
        next.handleRequest(exchange, wrapperCompletionHandler);
    }

    private final class BasicRunnable implements Runnable {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;
        private final String userName;
        private char[] password;

        private BasicRunnable(HttpServerExchange exchange, HttpCompletionHandler completionHandler, final String userName,
                final char[] password) {
            this.exchange = exchange;
            this.completionHandler = completionHandler;
            this.userName = userName;
            this.password = password;
        }

        @Override
        public void run() {
            // To reach this point we must have been supplied a username and password.
            SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);

            // TODO - This section will be re-worked to plug in a more appropriate identity repo style API / SPI.
            NameCallback ncb = new NameCallback("Username", userName);
            PasswordCallback pcp = new PasswordCallback("Password", false);

            try {
                callbackHandler.handle(new Callback[] { ncb, pcp });

                if (Arrays.equals(password, pcp.getPassword())) {
                    context.setAuthenticationState(AuthenticationState.AUTHENTICATED);
                    context.setAuthenticatedPrincipal(new Principal() {

                        @Override
                        public String getName() {
                            return userName;
                        }
                    });
                } else {
                    context.setAuthenticationState(AuthenticationState.FAILED);
                }

            } catch (IOException e) {
                context.setAuthenticationState(AuthenticationState.FAILED);
            } catch (UnsupportedCallbackException e) {
                context.setAuthenticationState(AuthenticationState.FAILED);
            }

            next.handleRequest(exchange, completionHandler);
        }
    }

    private class BasicCompletionHandler implements HttpCompletionHandler {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler next;

        private BasicCompletionHandler(final HttpServerExchange exchange, final HttpCompletionHandler next) {
            this.exchange = exchange;
            this.next = next;
        }

        public void handleComplete() {
            SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
            AuthenticationState authenticationState = context.getAuthenticationState();
            // TODO Including Failed in this check to allow a subsequent attemp, may prefer a utility method somethere
            // e.g. shouldSendChallenge()
            if (authenticationState == AuthenticationState.REQUIRED || authenticationState == AuthenticationState.FAILED) {
                exchange.getResponseHeaders().add(WWW_AUTHENTICATE, challenge);
                exchange.setResponseCode(CODE_401.getCode());
            }

            next.handleComplete();
        }

    }

}
