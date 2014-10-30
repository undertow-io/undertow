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
import java.util.Deque;
import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.PathParameterSessionConfig;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * basic test of in memory session functionality
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class URLRewritingSessionTestCase {

    public static final String COUNT = "count";

    @BeforeClass
    public static void setup() {
        final PathParameterSessionConfig sessionConfig = new PathParameterSessionConfig();
        final SessionAttachmentHandler handler = new SessionAttachmentHandler(new InMemorySessionManager(""), sessionConfig);
        handler.setNext(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
                Session session = manager.getSession(exchange, sessionConfig);
                if (session == null) {
                    session = manager.createSession(exchange, sessionConfig);
                    session.setAttribute(COUNT, 0);
                } else {
                    Assert.assertEquals("/notamatchingpath;jsessionid=" + session.getId(), exchange.getRequestURI());
                }
                Integer count = (Integer) session.getAttribute(COUNT);
                exchange.getResponseHeaders().add(new HttpString(COUNT), count.toString());
                session.setAttribute(COUNT, ++count);

                for (Map.Entry<String, Deque<String>> qp : exchange.getQueryParameters().entrySet()) {
                    exchange.getResponseHeaders().add(new HttpString(qp.getKey()), qp.getValue().getFirst());
                }
                if (exchange.getQueryString().isEmpty()) {
                    exchange.getResponseSender().send(sessionConfig.rewriteUrl(DefaultServer.getDefaultServerURL() + "/notamatchingpath", session.getId()));
                } else {
                    exchange.getResponseSender().send(sessionConfig.rewriteUrl(DefaultServer.getDefaultServerURL() + "/notamatchingpath?" + exchange.getQueryString(), session.getId()));
                }
            }
        });
        DefaultServer.setRootHandler(handler);
    }

    @Test
    public void testURLRewriting() throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new BasicCookieStore());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String url = HttpClientUtils.readResponse(result);
            Header[] header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());

            get = new HttpGet(url);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            url = HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("1", header[0].getValue());

            get = new HttpGet(url);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            url = HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("2", header[0].getValue());


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testURLRewritingWithQueryParameters() throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new BasicCookieStore());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath?a=b");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String url = HttpClientUtils.readResponse(result);
            Header[] header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());
            Assert.assertEquals("b", result.getHeaders("a")[0].getValue());


            get = new HttpGet(url);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            url = HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("1", header[0].getValue());
            Assert.assertEquals("b", result.getHeaders("a")[0].getValue());

            get = new HttpGet(url);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            url = HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("2", header[0].getValue());
            Assert.assertEquals("b", result.getHeaders("a")[0].getValue());


        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
