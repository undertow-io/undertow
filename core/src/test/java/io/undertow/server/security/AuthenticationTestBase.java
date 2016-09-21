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
package io.undertow.server.security;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.NotificationReceiverHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.GSSContextCredential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.idm.X509CertificateCredential;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HeaderMap;
import io.undertow.util.HexConverter;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.ietf.jgss.GSSException;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Base class for the authentication tests.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class AuthenticationTestBase {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    protected static final IdentityManager identityManager;
    protected static final AuditReceiver auditReceiver = new AuditReceiver();

    static {
        final Set<String> certUsers = new HashSet<>();
        certUsers.add("CN=Test Client,OU=OU,O=Org,L=City,ST=State,C=GB");

        final Set<String> gssApiUsers = new HashSet<>();
        gssApiUsers.add("jduke@UNDERTOW.IO");

        final Map<String, char[]> passwordUsers = new HashMap<>(2);
        passwordUsers.put("userOne", "passwordOne".toCharArray());
        passwordUsers.put("userTwo", "passwordTwo".toCharArray());
        passwordUsers.put("encodingUser", "password-ü".toCharArray());

        identityManager = new IdentityManager() {

            @Override
            public Account verify(Account account) {
                // An existing account so for testing assume still valid.
                return account;
            }

            @Override
            public Account verify(String id, Credential credential) {
                Account account = getAccount(id);
                if (account != null && verifyCredential(account, credential)) {
                    return account;
                }

                return null;
            }

            @Override
            public Account verify(Credential credential) {
                if (credential instanceof X509CertificateCredential) {
                    final Principal p = ((X509CertificateCredential) credential).getCertificate().getSubjectX500Principal();
                    if (certUsers.contains(p.getName())) {
                        return new Account() {

                            @Override
                            public Principal getPrincipal() {
                                return p;
                            }

                            @Override
                            public Set<String> getRoles() {
                                return Collections.emptySet();
                            }

                        };
                    }

                } else if (credential instanceof GSSContextCredential) {
                    try {
                        final GSSContextCredential gssCredential = (GSSContextCredential) credential;
                        final String name = gssCredential.getGssContext().getSrcName().toString();
                        if (gssApiUsers.contains(name)) {
                            return new Account() {

                                private final Principal principal = new Principal() {

                                    @Override
                                    public String getName() {
                                        return name;
                                    }
                                };

                                @Override
                                public Principal getPrincipal() {
                                    return principal;
                                }

                                @Override
                                public Set<String> getRoles() {
                                    return Collections.emptySet();
                                }
                            };

                        }

                    } catch (GSSException e) {
                        throw new RuntimeException(e);
                    }
                }

                return null;
            }

            private boolean verifyCredential(Account account, Credential credential) {
                if (credential instanceof PasswordCredential) {
                    char[] password = ((PasswordCredential) credential).getPassword();
                    char[] expectedPassword = passwordUsers.get(account.getPrincipal().getName());

                    return Arrays.equals(password, expectedPassword);
                } else if (credential instanceof DigestCredential) {
                    DigestCredential digCred = (DigestCredential) credential;
                    MessageDigest digest = null;
                    try {
                        digest = digCred.getAlgorithm().getMessageDigest();

                        digest.update(account.getPrincipal().getName().getBytes(UTF_8));
                        digest.update((byte) ':');
                        digest.update(digCred.getRealm().getBytes(UTF_8));
                        digest.update((byte) ':');
                        char[] expectedPassword = passwordUsers.get(account.getPrincipal().getName());
                        digest.update(new String(expectedPassword).getBytes(UTF_8));

                        return digCred.verifyHA1(HexConverter.convertToHexBytes(digest.digest()));
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException("Unsupported Algorithm", e);
                    } finally {
                        digest.reset();
                    }
                } else {
                    throw new IllegalArgumentException("Invalid Credential Type " + credential.getClass().getName());
                }
            }

            private Account getAccount(final String id) {
                if (passwordUsers.containsKey(id)) {
                    return new Account() {

                        private final Principal principal = new Principal() {

                            @Override
                            public String getName() {
                                return id;
                            }
                        };

                        @Override
                        public Principal getPrincipal() {
                            return principal;
                        }

                        @Override
                        public Set<String> getRoles() {
                            return Collections.emptySet();
                        }

                    };
                }
                return null;
            }

        };
    }

    protected void setAuthenticationChain() {
        HttpHandler current = new ResponseHandler();
        current = new AuthenticationCallHandler(current);
        current = new AuthenticationConstraintHandler(current);

        current = new AuthenticationMechanismsHandler(current, getTestMechanisms());
        auditReceiver.takeNotifications(); // Ensure empty on initialisation.
        current = new NotificationReceiverHandler(current, Collections.<NotificationReceiver> singleton(auditReceiver));

        current = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, current);

        DefaultServer.setRootHandler(current);
    }

    protected abstract List<AuthenticationMechanism> getTestMechanisms();

    /**
     * Basic test to prove detection of the ResponseHandler response.
     */
    @Test
    public void testNoMechanisms() throws Exception {
        DefaultServer.setRootHandler(new ResponseHandler());

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
        HttpResponse result = client.execute(get);
        assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

        Header[] values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
    }

    protected static void assertSingleNotificationType(final SecurityNotification.EventType eventType) {
        List<SecurityNotification> notifications = auditReceiver.takeNotifications();
        assertEquals("A single notification is expected.", 1, notifications.size());
        assertEquals("Expected EventType not matched.", eventType, notifications.get(0).getEventType());
    }

    protected static void assertNotifiactions(final SecurityNotification.EventType ... eventTypes) {
        List<SecurityNotification> notifications = auditReceiver.takeNotifications();
        assertEquals("A single notification is expected.", eventTypes.length, notifications.size());
        final List<SecurityNotification.EventType> types = new ArrayList<>();
        for(SecurityNotification i : notifications) {
            types.add(i.getEventType());
        }
        assertEquals("Expected EventType not matched.", Arrays.asList(eventTypes), types);
    }

    protected static String getAuthenticatedUser(final HttpServerExchange exchange) {
        SecurityContext context = exchange.getSecurityContext();
        if (context != null) {
            Account account = context.getAuthenticatedAccount();
            if (account != null) {
                // An account must always return a Principal otherwise it is not an Account.
                return account.getPrincipal().getName();
            }
        }

        return null;
    }

    protected static String getAuthHeader(final HttpString prefix, final Header[] values) {
        for (Header current : values) {
            String currentValue = current.getValue();
            if (currentValue.startsWith(prefix.toString())) {
                return currentValue;
            }
        }

        fail("Expected header not found.");
        return null; // Unreachable
    }

    /**
     * A simple end of chain handler to set a header and cause the call to return.
     * <p/>
     * Reaching this handler is a sign the mechanism handlers have allowed the request through.
     */
    protected static class ResponseHandler implements HttpHandler {

        static final HttpString PROCESSED_BY = new HttpString("ProcessedBy");
        static final HttpString AUTHENTICATED_USER = new HttpString("AuthenticatedUser");

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            HeaderMap responseHeader = exchange.getResponseHeaders();
            responseHeader.add(PROCESSED_BY, "ResponseHandler");
            String user = getAuthenticatedUser(exchange);
            if (user != null) {
                responseHeader.add(AUTHENTICATED_USER, user);
            }
            if(exchange.getQueryParameters().get("logout") != null) {
                exchange.getSecurityContext().logout();
            }

            exchange.endExchange();
        }
    }

    protected static class AuditReceiver implements NotificationReceiver {

        private final List<SecurityNotification> receivedNotifications = new ArrayList<>();

        @Override
        public void handleNotification(SecurityNotification notification) {
            receivedNotifications.add(notification);
        }

        public List<SecurityNotification> takeNotifications() {
            try {
                return new ArrayList<>(receivedNotifications);
            } finally {
                receivedNotifications.clear();
            }
        }

    }

}
