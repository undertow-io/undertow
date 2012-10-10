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

import static io.undertow.server.handlers.security.DigestAuthorizationToken.parseHeader;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.DIGEST;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.CODE_401;
import static io.undertow.util.WorkerDispatcher.dispatch;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;

/**
 * {@link HttpHandler} to handle HTTP Digest authentication, both according to RFC-2617 and draft update to allow additional
 * algorithms to be used.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class DigestAuthenticationHandler implements HttpHandler {

    private static final String DIGEST_PREFIX = DIGEST + " ";
    private static final int PREFIX_LENGTH = DIGEST_PREFIX.length();

    private final HttpHandler next;
    /**
     * The {@link List} of supported algorithms, this is assumed to be in priority order.
     */
    private final List<DigestAlgorithm> supportedAlgorithms;
    private final List<DigestQop> supportedQops;
    private final String qopString;
    private final String realmName; // TODO - Will offer choice once backing store API/SPI is in.
    private final CallbackHandler callbackHandler;
    private final NonceManager nonceManager;

    // Some form of nonce factory.

    // Where do session keys fit? Do we just hang onto a session key or keep visiting the user store to check if the password
    // has changed?
    // Maybe even support registration of a session so it can be invalidated?

    public DigestAuthenticationHandler(final HttpHandler next, final List<DigestAlgorithm> supportedAlgorithms,
            final List<DigestQop> supportedQops, final String realmName, final CallbackHandler callbackHandler,
            final NonceManager nonceManager) {
        this.next = next;
        this.supportedAlgorithms = supportedAlgorithms;
        this.supportedQops = supportedQops;
        this.realmName = realmName;
        this.callbackHandler = callbackHandler;
        this.nonceManager = nonceManager;

        if (supportedQops.size() > 0) {
            StringBuilder sb = new StringBuilder();
            Iterator<DigestQop> it = supportedQops.iterator();
            sb.append(it.next().getToken());
            while (it.hasNext()) {
                sb.append(",").append(it.next().getToken());
            }
            qopString = sb.toString();
        } else {
            qopString = null;
        }
    }

    /**
     *
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange,
     *      io.undertow.server.HttpCompletionHandler)
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        HttpCompletionHandler wrapperCompletionHandler = new DigestCompletionHandler(exchange, completionHandler);
        SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        AuthenticationState authState = context.getAuthenticationState();

        if (authState == AuthenticationState.REQUIRED || authState == AuthenticationState.NOT_REQUIRED) {
            Deque<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
            if (authHeaders != null) {
                for (String current : authHeaders) {
                    if (current.startsWith(DIGEST_PREFIX)) {
                        String digestChallenge = current.substring(PREFIX_LENGTH);

                        try {
                            Map<DigestAuthorizationToken, String> parsedHeader = parseHeader(digestChallenge);

                            dispatch(exchange, new DigestRunnable(exchange, wrapperCompletionHandler, parsedHeader));

                            // The request has now potentially been dispatched to a different worker thread, the run method
                            // within BasicRunnable is now responsible for ensuring the request continues.
                            return;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // By this point we had a header we should have been able to verify but for some reason
                    // it was not correctly structured.
                    context.setAuthenticationState(AuthenticationState.FAILED);
                }
            }

        }

        // Either an authentication attempt has already occurred or no suitable header has been found in this request,
        // either way let the call continue for the final decision to be made in the SecurityEndHandler.
        next.handleRequest(exchange, wrapperCompletionHandler);

    }

    private final class DigestRunnable implements Runnable {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;
        private final Map<DigestAuthorizationToken, String> parsedHeader;

        private DigestRunnable(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler,
                Map<DigestAuthorizationToken, String> parsedHeader) {
            this.exchange = exchange;
            this.completionHandler = completionHandler;
            this.parsedHeader = parsedHeader;
        }

        public void run() {

        }
    }

    private final class DigestCompletionHandler implements HttpCompletionHandler {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler next;

        private DigestCompletionHandler(final HttpServerExchange exchange, final HttpCompletionHandler next) {
            this.exchange = exchange;
            this.next = next;
        }

        public void handleComplete() {

            SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
            AuthenticationState authenticationState = context.getAuthenticationState();

            if (authenticationState == AuthenticationState.REQUIRED || authenticationState == AuthenticationState.FAILED) {
                // Need to dispatch to a Runnable here in-case the nonce management is blocking and we
                // don't know if we were already dispatched to a different worker thread.
                dispatch(exchange, new SendChallengeRunnable());

                return;
            } else if (authenticationState == AuthenticationState.AUTHENTICATED) {
                // In this case we may be sending an Authentication-Info header.
                // Depending on the chosen QOP we may need to be providing a hash
                // including the message body - also if the nonce has change we may
                // need to send an alternative.

                // Need to check if the AUTHENTICATED state was due to this handler but if so
                // we know it has already been dispatched.

            }

            next.handleComplete();
        }

        private class SendChallengeRunnable implements Runnable {

            public void run() {
                StringBuilder rb = new StringBuilder(DIGEST_PREFIX);
                rb.append(Headers.REALM.toString()).append("=\"").append(realmName).append("\",");
                rb.append(Headers.DOMAIN.toString()).append("=\"/\","); // TODO - This will need to be generated
                                                                        // based on security constraints.
                rb.append(Headers.NONCE.toString()).append("=\"").append(nonceManager.nextNonce(null)).append("\",");
                // Not currently using OPAQUE as it offers no integrity, used for session data leaves it vulnerable to
                // session fixation type issues as well.
                rb.append(Headers.OPAQUE.toString()).append("=\"00000000000000000000000000000000\"");
                // No stale in the initial challenge, will optionally enable stale for a failed authentication.
                if (supportedAlgorithms.size() > 0) {
                    // This header will need to be repeated once for each algorithm.
                    rb.append(",").append(Headers.ALGORITHM.toString()).append("=%s");
                }
                if (qopString != null) {
                    rb.append(",").append(Headers.QOP.toString()).append("=\"").append(qopString).append("\"");
                }

                String theChallenge = rb.toString();
                HeaderMap responseHeader = exchange.getResponseHeaders();
                if (supportedAlgorithms.size() > 0) {
                    for (DigestAlgorithm current : supportedAlgorithms) {
                        responseHeader.add(WWW_AUTHENTICATE, String.format(theChallenge, current.getToken()));
                    }
                } else {
                    responseHeader.add(WWW_AUTHENTICATE, theChallenge);
                }
                exchange.setResponseCode(CODE_401.getCode());

                next.handleComplete();
            }
        }

    }

}
