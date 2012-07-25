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

package tmp.texugo.test.session.inmemory;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import tmp.texugo.server.HttpCompletionHandler;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.server.handlers.HttpHandlers;
import tmp.texugo.server.handlers.ResponseCodeHandler;
import tmp.texugo.server.session.InMemorySessionManager;
import tmp.texugo.server.session.Session;
import tmp.texugo.server.session.SessionAttachmentHandler;
import tmp.texugo.server.session.SessionManager;
import tmp.texugo.test.util.DefaultServer;
import tmp.texugo.test.util.HttpClientUtils;

/**
 * basic test of in memory session functionality
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class InMemorySessionTestCase {

    public static final String COUNT = "count";

    @Test
    public void testBasicPathHanding() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        client.setCookieStore(new BasicCookieStore());
        try {
            final SessionAttachmentHandler handler = new SessionAttachmentHandler();
            handler.setNext(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
                    Session session = (Session) exchange.getAttachment(Session.ATTACHMENT_KEY);
                    if(session == null) {
                        final SessionManager manager = (SessionManager) exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
                        session = manager.createSession(exchange);
                        session.setAttribute(COUNT, 0);
                    }
                    Integer count = (Integer)session.getAttribute(COUNT);
                    exchange.getResponseHeaders().add(COUNT, count.toString());
                    session.setAttribute(COUNT, ++count);
                    HttpHandlers.executeHandler(ResponseCodeHandler.HANDLE_200, exchange, completionHandler);
                }
            });
            handler.setSessionManager(new InMemorySessionManager());
            DefaultServer.setRootHandler(handler);

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
