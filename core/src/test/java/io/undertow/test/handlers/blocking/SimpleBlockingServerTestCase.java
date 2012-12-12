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

package io.undertow.test.handlers.blocking;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import io.undertow.util.TestHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleBlockingServerTestCase {

    private static volatile String message;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new BlockingHttpHandler() {
            @Override
            public void handleRequest(final BlockingHttpServerExchange exchange) {
                try {
                    if (exchange.getExchange().getRequestMethod().equals(Methods.POST)) {
                        //for a post we just echo back what was sent
                        exchange.getExchange().getResponseHeaders().put(Headers.CONTENT_LENGTH, exchange.getExchange().getRequestHeaders().getFirst(Headers.CONTENT_LENGTH));
                        //we need to fully buffer it, as otherwise the send buffer fills up, and the client will still be blocked
                        //on writing and will never read
                        byte[] buffer = new byte[1024];
                        final ByteArrayOutputStream b = new ByteArrayOutputStream();
                        int r = 0;
                        while ((r = exchange.getInputStream().read(buffer)) > 0) {
                            b.write(buffer, 0 , r);
                        }
                        exchange.getOutputStream().write(b.toByteArray());
                        exchange.getOutputStream().close();
                    } else {
                        exchange.getExchange().getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
                        exchange.getOutputStream().write(message.getBytes());
                        exchange.getOutputStream().close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void sendHttpRequest() throws IOException {
        message = "My HTTP Request!";
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testLargeResponse() throws IOException {
        final StringBuilder messageBuilder = new StringBuilder(6919638);
        for (int i = 0; i < 6919638; ++i) {
            messageBuilder.append("*");
        }
        message = messageBuilder.toString();
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testLargeRequest() throws IOException {
        message = null;
        final StringBuilder messageBuilder = new StringBuilder(6919638);
        for (int i = 0; i < 6919638; ++i) {
            messageBuilder.append("+");
        }
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/path");
            post.setEntity(new StringEntity(messageBuilder.toString()));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals(messageBuilder.toString(), HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
