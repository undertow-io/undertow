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
import static io.undertow.util.Base64.encode;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.HOST;
import static io.undertow.util.Headers.NEGOTIATE;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.CODE_401;
import static io.undertow.util.WorkerDispatcher.dispatch;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Deque;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;

/**
 * HTTP Handler for GSSAPI / SPNEGO based authentication.
 *
 * GSSAPI authentication is associated with the HTTP connection, as long as a connection is being re-used allow the
 * authentication state to be re-used.
 *
 * TODO - May consider an option to allow it to also be associated with the underlying session but that has it's own risks so
 * would need to come with a warning.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class GSSAPIAuthenticationHandler implements HttpHandler {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String NEGOTIATE_PREFIX = NEGOTIATE + " ";

    private final HttpHandler next;
    private final GSSAPIServerSubjectFactory subjectFactory;

    public GSSAPIAuthenticationHandler(final HttpHandler next, final GSSAPIServerSubjectFactory subjectFactory) {
        this.next = next;
        this.subjectFactory = subjectFactory;
    }

    /**
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange,
     *      io.undertow.server.HttpCompletionHandler)
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        HttpCompletionHandler wrapperCompletionHandler = new GSSAPICompletionHandler(exchange, completionHandler);
        SecurityContext secContext = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        AuthenticationState authState = secContext.getAuthenticationState();

        if (authState == AuthenticationState.REQUIRED || authState == AuthenticationState.NOT_REQUIRED) {
            HttpServerConnection connection = exchange.getConnection();
            NegotiationContext negContext = connection.getAttachment(NegotiationContext.ATTACHMENT_KEY);
            if (negContext != null) {
                exchange.putAttachment(NegotiationContext.ATTACHMENT_KEY, negContext);
                if (negContext.isEstablished()) {
                    secContext.setAuthenticatedPrincipal(negContext.getPrincipal());
                    secContext.setAuthenticationState(AuthenticationState.AUTHENTICATED);
                }
            }
        }

        // Repeat this check in case a cached authentication has now updates the state.
        if (authState == AuthenticationState.REQUIRED || authState == AuthenticationState.NOT_REQUIRED) {
            Deque<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
            if (authHeaders != null) {
                for (String current : authHeaders) {
                    if (current.startsWith(NEGOTIATE_PREFIX)) {
                        String base64Challenge = current.substring(NEGOTIATE_PREFIX.length());
                        byte[] challenge = base64Decode(base64Challenge).getBytes(UTF_8);

                        dispatch(exchange, new GSSAPIRunnable(exchange, wrapperCompletionHandler, challenge));

                        // The request has now potentially been dispatched to a different worker thread, the run method
                        // within GSSAPIRunnable is now responsible for ensuring the request continues.
                        return;
                    }
                }
            }

        }

        // Either an authentication attempt has already occurred or no suitable header has been found in this request,
        // either way let the call continue for the final decision to be made in the SecurityEndHandler.
        next.handleRequest(exchange, wrapperCompletionHandler);
    }

    private String getHostName(final HttpServerExchange exchange) {
        final Deque<String> host = exchange.getRequestHeaders().get(HOST);
        if (host != null) {
            String hostName = host.getFirst();
            if (hostName.contains(":")) {
                hostName = hostName.substring(0, hostName.indexOf(":"));
            }

            return hostName;
        }

        return null;
    }

    private final class GSSAPIRunnable implements Runnable {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;
        private final byte[] challenge;

        private GSSAPIRunnable(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler,
                final byte[] challenge) {
            this.exchange = exchange;
            this.completionHandler = completionHandler;
            this.challenge = challenge;
        }

        public void run() {
            try {
                Subject server = subjectFactory.getSubjectForHost(getHostName(exchange));
                Subject.doAs(server, new AcceptSecurityContext(exchange, challenge));
            } catch (GeneralSecurityException e) {
                SecurityContext secContext = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
                secContext.setAuthenticationState(AuthenticationState.FAILED);
            } catch (PrivilegedActionException e) {
                SecurityContext secContext = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
                secContext.setAuthenticationState(AuthenticationState.FAILED);
            }

            next.handleRequest(exchange, completionHandler);
        }

    }

    private class AcceptSecurityContext implements PrivilegedExceptionAction<Void> {

        private final HttpServerExchange exchange;
        private final byte[] challenge;

        private AcceptSecurityContext(final HttpServerExchange exchange, final byte[] challenge) {
            this.exchange = exchange;
            this.challenge = challenge;
        }

        public Void run() throws GSSException {
            NegotiationContext negContext = exchange.getAttachment(NegotiationContext.ATTACHMENT_KEY);
            if (negContext == null) {
                negContext = new NegotiationContext();
                exchange.putAttachment(NegotiationContext.ATTACHMENT_KEY, negContext);
                // Also cache it on the connection for future calls.
                exchange.getConnection().putAttachment(NegotiationContext.ATTACHMENT_KEY, negContext);
            }

            GSSContext gssContext = negContext.getGssContext();
            if (gssContext == null) {
                GSSManager manager = GSSManager.getInstance();
                gssContext = manager.createContext((GSSCredential) null);

                negContext.setGssContext(gssContext);
            }

            byte[] respToken = gssContext.acceptSecContext(challenge, 0, challenge.length);
            negContext.setResponseToken(respToken);

            if (negContext.isEstablished()) {
                SecurityContext secContext = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
                secContext.setAuthenticatedPrincipal(negContext.getPrincipal());
                secContext.setAuthenticationState(AuthenticationState.AUTHENTICATED);
            }

            return null;
        }

    }

    private class GSSAPICompletionHandler implements HttpCompletionHandler {

        private final HttpServerExchange exchange;
        private final HttpCompletionHandler next;

        private GSSAPICompletionHandler(final HttpServerExchange exchange, final HttpCompletionHandler next) {
            this.exchange = exchange;
            this.next = next;
        }

        @Override
        public void handleComplete() {
            SecurityContext secContext = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
            NegotiationContext negContext = exchange.getAttachment(NegotiationContext.ATTACHMENT_KEY);
            AuthenticationState authenticationState = secContext.getAuthenticationState();

            if (negContext != null) {
                byte[] responseChallenge = negContext.useResponseToken();
                if (responseChallenge != null) {
                    HeaderMap headers = exchange.getResponseHeaders();
                    headers.add(WWW_AUTHENTICATE, NEGOTIATE_PREFIX + new String(encode(responseChallenge), UTF_8));
                    if (authenticationState != AuthenticationState.AUTHENTICATED) {
                        exchange.setResponseCode(CODE_401.getCode());
                    }

                    next.handleComplete();
                    return;
                }
            }

            // An in-progress authentication didn't take this handle call so check if a new challenge is needed.
            if (authenticationState == AuthenticationState.REQUIRED || authenticationState == AuthenticationState.FAILED) {
                exchange.getResponseHeaders().add(WWW_AUTHENTICATE, NEGOTIATE);
                exchange.setResponseCode(CODE_401.getCode());
            }

            next.handleComplete();
        }

    }

    private static class NegotiationContext {

        static AttachmentKey<NegotiationContext> ATTACHMENT_KEY = AttachmentKey.create(NegotiationContext.class);

        private GSSContext gssContext;
        private byte[] responseToken;
        private Principal principal;

        GSSContext getGssContext() {
            return gssContext;
        }

        void setGssContext(GSSContext gssContext) {
            this.gssContext = gssContext;
        }

        byte[] useResponseToken() {
            // The token only needs to be returned once so clear it once used.
            try {
                return responseToken;
            } finally {
                responseToken = null;
            }
        }

        void setResponseToken(byte[] responseToken) {
            this.responseToken = responseToken;
        }

        boolean isEstablished() {
            return gssContext != null ? gssContext.isEstablished() : false;
        }

        Principal getPrincipal() {
            if (isEstablished() == false) {
                throw new IllegalStateException("No established GSSContext to use for the Principal.");
            }

            if (principal == null) {
                try {
                    principal = new KerberosPrincipal(gssContext.getSrcName().toString());
                } catch (GSSException e) {
                    throw new IllegalStateException("Unable to create Principal", e);
                }

            }

            return principal;
        }

    }

}
