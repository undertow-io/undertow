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
import io.undertow.server.session.Session;
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
    public void testLoadSharedWithServerShutdown() throws IOException {
        final StringBuilder resultString = new StringBuilder();

        for (int i = 0; i < 6; ++i) {
            TestHttpClient client = new TestHttpClient();
            try {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                resultString.append(HttpClientUtils.readResponse(result));
                resultString.append(' ');
            } catch (Throwable t) {
                throw new RuntimeException("Failed with i=" + i, t);
            } finally {
                client.getConnectionManager().shutdown();
            }
            server1.stop();
            server1.start();
            server2.stop();
            server2.start();
            try {
                //so this is not great, but we need to make sure the connection has actually closed
                //otherwise the TCP close may not have been processed yet, resulting in the proxy
                //picking a connection that is about to be closed
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
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
                    Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                    int count = Integer.parseInt(HttpClientUtils.readResponse(result));
                    Assert.assertEquals(expected++, count);
                } catch (Exception e) {
                    throw new RuntimeException("Test failed with i=" + i, e);
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
                    Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                    int count = Integer.parseInt(HttpClientUtils.readResponse(result));
                    Assert.assertEquals(expected++, count);
                } catch (Exception e) {
                    throw new RuntimeException("Test failed with i=" + i, e);
                }
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
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
