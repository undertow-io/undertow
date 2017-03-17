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

package io.undertow.server.handlers.flash;

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
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests the session based flash handler
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */

@RunWith(DefaultServer.class)
public class FlashHandlerTestCase {

    @Test
    public void handlerTest() throws IOException {

        final SessionCookieConfig sessionConfig = new SessionCookieConfig();

        final FlashStoreManager flashStoreManager = new HashMapFlashStoreManager();

        HttpHandler appHandler = new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                if (exchange.getRequestPath().equals("/")) {
                    final SessionManager manager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
                    Session session = manager.getSession(exchange, sessionConfig);
                    if (session == null) {
                        manager.createSession(exchange, sessionConfig);
                    }
                } else if (exchange.getRequestPath().equals("/flash")) {
                    flashStoreManager.setAttribute(exchange, "foo", "bar");
                    flashStoreManager.setAttribute(exchange, "foo", "boo"); // overwrites
                    redirect(exchange, "/flash2");
                } else if (exchange.getRequestPath().equals("/flash2")) {
                    Assert.assertEquals("boo", flashStoreManager.getAttribute(exchange, "foo"));
                    flashStoreManager.setAttribute(exchange, "bar", "baz"); // adds a new one
                    Assert.assertEquals("boo", flashStoreManager.getAttribute(exchange, "foo"));
                    Assert.assertEquals("baz", flashStoreManager.getAttribute(exchange, "bar"));
                    redirect(exchange, "/flash3");
                } else if (exchange.getRequestPath().equals("/flash3")) {
                    Assert.assertNull(flashStoreManager.getAttribute(exchange, "foo"));
                    Assert.assertEquals("baz", flashStoreManager.getAttribute(exchange, "bar"));
                }
                exchange.endExchange();
            }
        };

        final FlashHandler flashHandler = new FlashHandler(sessionConfig, flashStoreManager);
        flashHandler.setNext(appHandler);

        final SessionAttachmentHandler sessionHandler = new SessionAttachmentHandler(flashHandler, new InMemorySessionManager(), sessionConfig);

        DefaultServer.setRootHandler(sessionHandler);

        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new BasicCookieStore());

        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            HttpResponse result = client.execute(get);
            HttpClientUtils.readResponse(result);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/flash");
            result = client.execute(get);
            HttpClientUtils.readResponse(result);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void redirect(HttpServerExchange exchange, String location) {
        exchange.getResponseHeaders().put(Headers.LOCATION, DefaultServer.getDefaultServerURL() + location);
        exchange.setResponseCode(StatusCodes.FOUND);
    }
}
