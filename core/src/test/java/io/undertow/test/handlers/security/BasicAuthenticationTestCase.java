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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.security.AuthenticationConstraintHandler;
import io.undertow.server.handlers.security.AuthenticationMechanism;
import io.undertow.server.handlers.security.AuthenticationMechanismsHandler;
import io.undertow.server.handlers.security.AuthenticationCallHandler;
import io.undertow.server.handlers.security.BasicAuthenticationMechanism;
import io.undertow.server.handlers.security.SecurityInitialHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;

/**
 * A test case to test when the only authentication mechanism
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class BasicAuthenticationTestCase {

    private static final CallbackHandler callbackHandler;

    static {
        final Map<String, char[]> users = new HashMap<String, char[]>(2);
        users.put("userOne", "passwordOne".toCharArray());
        users.put("userTwo", "passwordTwo".toCharArray());
        callbackHandler = new CallbackHandler() {

            @Override
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                NameCallback ncb = null;
                PasswordCallback pcb = null;
                for (Callback current : callbacks) {
                    if (current instanceof NameCallback) {
                        ncb = (NameCallback) current;
                    } else if (current instanceof PasswordCallback) {
                        pcb = (PasswordCallback) current;
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
    }

    /**
     * Basic test to prove detection of the ResponseHandler response.
     */
    @Test
    public void testNoMechanisms() throws Exception {
        DefaultServer.setRootHandler(new ResponseHandler());

        DefaultHttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress());
        HttpResponse result = client.execute(get);
        assertEquals(200, result.getStatusLine().getStatusCode());

        Header[] values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
    }

    @Test
    public void testBasicSuccess() throws Exception {
        HttpHandler responseHandler = new ResponseHandler();
        HttpHandler callHandler = new AuthenticationCallHandler(responseHandler);
        HttpHandler constraintHandler = new AuthenticationConstraintHandler(callHandler);
        BasicAuthenticationMechanism basicAuthHandler = new BasicAuthenticationMechanism("Test Realm", callbackHandler);
        HttpHandler methodsAddHandler = new AuthenticationMechanismsHandler(constraintHandler, Collections.<AuthenticationMechanism>singletonList(basicAuthHandler));
        HttpHandler initialHandler = new SecurityInitialHandler(methodsAddHandler);
        DefaultServer.setRootHandler(initialHandler);

        DefaultHttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress());
        HttpResponse result = client.execute(get);
        assertEquals(401, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());

        client = new DefaultHttpClient();
        get = new HttpGet(DefaultServer.getDefaultServerAddress());
        get.addHeader(AUTHORIZATION.toString(), BASIC + " " + Base64.encodeBase64String("userOne:passwordOne".getBytes()));
        result = client.execute(get);
        assertEquals(200, result.getStatusLine().getStatusCode());

        values = result.getHeaders("ProcessedBy");
        assertEquals(1, values.length);
        assertEquals("ResponseHandler", values[0].getValue());
    }

    /**
     * A simple end of chain handler to set a header and cause the call to return.
     *
     * Reaching this handler is a sign the mechanism handlers have allowed the request through.
     */
    private static class ResponseHandler implements HttpHandler {

        static final HttpString PROCESSED_BY = new HttpString("ProcessedBy");

        @Override
        public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
            HeaderMap responseHeader = exchange.getResponseHeaders();
            responseHeader.add(PROCESSED_BY, "ResponseHandler");

            completionHandler.handleComplete();
        }

    }

}
