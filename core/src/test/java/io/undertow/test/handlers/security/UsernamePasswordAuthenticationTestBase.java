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
package io.undertow.test.handlers.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import io.undertow.idm.Account;
import io.undertow.idm.Credential;
import io.undertow.idm.IdentityManager;
import io.undertow.idm.PasswordCredential;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.security.AuthenticationCallHandler;
import io.undertow.server.handlers.security.AuthenticationConstraintHandler;
import io.undertow.server.handlers.security.AuthenticationMechanism;
import io.undertow.server.handlers.security.AuthenticationMechanismsHandler;
import io.undertow.server.handlers.security.RoleCallback;
import io.undertow.server.handlers.security.SecurityInitialHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.util.TestHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Base class for the username / password based tests.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class UsernamePasswordAuthenticationTestBase {

    protected static final CallbackHandler callbackHandler;
    protected static final IdentityManager identityManager;

    static {
        final Map<String, char[]> users = new HashMap<String, char[]>(2);
        users.put("userOne", "passwordOne".toCharArray());
        users.put("userTwo", "passwordTwo".toCharArray());
        callbackHandler = new CallbackHandler() {

            @Override
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                NameCallback ncb = null;
                PasswordCallback pcb = null;
                RoleCallback rcb = null;
                for (Callback current : callbacks) {
                    if (current instanceof NameCallback) {
                        ncb = (NameCallback) current;
                    } else if (current instanceof PasswordCallback) {
                        pcb = (PasswordCallback) current;
                    } else if (current instanceof RoleCallback) {
                        rcb = (RoleCallback) current;
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }

                char[] password = users.get(ncb.getDefaultName());
                if (password == null) {
                    throw new IOException("User not found");
                }
                pcb.setPassword(password);
            }
        };
        identityManager = new IdentityManager() {

            @Override
            public boolean verifyCredential(Account account, Credential credential) {
                if (credential instanceof PasswordCredential) {
                    char[] password = ((PasswordCredential) credential).getPassword();
                    char[] expectedPassword = users.get(account.getName());

                    return Arrays.equals(password, expectedPassword);
                }
                return false;
            }

            @Override
            public Account verifyCredential(Credential credential) {
                return null;
            }

            @Override
            public Account lookupAccount(final String id) {
                if (users.containsKey(id)) {
                    return new Account() {

                        @Override
                        public String getName() {
                            return id;
                        }

                    };
                }
                return null;
            }

            @Override
            public Set<String> getRoles(Account account) {
                return Collections.emptySet();
            }
        };
    }

    protected void setAuthenticationChain() {
        HttpHandler responseHandler = new ResponseHandler();
        HttpHandler callHandler = new AuthenticationCallHandler(responseHandler);
        HttpHandler constraintHandler = new AuthenticationConstraintHandler(callHandler);

        AuthenticationMechanism authMech = getTestMechanism();

        HttpHandler methodsAddHandler = new AuthenticationMechanismsHandler(constraintHandler,
                Collections.<AuthenticationMechanism>singletonList(authMech));
        HttpHandler initialHandler = new SecurityInitialHandler(identityManager, methodsAddHandler);
        DefaultServer.setRootHandler(initialHandler);
    }

    protected abstract AuthenticationMechanism getTestMechanism();

    /**
     * Basic test to prove detection of the ResponseHandler response.
     */
    @Test
    public void testNoMechanisms() throws Exception {
        DefaultServer.setRootHandler(new ResponseHandler());

        TestHttpClient client = new TestHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress());
        HttpResponse result = client.execute(get);
        assertEquals(200, result.getStatusLine().getStatusCode());

        Header[] values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
    }

    /**
     * A simple end of chain handler to set a header and cause the call to return.
     * <p/>
     * Reaching this handler is a sign the mechanism handlers have allowed the request through.
     */
    protected static class ResponseHandler implements HttpHandler {

        static final HttpString PROCESSED_BY = new HttpString("ProcessedBy");

        @Override
        public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
            HeaderMap responseHeader = exchange.getResponseHeaders();
            responseHeader.add(PROCESSED_BY, "ResponseHandler");

            completionHandler.handleComplete();
        }

    }

}
