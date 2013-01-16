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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Deque;
import java.util.concurrent.Executor;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.GSSAPIServerSubjectFactory;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.FlexBase64;
import io.undertow.util.HeaderMap;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.xnio.IoFuture;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.HOST;
import static io.undertow.util.Headers.NEGOTIATE;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.CODE_401;

/**
 * {@link io.undertow.security.api.AuthenticationMechanism} for GSSAPI / SPNEGO based authentication.
 *
 * GSSAPI authentication is associated with the HTTP connection, as long as a connection is being re-used allow the
 * authentication state to be re-used.
 *
 * TODO - May consider an option to allow it to also be associated with the underlying session but that has it's own risks so
 * would need to come with a warning.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class GSSAPIAuthenticationMechanism implements AuthenticationMechanism {

    private static final String NEGOTIATE_PREFIX = NEGOTIATE + " ";

    private final GSSAPIServerSubjectFactory subjectFactory;

    public GSSAPIAuthenticationMechanism(final GSSAPIServerSubjectFactory subjectFactory) {
        this.subjectFactory = subjectFactory;
    }

    public String getName() {
        return "SPNEGO";
    }

    @Override
    public IoFuture<AuthenticationMechanismResult> authenticate(HttpServerExchange exchange, final IdentityManager identityManager, final Executor handOffExecutor) {
        ConcreteIoFuture<AuthenticationMechanismResult> result = new ConcreteIoFuture<AuthenticationMechanismResult>();
        HttpServerConnection connection = exchange.getConnection();
        NegotiationContext negContext = connection.getAttachment(NegotiationContext.ATTACHMENT_KEY);
        if (negContext != null) {
            exchange.putAttachment(NegotiationContext.ATTACHMENT_KEY, negContext);
            if (negContext.isEstablished()) {
                final Principal principal = negContext.getPrincipal();
                final Account account = identityManager.lookupAccount(principal.getName());
                result.setResult(new AuthenticationMechanismResult(principal, account));
            }
        }

        Deque<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.startsWith(NEGOTIATE_PREFIX)) {
                    String base64Challenge = current.substring(NEGOTIATE_PREFIX.length());
                    try {
                        ByteBuffer challenge = FlexBase64.decode(base64Challenge);
                        handOffExecutor.execute(new GSSAPIRunnable(result, exchange, challenge, identityManager));
                        // The request has now potentially been dispatched to a different worker thread, the run method
                        // within GSSAPIRunnable is now responsible for ensuring the request continues.
                        return result;
                    } catch (IOException e) {
                    }

                    // By this point we had a header we should have been able to verify but for some reason
                    // it was not correctly structured.
                    result.setResult(new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED));
                    return result;
                }
            }
        }

        // No suitable header was found so authentication was not even attempted.
        result.setResult(new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_ATTEMPTED));
        return result;
    }

    public void sendChallenge(HttpServerExchange exchange) {
        NegotiationContext negContext = exchange.getAttachment(NegotiationContext.ATTACHMENT_KEY);

        boolean authAdded = false;

        if (negContext != null) {
            byte[] responseChallenge = negContext.useResponseToken();
            exchange.putAttachment(NegotiationContext.ATTACHMENT_KEY, null);
            if (responseChallenge != null) {
                HeaderMap headers = exchange.getResponseHeaders();
                headers.add(WWW_AUTHENTICATE, NEGOTIATE_PREFIX + FlexBase64.encodeString(responseChallenge, false));
                authAdded = true;
            }
        }

        if (Util.shouldChallenge(exchange)) {
            if (!authAdded) {
                exchange.getResponseHeaders().add(WWW_AUTHENTICATE, NEGOTIATE.toString());
            }
            // We only set this is actually challenging the client, the previously set header may have been a FYI for the
            // client.
            exchange.setResponseCode(CODE_401.getCode());
        }
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

        private final ConcreteIoFuture<AuthenticationMechanismResult> result;
        private final HttpServerExchange exchange;
        private final ByteBuffer challenge;
        private final IdentityManager identityManager;

        private GSSAPIRunnable(final ConcreteIoFuture<AuthenticationMechanismResult> result, final HttpServerExchange exchange,
                               final ByteBuffer challenge, final IdentityManager identityManager) {
            this.result = result;
            this.exchange = exchange;
            this.challenge = challenge;
            this.identityManager = identityManager;
        }

        public void run() {
            try {
                Subject server = subjectFactory.getSubjectForHost(getHostName(exchange));
                // The AcceptSecurityContext takes over responsibility for setting the result.
                Subject.doAs(server, new AcceptSecurityContext(result, exchange, challenge, identityManager));
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
                result.setResult(new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED));
            } catch (PrivilegedActionException e) {
                e.printStackTrace();
                result.setResult(new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED));
            }
        }

    }

    private class AcceptSecurityContext implements PrivilegedExceptionAction<Void> {

        private final ConcreteIoFuture<AuthenticationMechanismResult> result;
        private final HttpServerExchange exchange;
        private final ByteBuffer challenge;
        private final IdentityManager identityManager;

        private AcceptSecurityContext(final ConcreteIoFuture<AuthenticationMechanismResult> result, final HttpServerExchange exchange,
                                      final ByteBuffer challenge, final IdentityManager identityManager) {
            this.result = result;
            this.exchange = exchange;
            this.challenge = challenge;
            this.identityManager = identityManager;
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

            byte[] respToken = gssContext.acceptSecContext(challenge.array(), challenge.arrayOffset(), challenge.limit());
            negContext.setResponseToken(respToken);

            if (negContext.isEstablished()) {
                final Principal principal = negContext.getPrincipal();
                final Account account = identityManager.lookupAccount(principal.getName());
                result.setResult(new AuthenticationMechanismResult(principal, account));
            } else {
                // This isn't a failure but as the context is not established another round trip with the client is needed.
                result.setResult(new AuthenticationMechanismResult(AuthenticationMechanismOutcome.NOT_AUTHENTICATED));
            }

            return null;
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
