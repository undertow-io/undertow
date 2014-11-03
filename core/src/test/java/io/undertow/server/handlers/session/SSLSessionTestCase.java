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
import io.undertow.server.session.SessionManager;
import io.undertow.server.session.SslSessionConfig;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
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
@ProxyIgnore
@HttpOneOnly
public class SSLSessionTestCase {

    public static final String COUNT = "count";

    @Test
    public void testSslSession() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            InMemorySessionManager sessionManager = new InMemorySessionManager("");
            final SslSessionConfig sessionConfig = new SslSessionConfig(sessionManager);
            final SessionAttachmentHandler handler = new SessionAttachmentHandler(sessionManager, sessionConfig)
                    .setNext(new HttpHandler() {
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
            DefaultServer.startSSLServer();
            client.setSSLContext(DefaultServer.getClientSSLContext());
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/notamatchingpath");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Header[] header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/notamatchingpath");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("1", header[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/notamatchingpath");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("2", header[0].getValue());

            Assert.assertEquals(0, client.getCookieStore().getCookies().size());


        } finally {
            DefaultServer.stopSSLServer();
            client.getConnectionManager().shutdown();
        }
    }

}
