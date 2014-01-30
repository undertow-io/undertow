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

package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.session.CookieSessionConfig;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static io.undertow.Handlers.jvmRoute;
import static io.undertow.Handlers.path;

/**
 * Tests the load balancing proxy
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class LoadBalancingProxyTestCase {

    private static final String COUNT = "count";

    private static Undertow server1;
    private static Undertow server2;

    @BeforeClass
    public static void setup() throws URISyntaxException {

        final CookieSessionConfig sessionConfig = new CookieSessionConfig();
        int port = DefaultServer.getHostPort("default");
        server1 = Undertow.builder()
                .addListener(port + 1, DefaultServer.getHostAddress("default"))
                .setHandler(jvmRoute("JSESSIONID", "s1", path()
                        .addPrefixPath("/session", new SessionAttachmentHandler(new SessionTestHandler(sessionConfig), new InMemorySessionManager(""), sessionConfig))
                        .addPrefixPath("/name", new StringSendHandler("server1"))))
                .build();

        server2 = Undertow.builder()
                .addListener(port + 2, DefaultServer.getHostAddress("default"))
                .setHandler(jvmRoute("JSESSIONID", "s2", path()
                        .addPrefixPath("/session", new SessionAttachmentHandler(new SessionTestHandler(sessionConfig), new InMemorySessionManager(""), sessionConfig))
                        .addPrefixPath("/name", new StringSendHandler("server2"))))
                .build();
        server1.start();
        server2.start();

        DefaultServer.setRootHandler(new ProxyHandler(new LoadBalancingProxyClient()
                .setConnectionsPerThread(1)
                .addHost(new URI("http", null, DefaultServer.getHostAddress("default"), port + 1, null, null, null), "s1")
                .addHost(new URI("http", null, DefaultServer.getHostAddress("default"), port + 2, null, null, null), "s2")
                , 10000, ResponseCodeHandler.HANDLE_404));
    }

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
                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
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
                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
                resultString.append(HttpClientUtils.readResponse(result));
                resultString.append(' ');
            } finally {
                client.getConnectionManager().shutdown();
            }
            server1.stop();
            server1.start();
            server2.stop();
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
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/session");
                get.addHeader("Connection", "close");
                HttpResponse result = client.execute(get);
                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
                int count = Integer.parseInt(HttpClientUtils.readResponse(result));
                Assert.assertEquals(expected++, count);
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    private static final class SessionTestHandler implements HttpHandler {

        private final SessionConfig sessionConfig;

        private SessionTestHandler(SessionConfig sessionConfig) {
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


    private static final class StringSendHandler implements HttpHandler {

        private final String serverName;

        private StringSendHandler(String serverName) {
            this.serverName = serverName;
        }

        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send(serverName);
        }
    }
}
