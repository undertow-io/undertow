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

package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static io.undertow.Handlers.jvmRoute;
import static io.undertow.Handlers.path;

/**
 * Tests the load balancing proxy
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public abstract class AbstractLoadBalancingProxyTestCase {

    private static final String COUNT = "count";

    protected static Undertow server1;
    protected static Undertow server2;

    @AfterClass
    public static void teardown() {
        server1.stop();
        server2.stop();
    }

    @Test
    public void testLoadShared() throws IOException {
        final StringBuilder resultString = new StringBuilder();

        for (int i = 0; i < 6; ++i) {
            TestHttpClient client = new TestHttpClient();
            try {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                resultString.append(HttpClientUtils.readResponse(result));
                resultString.append(' ');
            } finally {
                client.getConnectionManager().shutdown();
            }
        }
        Assert.assertTrue(resultString.toString().contains("server1"));
        Assert.assertTrue(resultString.toString().contains("server2"));
    }


    @Test
    public void testLoadSharedWithServerShutdown() throws Exception {
        final StringBuilder resultString = new StringBuilder();

        for (int i = 0; i < 6; ++i) {
            TestHttpClient client = new TestHttpClient();
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
            HttpResponse result = client.execute(get);
            Assert.assertEquals("Test failed with i=" + i, StatusCodes.OK, result.getStatusLine().getStatusCode());
            resultString.append(HttpClientUtils.readResponse(result));
            resultString.append(' ');
            server1.stop();
            Thread.sleep(600);
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
            result = client.execute(get);
            Assert.assertEquals("Test failed with i=" + i, StatusCodes.OK, result.getStatusLine().getStatusCode());
            resultString.append(HttpClientUtils.readResponse(result));
            resultString.append(' ');
            server1.start();
            server2.stop();
            Thread.sleep(600);
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
            result = client.execute(get);
            Assert.assertEquals("Test failed with i=" + i, StatusCodes.OK, result.getStatusLine().getStatusCode());
            resultString.append(HttpClientUtils.readResponse(result));
            resultString.append(' ');
            server2.start();
        }
        Assert.assertTrue(resultString.toString().contains("server1"));
        Assert.assertTrue(resultString.toString().contains("server2"));
    }

    @Test
    public void testStickySessions() throws IOException {
        int expected = 0;
        TestHttpClient client = new TestHttpClient();
        try {
            for (int i = 0; i < 6; ++i) {
                try {
                    HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/session");
                    get.addHeader("Connection", "close");
                    HttpResponse result = client.execute(get);
                    Assert.assertEquals("Test failed with i=" + i, StatusCodes.OK, result.getStatusLine().getStatusCode());
                    int count = Integer.parseInt(HttpClientUtils.readResponse(result));
                    Assert.assertEquals(expected++, count);
                } catch (AssertionError e) {
                    throw e;
                } catch (Exception e) {
                    throw new AssertionError("Test failed with i=" + i, e);
                }
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    //see https://issues.jboss.org/browse/UNDERTOW-276
    @Test
    public void testDuplicateHeaders() throws IOException {
        int expected = 0;
        TestHttpClient client = new TestHttpClient();
        try {
            for (int i = 0; i < 6; ++i) {
                try {
                    HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/session");
                    get.addHeader("a", "b");
                    get.addHeader("a", "b");
                    get.addHeader("a", "b");
                    get.addHeader("a", "b");
                    get.addHeader("a", "b");
                    get.addHeader("a", "b");
                    get.addHeader("a", "b");
                    get.addHeader("a", "b");
                    get.addHeader("Connection", "close");
                    HttpResponse result = client.execute(get);
                    Assert.assertEquals("Test failed with i=" + i, StatusCodes.OK, result.getStatusLine().getStatusCode());
                    int count = Integer.parseInt(HttpClientUtils.readResponse(result));
                    Assert.assertEquals("Test failed with i=" + i, expected++, count);
                } catch (AssertionError e) {
                    throw e;
                } catch (Exception e) {
                    throw new AssertionError("Test failed with i=" + i, e);
                }
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    protected static HttpHandler getRootHandler(String s1, String server1) {
        final SessionCookieConfig sessionConfig = new SessionCookieConfig();
        return jvmRoute("JSESSIONID", s1, path()
                .addPrefixPath("/session", new SessionAttachmentHandler(new SessionTestHandler(sessionConfig), new InMemorySessionManager(""), sessionConfig))
                .addPrefixPath("/name", new StringSendHandler(server1))
                .addPrefixPath("/path", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send(exchange.getRequestURI());
                    }
                }));
    }

    protected static final class SessionTestHandler implements HttpHandler {

        private final SessionCookieConfig sessionConfig;

        protected SessionTestHandler(SessionCookieConfig sessionConfig) {
            this.sessionConfig = sessionConfig;
        }

        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
            Session session = manager.getSession(exchange, sessionConfig);
            if (session == null) {
                session = manager.createSession(exchange, sessionConfig);
                session.setAttribute(COUNT, 0);
            }
            Integer count = (Integer) session.getAttribute(COUNT);
            session.setAttribute(COUNT, count + 1);
            exchange.getResponseSender().send("" + count);
        }
    }


    protected static final class StringSendHandler implements HttpHandler {

        private final String serverName;

        protected StringSendHandler(String serverName) {
            this.serverName = serverName;
        }

        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send(serverName);
        }
    }
}
