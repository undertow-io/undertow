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
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;

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
        handler.setNext(exchange -> {
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
        });
        DefaultServer.setRootHandler(handler);
    }

    @Test
    public void testURLRewriting() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath;foo=bar");
            String url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("0", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("1", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });
        }
    }

    @Test
    public void testURLRewritingWithQueryParameters() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/notamatchingpath?a=b;c");
            String url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("0", header[0].getValue());
                Assert.assertEquals("b;c", result.getHeaders("a")[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("1", header[0].getValue());
                Assert.assertEquals("b;c", result.getHeaders("a")[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("2", header[0].getValue());
                Assert.assertEquals("b;c", result.getHeaders("a")[0].getValue());
                return HttpClientUtils.readResponse(result);
            });
        }
    }
}
