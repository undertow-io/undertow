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

import io.undertow.predicate.Predicates;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.impl.CachedAuthenticatedSessionMechanism;
import io.undertow.security.impl.FormAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FormAuthTestCase extends AuthenticationTestBase {

    public static final String HELLO_WORLD = "Hello World";

    @Override
    protected void setRootHandler(HttpHandler current) {
        final PredicateHandler handler = new PredicateHandler(Predicates.path("/login"), new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send("Login Page");
            }
        }, current);
        super.setRootHandler(new SessionAttachmentHandler(handler, new InMemorySessionManager("test"), new SessionCookieConfig()));
    }

    protected boolean cachingRequired() {
        return true;
    }

    @Test
    public void testFormAuth() throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
                Header[] locationHeaders = response.getHeaders("Location");
                if (locationHeaders != null && locationHeaders.length > 0) {
                    for (Header locationHeader : locationHeaders) {
                        assertFalse("Location header incorrectly computed resulting in wrong request URI upon redirect, " +
                                        "failed probably due UNDERTOW-884",
                                locationHeader.getValue().startsWith(DefaultServer.getDefaultServerURL() + DefaultServer.getDefaultServerURL()));
                    }
                }
                if (response.getStatusLine().getStatusCode() == StatusCodes.FOUND) {
                    return true;
                }
                return super.isRedirected(request, response, context);
            }
        });
        try {
            final String uri = DefaultServer.getDefaultServerURL() + "/secured/test";
            HttpGet get = new HttpGet(uri);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Login Page", response);

            BasicNameValuePair[] pairs = new BasicNameValuePair[]{new BasicNameValuePair("j_username", "userOne"), new BasicNameValuePair("j_password", "passwordOne")};
            final List<NameValuePair> data = new ArrayList<>();
            data.addAll(Arrays.asList(pairs));
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/j_security_check;jsessionid=dsjahfklsahdfjklsa");

            post.setEntity(new UrlEncodedFormEntity(data));

            result = client.execute(post);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            Header[] values = result.getHeaders("ProcessedBy");
            assertEquals(1, values.length);
            assertEquals("ResponseHandler", values[0].getValue());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        List<AuthenticationMechanism> ret = new ArrayList<>();
        ret.add(new CachedAuthenticatedSessionMechanism());
        ret.add(new FormAuthenticationMechanism("test", "/login", "/error"));
        return ret;
    }
}
