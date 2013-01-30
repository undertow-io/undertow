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

package io.undertow.test.session.inmemory;

import java.io.IOException;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CookieHandler;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.HttpString;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import io.undertow.util.TestHttpClient;
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
        final CookieHandler cookieHandler = new CookieHandler();
        try {
            final SessionCookieConfig sessionConfig = new SessionCookieConfig();
            final SessionAttachmentHandler handler = new SessionAttachmentHandler(new InMemorySessionManager(), sessionConfig);
            handler.setNext(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) {
                    try {
                        Session session = sessionConfig.getAttachedSession(exchange);
                        if(session == null) {
                            final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
                            session = manager.createSession(exchange, sessionConfig).get();
                            session.setAttribute(COUNT, 0);
                        }
                        Integer count = (Integer)session.getAttribute(COUNT).get();
                        exchange.getResponseHeaders().add(new HttpString(COUNT), count.toString());
                        session.setAttribute(COUNT, ++count);
                        HttpHandlers.executeHandler(ResponseCodeHandler.HANDLE_200, exchange);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            cookieHandler.setNext(handler);
            DefaultServer.setRootHandler(cookieHandler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Header[] header = result.getHeaders(COUNT);
            Assert.assertEquals("0", header[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("1", header[0].getValue());

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            header = result.getHeaders(COUNT);
            Assert.assertEquals("2", header[0].getValue());


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
