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

package io.undertow.server.handlers.blocking;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.Methods;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
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
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                try {
                    if (exchange.getRequestMethod().equals(Methods.POST)) {
                        //for a post we just echo back what was sent
                        //we need to fully buffer it, as otherwise the send buffer fills up, and the client will still be blocked
                        //on writing and will never read
                        byte[] buffer = new byte[1024];
                        final ByteArrayOutputStream b = new ByteArrayOutputStream();
                        int r = 0;
                        final OutputStream outputStream = exchange.getOutputStream();
                        final InputStream inputStream = exchange.getInputStream();
                        while ((r = inputStream.read(buffer)) > 0) {
                            b.write(buffer, 0, r);
                        }
                        outputStream.write(b.toByteArray());
                        outputStream.close();
                    } else {
                        if (exchange.getQueryParameters().containsKey("useFragmentedSender")) {
                            //we send it byte at a time
                            exchange.getResponseSender().send("", new IoCallback() {
                                int i = 0;

                                @Override
                                public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                                    if (i == message.length()) {
                                        sender.close();
                                        exchange.endExchange();
                                    } else {
                                        sender.send("" + message.charAt(i++), this);
                                    }
                                }

                                @Override
                                public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                                    exchange.endExchange();
                                }
                            });

                        } else if (exchange.getQueryParameters().containsKey("useSender")) {
                            exchange.getResponseSender().send(message, IoCallback.END_EXCHANGE);
                        } else {
                            final OutputStream outputStream = exchange.getOutputStream();
                            outputStream.write(message.getBytes());
                            outputStream.close();
                        }
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
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
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
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertTrue(message.equals(HttpClientUtils.readResponse(result)));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?useSender");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String resultBody = HttpClientUtils.readResponse(result);
            Assert.assertTrue(message.equals(resultBody));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?useFragmentedSender");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            resultBody = HttpClientUtils.readResponse(result);
            Assert.assertTrue(message.equals(resultBody));

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
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity(messageBuilder.toString()));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertTrue(messageBuilder.toString().equals(HttpClientUtils.readResponse(result)));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
