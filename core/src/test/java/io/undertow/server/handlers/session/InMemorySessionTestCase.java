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
import java.util.ArrayList;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import io.undertow.testutils.TestHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

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

    private static final String PATH = "/";
    private static final String SUB_PATH = "/sub";
    private static final String COOKIE_NAME = "JSESSIONID";

    @Test // https://issues.redhat.com/browse/UNDERTOW-2149
    public void inMemorySessionTransitiveMultiplePathsCookieHandling_V1() throws IOException, InterruptedException {
        // transitive, '/' than '/sub' - this will have same SID as its a "client request ID" scenario since '/' will match
        // '/sub'
        try {
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION, false));
            final PathHandler rootHandler = new PathHandler();

            SessionCookieConfig sessionCookieConfig = new SessionCookieConfig() {
                @Override
                public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
                    Cookie cookie = new CookieImpl(this.getCookieName(), sessionId).setPath(this.getPath())
                            .setDomain(this.getDomain()).setDiscard(this.isDiscard()).setSecure(this.isSecure())
                            .setHttpOnly(this.isHttpOnly()).setComment(this.getComment());
                    if (this.getMaxAge() > 0) {
                        cookie.setMaxAge(this.getMaxAge());
                    }
                    // force V1....
                    cookie.setVersion(1);

                    exchange.setResponseCookie(cookie);
                    UndertowLogger.SESSION_LOGGER.tracef("Setting session cookie session id %s on %s", sessionId, exchange);
                }
            };
            sessionCookieConfig.setCookieName(COOKIE_NAME).setPath(PATH);

            SessionAttachmentHandler sessionAttachmentHandler = new SessionAttachmentHandler(new InMemorySessionManager("-1"),
                    sessionCookieConfig);
            sessionAttachmentHandler.setNext(new CopyHeadersHttpHandler());
            rootHandler.addExactPath(PATH, sessionAttachmentHandler);

            sessionCookieConfig = new SessionCookieConfig() {
                @Override
                public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
                    Cookie cookie = new CookieImpl(this.getCookieName(), sessionId).setPath(this.getPath())
                            .setDomain(this.getDomain()).setDiscard(this.isDiscard()).setSecure(this.isSecure())
                            .setHttpOnly(this.isHttpOnly()).setComment(this.getComment());
                    if (this.getMaxAge() > 0) {
                        cookie.setMaxAge(this.getMaxAge());
                    }
                    // force V1....
                    cookie.setVersion(1);

                    exchange.setResponseCookie(cookie);
                    UndertowLogger.SESSION_LOGGER.tracef("Setting session cookie session id %s on %s", sessionId, exchange);
                }
            };
            sessionCookieConfig.setCookieName(COOKIE_NAME).setPath(SUB_PATH);

            sessionAttachmentHandler = new SessionAttachmentHandler(new InMemorySessionManager("-2"), sessionCookieConfig);
            sessionAttachmentHandler.setNext(new CopyHeadersHttpHandler());
            rootHandler.addExactPath(SUB_PATH, sessionAttachmentHandler);
            DefaultServer.setRootHandler(rootHandler);

            TestHttpClient client = new TestHttpClient();
            client.setCookieStore(new BasicCookieStore());

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + PATH);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            // incoming should be empty
            Header[] responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
            Assert.assertNotNull(responseIncomingIDHeaders);
            Assert.assertEquals(0, responseIncomingIDHeaders.length);
            Header sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
            Assert.assertNotNull(sidHeader);
            final String pathSID = sidHeader.getValue();
            Assert.assertNotNull(pathSID);
            Header[] responseSetCookieHeaders = result.getHeaders("Set-Cookie");
            Assert.assertNotNull(responseSetCookieHeaders);
            Assert.assertEquals(1, responseSetCookieHeaders.length);
            verifyValue(responseSetCookieHeaders[0], pathSID);
            verifyPath(responseSetCookieHeaders[0], PATH);
            HttpClientUtils.readResponse(result);

            // response to sub path will:
            // - contain another set-cookie
            // - incoming_sid
            // - sid == incoming/pathSID
            get = new HttpGet(DefaultServer.getDefaultServerURL() + SUB_PATH);
            result = client.execute(get);
            sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
            Assert.assertNotNull(sidHeader);
            Assert.assertEquals(pathSID, sidHeader.getValue());
            responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
            Assert.assertNotNull(responseIncomingIDHeaders);
            Assert.assertEquals(1, responseIncomingIDHeaders.length);
            Header[] responseIncomingIDPathHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING_PATH);
            Assert.assertNotNull(responseIncomingIDPathHeaders);
            Assert.assertEquals(1, responseIncomingIDPathHeaders.length);
            Assert.assertEquals(pathSID, responseIncomingIDHeaders[0].getValue());
            Assert.assertEquals(PATH, responseIncomingIDPathHeaders[0].getValue());
            responseSetCookieHeaders = result.getHeaders("Set-Cookie");
            Assert.assertNotNull(responseSetCookieHeaders);
            Assert.assertEquals(1, responseSetCookieHeaders.length);
            verifyValue(responseSetCookieHeaders[0], pathSID);
            verifyPath(responseSetCookieHeaders[0], SUB_PATH);
            HttpClientUtils.readResponse(result);

            //This will send sid on path /, but it the only and closest match
            get = new HttpGet(DefaultServer.getDefaultServerURL() + SUB_PATH);
            result = client.execute(get);

            sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
            Assert.assertNotNull(sidHeader);
            Assert.assertEquals(pathSID, sidHeader.getValue());
            responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
            Assert.assertNotNull(responseIncomingIDHeaders);
            Assert.assertEquals(1, responseIncomingIDHeaders.length);
            responseIncomingIDPathHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING_PATH);
            Assert.assertNotNull(responseIncomingIDPathHeaders);
            Assert.assertEquals(1, responseIncomingIDPathHeaders.length);
            Assert.assertEquals(pathSID, responseIncomingIDHeaders[0].getValue());
            Assert.assertEquals(SUB_PATH, responseIncomingIDPathHeaders[0].getValue());
            responseSetCookieHeaders = result.getHeaders("Set-Cookie");
            Assert.assertNotNull(responseSetCookieHeaders);
            Assert.assertEquals(0, responseSetCookieHeaders.length);
            HttpClientUtils.readResponse(result);
        } finally {
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.REQUEST_PARSE_TIMEOUT, 10));
        }
    }

    @Test // https://issues.redhat.com/browse/UNDERTOW-2149
    public void inMemorySessionNonTransitiveMultiplePathsCookieHandling_V1() throws IOException, InterruptedException {
        try {
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION, false));
            final PathHandler rootHandler = new PathHandler();

            SessionCookieConfig sessionCookieConfig = new SessionCookieConfig() {
                @Override
                public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
                    Cookie cookie = new CookieImpl(this.getCookieName(), sessionId).setPath(this.getPath())
                            .setDomain(this.getDomain()).setDiscard(this.isDiscard()).setSecure(this.isSecure())
                            .setHttpOnly(this.isHttpOnly()).setComment(this.getComment());
                    if (this.getMaxAge() > 0) {
                        cookie.setMaxAge(this.getMaxAge());
                    }
                    // force V1....
                    cookie.setVersion(1);

                    exchange.setResponseCookie(cookie);
                    UndertowLogger.SESSION_LOGGER.tracef("Setting session cookie session id %s on %s", sessionId, exchange);
                }
            };
            sessionCookieConfig.setCookieName(COOKIE_NAME);
            sessionCookieConfig.setPath(PATH);
            SessionAttachmentHandler sessionAttachmentHandler = new SessionAttachmentHandler(new InMemorySessionManager("-1"),
                    sessionCookieConfig);
            sessionAttachmentHandler.setNext(new CopyHeadersHttpHandler());
            rootHandler.addExactPath(PATH, sessionAttachmentHandler);

            sessionCookieConfig = new SessionCookieConfig() {
                @Override
                public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
                    Cookie cookie = new CookieImpl(this.getCookieName(), sessionId).setPath(this.getPath())
                            .setDomain(this.getDomain()).setDiscard(this.isDiscard()).setSecure(this.isSecure())
                            .setHttpOnly(this.isHttpOnly()).setComment(this.getComment());
                    if (this.getMaxAge() > 0) {
                        cookie.setMaxAge(this.getMaxAge());
                    }
                    // force V1....
                    cookie.setVersion(1);

                    exchange.setResponseCookie(cookie);
                    UndertowLogger.SESSION_LOGGER.tracef("Setting session cookie session id %s on %s", sessionId, exchange);
                }
            };
            sessionCookieConfig.setCookieName(COOKIE_NAME);
            sessionCookieConfig.setPath(SUB_PATH);
            sessionAttachmentHandler = new SessionAttachmentHandler(new InMemorySessionManager("-2"), sessionCookieConfig);
            sessionAttachmentHandler.setNext(new CopyHeadersHttpHandler());
            rootHandler.addExactPath(SUB_PATH, sessionAttachmentHandler);
            DefaultServer.setRootHandler(rootHandler);

            TestHttpClient client = new TestHttpClient();
            client.setCookieStore(new BasicCookieStore() {

                @Override
                public List<org.apache.http.cookie.Cookie> getCookies() {
                    // need to flip those, NATURAL order is bad, its even worse when done on multiple fields
                    // from our perspective it means it orders cookies by shortest name... than by match.
                    List<org.apache.http.cookie.Cookie> bastardizedList = super.getCookies();
                    List<org.apache.http.cookie.Cookie> properList = new ArrayList<>(bastardizedList.size());
                    for (org.apache.http.cookie.Cookie c : bastardizedList) {
                        properList.add(0, c);
                    }
                    return properList;
                }
            });

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + SUB_PATH);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            // incoming should be empty
            Header[] responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
            Assert.assertNotNull(responseIncomingIDHeaders);
            Assert.assertEquals(0, responseIncomingIDHeaders.length);
            Header sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
            Assert.assertNotNull(sidHeader);
            final String pathSID = sidHeader.getValue();
            Assert.assertNotNull(pathSID);
            Header[] responseSetCookieHeaders = result.getHeaders("Set-Cookie");
            Assert.assertNotNull(responseSetCookieHeaders);
            Assert.assertEquals(1, responseSetCookieHeaders.length);
            verifyValue(responseSetCookieHeaders[0], pathSID);
            verifyPath(responseSetCookieHeaders[0], SUB_PATH);
            HttpClientUtils.readResponse(result);

            // response to path will:
            // - contain another set-cookie
            // - incoming_sid
            // - sid == incoming/pathSID
            get = new HttpGet(DefaultServer.getDefaultServerURL() + PATH);
            result = client.execute(get);
            sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
            Assert.assertNotNull(sidHeader);
            Assert.assertNotEquals(pathSID, sidHeader.getValue());
            responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
            Assert.assertNotNull(responseIncomingIDHeaders);
            Assert.assertEquals(0, responseIncomingIDHeaders.length);
            Header[] responseIncomingIDPathHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING_PATH);
            Assert.assertNotNull(responseIncomingIDPathHeaders);
            Assert.assertEquals(0, responseIncomingIDPathHeaders.length);
            responseSetCookieHeaders = result.getHeaders("Set-Cookie");
            Assert.assertNotNull(responseSetCookieHeaders);
            Assert.assertEquals(1, responseSetCookieHeaders.length);
            verifyValue(responseSetCookieHeaders[0], sidHeader.getValue());
            verifyPath(responseSetCookieHeaders[0], PATH);
            HttpClientUtils.readResponse(result);

            //This will send sid on path /, but it the only and closest match
            get = new HttpGet(DefaultServer.getDefaultServerURL() + SUB_PATH);
            result = client.execute(get);
            sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
            Assert.assertNotNull(sidHeader);
            Assert.assertEquals(pathSID, sidHeader.getValue());
            responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
            Assert.assertNotNull(responseIncomingIDHeaders);
            Assert.assertEquals(1, responseIncomingIDHeaders.length);
            responseIncomingIDPathHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING_PATH);
            Assert.assertNotNull(responseIncomingIDPathHeaders);
            Assert.assertEquals(1, responseIncomingIDPathHeaders.length);
            Assert.assertEquals(pathSID, responseIncomingIDHeaders[0].getValue());
            Assert.assertEquals(SUB_PATH, responseIncomingIDPathHeaders[0].getValue());
            responseSetCookieHeaders = result.getHeaders("Set-Cookie");
            Assert.assertNotNull(responseSetCookieHeaders);
            Assert.assertEquals(0, responseSetCookieHeaders.length);
            HttpClientUtils.readResponse(result);
        } finally {
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.REQUEST_PARSE_TIMEOUT, 10));
        }
    }

    @Test // https://issues.redhat.com/browse/UNDERTOW-2149
    public void inMemorySessionTransitiveMultiplePathsCookieHandling_V0() throws IOException, InterruptedException {
        // transitive, '/' than '/sub' - this will have same SID as its a "client request ID" scenario since '/' will match
        // '/sub'
        final PathHandler rootHandler = new PathHandler();

        SessionCookieConfig sessionCookieConfig = new SessionCookieConfig();
        sessionCookieConfig.setCookieName(COOKIE_NAME).setPath(PATH);

        SessionAttachmentHandler sessionAttachmentHandler = new SessionAttachmentHandler(new InMemorySessionManager("-1"),
                sessionCookieConfig);
        sessionAttachmentHandler.setNext(new CopyHeadersHttpHandler());
        rootHandler.addExactPath(PATH, sessionAttachmentHandler);

        sessionCookieConfig = new SessionCookieConfig();
        sessionCookieConfig.setCookieName(COOKIE_NAME).setPath(SUB_PATH);

        sessionAttachmentHandler = new SessionAttachmentHandler(new InMemorySessionManager("-2"), sessionCookieConfig);
        sessionAttachmentHandler.setNext(new CopyHeadersHttpHandler());
        rootHandler.addExactPath(SUB_PATH, sessionAttachmentHandler);
        DefaultServer.setRootHandler(rootHandler);

        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new BasicCookieStore());

        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + PATH);
        HttpResponse result = client.execute(get);
        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        //incoming should be empty
        Header[] responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
        Assert.assertNotNull(responseIncomingIDHeaders);
        Assert.assertEquals(0, responseIncomingIDHeaders.length);
        Header sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
        Assert.assertNotNull(sidHeader);
        final String pathSID = sidHeader.getValue();
        Assert.assertNotNull(pathSID);
        Header[] responseSetCookieHeaders = result.getHeaders("Set-Cookie");
        Assert.assertNotNull(responseSetCookieHeaders);
        Assert.assertEquals(1, responseSetCookieHeaders.length);
        verifyValue(responseSetCookieHeaders[0], pathSID);
        verifyPath(responseSetCookieHeaders[0], PATH);
        HttpClientUtils.readResponse(result);

        //response to sub path will:
        //- contain another set-cookie
        //- incoming_sid
        //- sid == incoming/pathSID
        get = new HttpGet(DefaultServer.getDefaultServerURL() + SUB_PATH);
        result = client.execute(get);
        sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
        Assert.assertNotNull(sidHeader);
        Assert.assertEquals(pathSID, sidHeader.getValue());
        responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
        Assert.assertNotNull(responseIncomingIDHeaders);
        Assert.assertEquals(1, responseIncomingIDHeaders.length);
        Header[] responseIncomingIDPathHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING_PATH);
        Assert.assertNotNull(responseIncomingIDPathHeaders);
        Assert.assertEquals(1, responseIncomingIDPathHeaders.length);
        Assert.assertEquals(pathSID, responseIncomingIDHeaders[0].getValue());
        Assert.assertEquals(PATH, responseIncomingIDPathHeaders[0].getValue());
        responseSetCookieHeaders = result.getHeaders("Set-Cookie");
        Assert.assertNotNull(responseSetCookieHeaders);
        Assert.assertEquals(1, responseSetCookieHeaders.length);
        verifyValue(responseSetCookieHeaders[0], pathSID);
        verifyPath(responseSetCookieHeaders[0], SUB_PATH);
        HttpClientUtils.readResponse(result);


        get = new HttpGet(DefaultServer.getDefaultServerURL() + SUB_PATH);
        result = client.execute(get);

        sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
        Assert.assertNotNull(sidHeader);
        Assert.assertEquals(pathSID, sidHeader.getValue());
        responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
        Assert.assertNotNull(responseIncomingIDHeaders);
        Assert.assertEquals(1, responseIncomingIDHeaders.length);
        responseIncomingIDPathHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING_PATH);
        Assert.assertNotNull(responseIncomingIDPathHeaders);
        Assert.assertEquals(1, responseIncomingIDPathHeaders.length);
        Assert.assertEquals(pathSID, responseIncomingIDHeaders[0].getValue());
        // "/" - according to UA algo...
        Assert.assertEquals("/", responseIncomingIDPathHeaders[0].getValue());
        responseSetCookieHeaders = result.getHeaders("Set-Cookie");
        Assert.assertNotNull(responseSetCookieHeaders);
        Assert.assertEquals(0, responseSetCookieHeaders.length);
        HttpClientUtils.readResponse(result);
    }

    @Test // https://issues.redhat.com/browse/UNDERTOW-2149
    public void inMemorySessionNonTransitiveMultiplePathsCookieHandling_V0() throws IOException, InterruptedException {

        final PathHandler rootHandler = new PathHandler();

        SessionCookieConfig sessionCookieConfig = new SessionCookieConfig();
        sessionCookieConfig.setCookieName(COOKIE_NAME);
        sessionCookieConfig.setPath(PATH);
        SessionAttachmentHandler sessionAttachmentHandler = new SessionAttachmentHandler(new InMemorySessionManager("-1"),
                sessionCookieConfig);
        sessionAttachmentHandler.setNext(new CopyHeadersHttpHandler());
        rootHandler.addExactPath(PATH, sessionAttachmentHandler);

        sessionCookieConfig = new SessionCookieConfig();
        sessionCookieConfig.setCookieName(COOKIE_NAME);
        sessionCookieConfig.setPath(SUB_PATH);
        sessionAttachmentHandler = new SessionAttachmentHandler(new InMemorySessionManager("-2"), sessionCookieConfig);
        sessionAttachmentHandler.setNext(new CopyHeadersHttpHandler());
        rootHandler.addExactPath(SUB_PATH, sessionAttachmentHandler);
        DefaultServer.setRootHandler(rootHandler);

        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new BasicCookieStore() {
//NOTE: this is done as hack to make test pass. Currently we dont allow duplicates so previous values are removed.
//see MultiValueHashListStorage.put
//CookiesTestCase#testMultipleRFC6265
//CookiesTestCase#testEmptyCookieNames
//            @Override
//            public List<org.apache.http.cookie.Cookie> getCookies() {
//                // need to flip those, NATURAL order is bad, its even worse when done on multiple fields
//                //from our perspective it means it orders cookies by shortest name... than by match.
//                List<org.apache.http.cookie.Cookie> bastardizedList = super.getCookies();
//                List<org.apache.http.cookie.Cookie> properList = new ArrayList<>(bastardizedList.size());
//                for(org.apache.http.cookie.Cookie c:bastardizedList) {
//                    properList.add(0, c);
//                }
//                return properList;
//            }
        });

        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + SUB_PATH);
        HttpResponse result = client.execute(get);
        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        //incoming should be empty
        Header[] responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
        Assert.assertNotNull(responseIncomingIDHeaders);
        Assert.assertEquals(0, responseIncomingIDHeaders.length);
        Header sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
        Assert.assertNotNull(sidHeader);
        final String pathSID = sidHeader.getValue();
        Assert.assertNotNull(pathSID);
        Header[] responseSetCookieHeaders = result.getHeaders("Set-Cookie");
        Assert.assertNotNull(responseSetCookieHeaders);
        Assert.assertEquals(1, responseSetCookieHeaders.length);
        verifyValue(responseSetCookieHeaders[0], pathSID);
        verifyPath(responseSetCookieHeaders[0], SUB_PATH);
        HttpClientUtils.readResponse(result);

        //response to path will:
        //- contain another set-cookie
        //- incoming_sid
        //- sid == incoming/pathSID
        get = new HttpGet(DefaultServer.getDefaultServerURL() + PATH);
        result = client.execute(get);
        sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
        Assert.assertNotNull(sidHeader);
        Assert.assertNotEquals(pathSID, sidHeader.getValue());
        responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
        Assert.assertNotNull(responseIncomingIDHeaders);
        Assert.assertEquals(0, responseIncomingIDHeaders.length);
        Header[] responseIncomingIDPathHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING_PATH);
        Assert.assertNotNull(responseIncomingIDPathHeaders);
        Assert.assertEquals(0, responseIncomingIDPathHeaders.length);
        responseSetCookieHeaders = result.getHeaders("Set-Cookie");
        Assert.assertNotNull(responseSetCookieHeaders);
        Assert.assertEquals(1, responseSetCookieHeaders.length);
        verifyValue(responseSetCookieHeaders[0], sidHeader.getValue());
        verifyPath(responseSetCookieHeaders[0], PATH);
        HttpClientUtils.readResponse(result);


        get = new HttpGet(DefaultServer.getDefaultServerURL() + SUB_PATH);
        result = client.execute(get);
        sidHeader = result.getFirstHeader(CopyHeadersHttpHandler.HEADER_ID);
        Assert.assertNotNull(sidHeader);
        Assert.assertEquals(pathSID, sidHeader.getValue());
        responseIncomingIDHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING);
        Assert.assertNotNull(responseIncomingIDHeaders);
        Assert.assertEquals(1, responseIncomingIDHeaders.length);
        responseIncomingIDPathHeaders = result.getHeaders(CopyHeadersHttpHandler.HEADER_ID_INCOMING_PATH);
        Assert.assertNotNull(responseIncomingIDPathHeaders);
        Assert.assertEquals(1, responseIncomingIDPathHeaders.length);
        Assert.assertEquals(pathSID, responseIncomingIDHeaders[0].getValue());
        // "/" - according to UA algo...
        Assert.assertEquals("/", responseIncomingIDPathHeaders[0].getValue());
        responseSetCookieHeaders = result.getHeaders("Set-Cookie");
        Assert.assertNotNull(responseSetCookieHeaders);
        Assert.assertEquals(0, responseSetCookieHeaders.length);
        HttpClientUtils.readResponse(result);
    }

    private static void verifyPath(final Header h, final String expected) {
        Assert.assertNotNull(h);
        Assert.assertNotNull(expected);
        HeaderElement[] els = h.getElements();
        Assert.assertNotNull(els);
        Assert.assertEquals(1,els.length);
        NameValuePair pams = els[0].getParameterByName("Path");
        Assert.assertNotNull(pams);
        Assert.assertEquals(expected,pams.getValue());
    }
    private static void verifyValue(final Header h, final String expected) {
        Assert.assertNotNull(h);
        Assert.assertNotNull(expected);
        HeaderElement[] he = h.getElements();
        Assert.assertNotNull(he);
        Assert.assertEquals(1,he.length);
        Assert.assertEquals(expected,he[0].getValue());

    }
    private static final class CopyHeadersHttpHandler implements HttpHandler{

        private static final String HEADER_ID = "SID";
        private static final String HEADER_ID_INCOMING = "SID_INCOMING";
        private static final String HEADER_ID_INCOMING_PATH = "SID_INCOMING_PATH";
        private static final String HEADER_PATH = "PATH";
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {

            final Cookie incomingID = exchange.getRequestCookie(COOKIE_NAME);
            //NOTE: dont use cookies here to sent back. apache seem to ignore other bits?
            //or there is some specific scenario that is hiding this
            if(incomingID != null) {
                exchange.getResponseHeaders().add(new HttpString(HEADER_ID_INCOMING), incomingID.getValue());
                if(incomingID.getPath() == null) {
                    exchange.getResponseHeaders().add(new HttpString(HEADER_ID_INCOMING_PATH), "/");
                }else {
                    exchange.getResponseHeaders().add(new HttpString(HEADER_ID_INCOMING_PATH), incomingID.getPath());
                }
            }
            final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
            final SessionCookieConfig sessionCookieConfig = (SessionCookieConfig) exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);
            Session session = manager.getSession(exchange, sessionCookieConfig);
            if( session == null) {
                session = manager.createSession(exchange, sessionCookieConfig);
            }
            exchange.getResponseHeaders().add(new HttpString(HEADER_ID), session.getId());
            exchange.getResponseHeaders().add(new HttpString(HEADER_PATH), exchange.getRequestPath());
            exchange.getResponseHeaders().addAll(Headers.COOKIE, exchange.getRequestHeaders().get(Headers.COOKIE_STRING));
            exchange.setStatusCode(200);
        }

    }
}
