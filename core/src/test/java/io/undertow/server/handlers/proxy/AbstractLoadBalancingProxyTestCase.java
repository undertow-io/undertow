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
import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;
import org.xnio.XnioWorker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

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
    public static final String RESPONSE_BODY = "This is a response body";

    protected static Undertow server1;
    protected static Undertow server2;

    private static volatile boolean firstFail = true;

    @BeforeClass
    public static void setupFailTest() {
        firstFail = true;
    }

    protected static final int IDLE_TIMEOUT = 1000;

    @AfterClass
    public static void teardown() {
        XnioWorker worker1 = null, worker2 = null;
        int countDown = 0;
        try {
            if (server1 != null) {
                final XnioWorker worker = server1.getWorker();
                server1.stop();
                // if stop did not shutdown the worker, we need to run the latch to prevent a Address already in use (UNDERTOW-1960)
                if (worker != null && !worker.isShutdown()) {
                    countDown++;
                    worker1 = worker;
                }
            }
        } finally {
            try {
                if (server2 != null) {
                    final XnioWorker worker = server2.getWorker();
                    server2.stop();
                    // if stop did not shutdown the worker, we need to run the latch to prevent a Address already in use (UNDERTOW-1960)
                    if (worker != null && !worker.isShutdown() && worker != worker1) {
                        worker2 = worker;
                        countDown++;
                    }
                }
            } finally {
                if (countDown != 0) {
                    // TODO this is needed solely for ssl servers; replace this by the mechanism described in UNDERTOW-1648 once it is implemented
                    final CountDownLatch latch = new CountDownLatch(countDown);
                    if (worker1 != null) worker1.getIoThread().execute(latch::countDown);
                    if (worker2 != null) worker2.getIoThread().execute(latch::countDown);
                    try {
                        latch.await();
                        //double protection, we need to guarantee that the servers have stopped, and some environments seem to need a small delay to re-bind the socket
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
            }
        }
    }

    @Test
    public void testLoadShared() throws IOException {
        final StringBuilder resultString = new StringBuilder();

        for (int i = 0; i < 6; ++i) {
            // decompression is automatic
            try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
                client.execute(get, result -> {
                    Assert.assertEquals(StatusCodes.OK, result.getCode());
                    resultString.append(HttpClientUtils.readResponse(result));
                    resultString.append(' ');
                    return null;
                });
            }
        }
        Assert.assertTrue(resultString.toString().contains("server1"));
        Assert.assertTrue(resultString.toString().contains("server2"));
    }

    @Test
    public void testAbruptClosed() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/close");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.SERVICE_UNAVAILABLE, result.getCode());
                return null;
            });
        }
    }

    @Test
    public void testUrlEncoding() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/url/foo=bar");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("/url/foo=bar", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }

    @Test
    @HttpOneOnly
    public void testOldBackend() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            for (int i = 0; i < 10; ++i) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/old");
                client.execute(get, result -> {
                    Assert.assertEquals(StatusCodes.OK, result.getCode());
                    Assert.assertEquals(RESPONSE_BODY, HttpClientUtils.readResponse(result));
                    return null;
                });
            }
        }
    }

    @Test
    public void testMaxRetries() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/fail");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("/fail:false", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }


    @Test
    public void testLoadSharedWithServerShutdown() throws Exception {
        final StringBuilder resultString = new StringBuilder();

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            for (int i = 0; i < 6; ++i) {
                int index = i;
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
                client.execute(get, result -> {
                    Assert.assertEquals("Test failed with i=" + index, StatusCodes.OK, result.getCode());
                    resultString.append(HttpClientUtils.readResponse(result));
                    resultString.append(' ');
                    return null;
                });
                server1.stop();
                Thread.sleep(1200);
                get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
                client.execute(get, result -> {
                    Assert.assertEquals("Test failed with i=" + index, StatusCodes.OK, result.getCode());
                    resultString.append(HttpClientUtils.readResponse(result));
                    resultString.append(' ');
                    return null;
                });
                server1.start();
                server2.stop();
                Thread.sleep(600);
                get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
                client.execute(get, result -> {
                    Assert.assertEquals("Test failed with i=" + index, StatusCodes.OK, result.getCode());
                    resultString.append(HttpClientUtils.readResponse(result));
                    resultString.append(' ');
                    return null;
                });
                server2.start();
            }
        }
        Assert.assertTrue(resultString.toString().contains("server1"));
        Assert.assertTrue(resultString.toString().contains("server2"));
    }

    @Test
    public void testStickySessions() throws IOException {
        int expected = 0;
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            for (int i = 0; i < 6; ++i) {
                int index = i;
                try {
                    HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/session");
                    get.addHeader("Connection", "close");
                    int count = client.execute(get, result -> {
                        Assert.assertEquals("Test failed with i=" + index, StatusCodes.OK, result.getCode());
                        return Integer.parseInt(HttpClientUtils.readResponse(result));
                    });
                    Assert.assertEquals(expected++, count);
                } catch (AssertionError e) {
                    throw e;
                } catch (Exception e) {
                    throw new AssertionError("Test failed with i=" + i, e);
                }
            }
        }
    }

    //see https://issues.jboss.org/browse/UNDERTOW-276
    @Test
    public void testDuplicateHeaders() throws IOException {
        int expected = 0;
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            for (int i = 0; i < 6; ++i) {
                int index = i;
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
                    int count = client.execute(get, result -> {
                        Assert.assertEquals("Test failed with i=" + index, StatusCodes.OK, result.getCode());
                        return Integer.parseInt(HttpClientUtils.readResponse(result));
                    });
                    Assert.assertEquals("Test failed with i=" + index, expected++, count);
                } catch (AssertionError e) {
                    throw e;
                } catch (Exception e) {
                    throw new AssertionError("Test failed with i=" + i, e);
                }
            }
        }
    }

    @Test
    public void testConnectionTimeout() throws Exception {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/timeout");
            get.addHeader("Connection", "close");
            client.execute(get, result -> {
                boolean res = Boolean.parseBoolean(HttpClientUtils.readResponse(result));
                Assert.assertFalse(res);
                return null;
            });
            for (int i = 0; i < 20; ++i) { //try and make sure that all IO threads have been used, this is not reliable however
                client.execute(get, HttpClientUtils::readResponse);
            }
            client.execute(get, result -> {
                boolean res = Boolean.parseBoolean(HttpClientUtils.readResponse(result));
                //Assert.assertTrue(res); //this will fail sometime, unless we make a huge number of requests to make sure all IO threads are utilised
                return null;
            });
            Thread.sleep(IDLE_TIMEOUT + 1000);
            UndertowLogger.ROOT_LOGGER.info("Sending timed out request");
            client.execute(get, result -> {
                boolean res = Boolean.parseBoolean(HttpClientUtils.readResponse(result));
                Assert.assertFalse(res);
                return null;
            });
        }
    }

    private static final AttachmentKey<Boolean> EXISTING = AttachmentKey.create(Boolean.class);

    protected static HttpHandler getRootHandler(String s1, String server1) {
        final SessionCookieConfig sessionConfig = new SessionCookieConfig();
        return jvmRoute("JSESSIONID", s1, path()
                .addPrefixPath("/session", new SessionAttachmentHandler(new SessionTestHandler(sessionConfig), new InMemorySessionManager(""), sessionConfig))
                .addPrefixPath("/name", new StringSendHandler(server1))
                .addPrefixPath("/url", exchange -> exchange.getResponseSender().send(exchange.getRequestURI()))
                .addPrefixPath("/path", exchange -> exchange.getResponseSender().send(exchange.getRequestURI()))
                .addPrefixPath("/fail", exchange -> {
                    if (firstFail) {
                        firstFail = false;
                        IoUtils.safeClose(exchange.getConnection());
                        return;
                    }
                    exchange.getResponseSender().send(exchange.getRequestURI() + ":" + firstFail);
                })
                .addPrefixPath("/timeout", exchange -> {
                    if (exchange.getConnection().getAttachment(EXISTING) == null) {
                        exchange.getConnection().putAttachment(EXISTING, true);
                        exchange.getResponseSender().send("false");
                    } else {
                        exchange.getResponseSender().send("true");
                    }
                })
                .addPrefixPath("/close", exchange -> IoUtils.safeClose(exchange.getConnection()))
                .addPrefixPath("/old", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        if (exchange.isInIoThread()) {
                            exchange.dispatch(this);
                            return;
                        }
                        exchange.startBlocking();
                        exchange.setProtocol(Protocols.HTTP_1_0);
                        exchange.getOutputStream().write(RESPONSE_BODY.getBytes(StandardCharsets.US_ASCII));
                        exchange.getOutputStream().flush();
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
