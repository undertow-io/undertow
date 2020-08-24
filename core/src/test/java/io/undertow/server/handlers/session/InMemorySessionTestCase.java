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

package io.undertow.server.handlers.session;

import java.io.IOException;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import io.undertow.testutils.TestHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * basic test of in memory session functionality
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class InMemorySessionTestCase {

    public static final String COUNT = "count";

    @Test
    public void inMemorySessionTest() throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new BasicCookieStore());
        try {
            final SessionCookieConfig sessionConfig = new SessionCookieConfig();
            final SessionAttachmentHandler handler = new SessionAttachmentHandler(new InMemorySessionManager(""), sessionConfig);
            handler.setNext(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
                    final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
                    Session session = manager.getSession(exchange, sessionConfig);
                    if (session == null) {
                        session = manager.createSession(exchange, sessionConfig);
                        session.setAttribute(COUNT, 0);
                    }
                    Integer count = (Integer) session.getAttribute(COUNT);
                    exchange.getResponseHeaders().add(new HttpString(COUNT), count.toString());
                    session.setAttribute(COUNT, ++count);
                }
            });
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Header[] header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("1", header[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("2", header[0].getValue());


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void inMemoryMaxSessionsTest() throws IOException {

        TestHttpClient client1 = new TestHttpClient();
        client1.setCookieStore(new BasicCookieStore());
        TestHttpClient client2 = new TestHttpClient();
        client2.setCookieStore(new BasicCookieStore());

        try {
            final SessionCookieConfig sessionConfig = new SessionCookieConfig();
            final SessionAttachmentHandler handler = new SessionAttachmentHandler(new InMemorySessionManager("", 1, true), sessionConfig);
            handler.setNext(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
                    final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
                    Session session = manager.getSession(exchange, sessionConfig);
                    if (session == null) {
                        session = manager.createSession(exchange, sessionConfig);
                        session.setAttribute(COUNT, 0);
                    }
                    Integer count = (Integer) session.getAttribute(COUNT);
                    exchange.getResponseHeaders().add(new HttpString(COUNT), count.toString());
                    session.setAttribute(COUNT, ++count);
                }
            });
            DefaultServer.setRootHandler(handler);


            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            HttpResponse result = client1.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Header[] header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            result = client1.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("1", header[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            result = client2.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            result = client1.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());


        } finally {
            client1.getConnectionManager().shutdown();
            client2.getConnectionManager().shutdown();
        }
    }

    @Test // https://issues.redhat.com/browse/UNDERTOW-1419
    public void inMemorySessionTimeoutExpirationTest() throws IOException, InterruptedException {

        final int maxInactiveIntervalInSeconds = 1;
        final int accessorThreadSleepInMilliseconds = 200;

        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new BasicCookieStore());
        try {
            final SessionCookieConfig sessionConfig = new SessionCookieConfig();
            final SessionAttachmentHandler handler = new SessionAttachmentHandler(new InMemorySessionManager(""), sessionConfig);
            handler.setNext(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
                    final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
                    Session session = manager.getSession(exchange, sessionConfig);
                    if (session == null) {
                        //  set 1 second timeout for this session expiration
                        manager.setDefaultSessionTimeout(maxInactiveIntervalInSeconds);
                        session = manager.createSession(exchange, sessionConfig);
                        session.setAttribute(COUNT, 0);
                        //  let's call getAttribute() some times to be sure that the session timeout is no longer bumped
                        //  by the method invocation
                        Runnable r = new Runnable() {
                            public void run() {
                                Session innerThreadSession = manager.getSession(exchange, sessionConfig);
                                int iterations = ((maxInactiveIntervalInSeconds * 1000)/accessorThreadSleepInMilliseconds);
                                for (int i = 0; i <= iterations; i++) {
                                    try {
                                        Thread.sleep(accessorThreadSleepInMilliseconds);
                                    } catch (InterruptedException e) {
                                        System.out.println(
                                                String.format("Unexpected error during Thread.sleep(): %s", e.getMessage()));
                                    }
                                    if (innerThreadSession != null) {
                                        try {
                                            System.out.println(String.format("Session is still valid. Attribute is: %s", innerThreadSession.getAttribute(COUNT).toString()));
                                            if (i == iterations) {
                                                System.out.println("Session should not still be valid!");
                                            }
                                        } catch (IllegalStateException e) {
                                            if ((e instanceof IllegalStateException) && e.getMessage().startsWith("UT000010")) {
                                                System.out.println(
                                                        String.format("This is expected as session is not valid anymore: %s", e.getMessage()));
                                            } else {
                                                System.out.println(
                                                        String.format("Unexpected exception while calling session.getAttribute(): %s", e.getMessage()));
                                            }
                                        }
                                    }
                                }
                            }
                        };
                        Thread thread = new Thread(r);
                        thread.start();
                    }
                    //  here the server is accessing one session attribute, so we're sure that the bumped timeout
                    //  issue is being replicated and we can test for regression
                    Integer count = (Integer) session.getAttribute(COUNT);
                    exchange.getResponseHeaders().add(new HttpString(COUNT), count.toString());
                    session.setAttribute(COUNT, ++count);
                }
            });
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Header[] header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());

            Thread.sleep(2 * 1000L);
            //  after 2 seconds from the last call, the session expiration timeout hasn't been bumped anymore,
            //  so now "COUNT" should be still set to 0 (zero)
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
