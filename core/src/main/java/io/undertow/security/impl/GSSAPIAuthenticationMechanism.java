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
import java.util.List;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.GSSAPIServerSubjectFactory;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.GSSContextCredential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.FlexBase64;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.HOST;
import static io.undertow.util.Headers.NEGOTIATE;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;

/**
 * {@link io.undertow.security.api.AuthenticationMechanism} for GSSAPI / SPNEGO based authentication.
 * <p/>
 * GSSAPI authentication is associated with the HTTP connection, as long as a connection is being re-used allow the
 * authentication state to be re-used.
 * <p/>
 * TODO - May consider an option to allow it to also be associated with the underlying session but that has it's own risks so
 * would need to come with a warning.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class GSSAPIAuthenticationMechanism implements AuthenticationMechanism {

    private static final String NEGOTIATION_PLAIN = NEGOTIATE.toString();
    private static final String NEGOTIATE_PREFIX = NEGOTIATE + " ";
    private final String name = "SPNEGO";

    private final GSSAPIServerSubjectFactory subjectFactory;

    public GSSAPIAuthenticationMechanism(final GSSAPIServerSubjectFactory subjectFactory) {
        this.subjectFactory = subjectFactory;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange,
                                                       final SecurityContext securityContext) {
        HttpServerConnection connection = exchange.getConnection();
        NegotiationContext negContext = connection.getAttachment(NegotiationContext.ATTACHMENT_KEY);
        if (negContext != null) {
            exchange.putAttachment(NegotiationContext.ATTACHMENT_KEY, negContext);
            if (negContext.isEstablished()) {
                IdentityManager identityManager = securityContext.getIdentityManager();
                final Account account = identityManager.verify(new GSSContextCredential(negContext.getGssContext()));
                if (account != null) {
                    securityContext.authenticationComplete(account, name);
                    return AuthenticationMechanismOutcome.AUTHENTICATED;
                } else {
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }
            }
        }

        List<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.startsWith(NEGOTIATE_PREFIX)) {
                    String base64Challenge = current.substring(NEGOTIATE_PREFIX.length());
                    try {
                        ByteBuffer challenge = FlexBase64.decode(base64Challenge);
                        return runGSSAPI(exchange, challenge, securityContext);
                    } catch (IOException e) {
                    }

                    // By this point we had a header we should have been able to verify but for some reason
                    // it was not correctly structured.
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }
            }
        }

        // No suitable header was found so authentication was not even attempted.
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        NegotiationContext negContext = exchange.getAttachment(NegotiationContext.ATTACHMENT_KEY);

        String header = NEGOTIATION_PLAIN;

        if (negContext != null) {
            byte[] responseChallenge = negContext.useResponseToken();
            exchange.putAttachment(NegotiationContext.ATTACHMENT_KEY, null);
            if (responseChallenge != null) {
                header = NEGOTIATE_PREFIX + FlexBase64.encodeString(responseChallenge, false);
            }
        }

        exchange.getResponseHeaders().add(WWW_AUTHENTICATE, header);

        return new ChallengeResult(true, UNAUTHORIZED);
    }


    public AuthenticationMechanismOutcome runGSSAPI(final HttpServerExchange exchange,
                                                    final ByteBuffer challenge, final SecurityContext securityContext) {
        try {
            Subject server = subjectFactory.getSubjectForHost(getHostName(exchange));
            // The AcceptSecurityContext takes over responsibility for setting the result.
            return Subject.doAs(server, new AcceptSecurityContext(exchange, challenge, securityContext));
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        } catch (PrivilegedActionException e) {
            e.printStackTrace();
            return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
        }
    }

    private String getHostName(final HttpServerExchange exchange) {
        String hostName = exchange.getRequestHeaders().getFirst(HOST);
        if (hostName != null) {
            if (hostName.contains(":")) {
                hostName = hostName.substring(0, hostName.indexOf(":"));
            }
            return hostName;
        }

        return null;
    }


    private class AcceptSecurityContext implements PrivilegedExceptionAction<AuthenticationMechanismOutcome> {

        private final HttpServerExchange exchange;
        private final ByteBuffer challenge;
        private final SecurityContext securityContext;

        private AcceptSecurityContext(final HttpServerExchange exchange,
                                      final ByteBuffer challenge, final SecurityContext securityContext) {
            this.exchange = exchange;
            this.challenge = challenge;
            this.securityContext = securityContext;
        }

        public AuthenticationMechanismOutcome run() throws GSSException {
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

                if (respToken != null) {
                    // There will be no further challenge but we do have a token so set it here.
                    exchange.getResponseHeaders().add(WWW_AUTHENTICATE,
                            NEGOTIATE_PREFIX + FlexBase64.encodeString(respToken, false));
                }
                IdentityManager identityManager = securityContext.getIdentityManager();
                final Account account = identityManager.verify(new GSSContextCredential(negContext.getGssContext()));
                if (account != null) {
                    securityContext.authenticationComplete(account, name);
                    return AuthenticationMechanismOutcome.AUTHENTICATED;
                } else {
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }
            } else {
                // This isn't a failure but as the context is not established another round trip with the client is needed.
                return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
            }
        }
    }

    private static class NegotiationContext {

        static final AttachmentKey<NegotiationContext> ATTACHMENT_KEY = AttachmentKey.create(NegotiationContext.class);

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
