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

package io.undertow.server.handlers.blocking;

import io.undertow.UndertowOptions;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

    @DefaultServer.BeforeServerStarts
    public static void setupServer() {
        DefaultServer.setServerOptions(OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, -1L));
    }

    @DefaultServer.AfterServerStops
    public static void cleanup() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

    @Test
    public void sendHttpRequest() throws IOException {
        message = "My HTTP Request!";
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(message, HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }

    @Test
    public void testHeadRequests() throws IOException {
        message = "My HTTP Request!";
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpHead head = new HttpHead(DefaultServer.getDefaultServerURL() + "/path");
            for (int i = 0; i < 3; ++i) {
                //WFLY-1540 run a few requests to make sure persistent re
                client.execute(head, result -> {
                    Assert.assertEquals(StatusCodes.OK, result.getCode());
                    Assert.assertEquals("", HttpClientUtils.readResponse(result));
                    Assert.assertEquals(message.length() + "", result.getFirstHeader(Headers.CONTENT_LENGTH_STRING).getValue());
                    return null;
                });
            }
        }
    }

    @Test
    public void testDeleteRequests() throws IOException {
        message = "My HTTP Request!";
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpDelete delete = new HttpDelete(DefaultServer.getDefaultServerURL() + "/path");
            for (int i = 0; i < 3; ++i) {
                //WFLY-1540 run a few requests to make sure persistent re
                client.execute(delete, result -> {
                    Assert.assertEquals(StatusCodes.OK, result.getCode());
                    Assert.assertEquals(message, HttpClientUtils.readResponse(result));
                    return null;
                });
            }
        }
    }

    @Test
    public void testLargeResponse() throws IOException {
        final StringBuilder messageBuilder = new StringBuilder(6919638);
        for (int i = 0; i < 6919638; ++i) {
            messageBuilder.append("*");
        }
        message = messageBuilder.toString();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String resultString = HttpClientUtils.readResponse(result);
                Assert.assertEquals(message.length(), resultString.length());
                Assert.assertTrue(message.equals(resultString));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?useSender");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String resultBody = HttpClientUtils.readResponse(result);
                Assert.assertTrue(message.equals(resultBody));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?useFragmentedSender");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String resultBody = HttpClientUtils.readResponse(result);
                Assert.assertTrue(message.equals(resultBody));
                return null;
            });

        }
    }


    @Test
    public void testSmallRequest() throws IOException {
        message = null;
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity("a"));
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertTrue("a".equals(HttpClientUtils.readResponse(result)));
                return null;
            });
        }
    }

    @Test
    public void testLargeRequest() throws IOException {
        message = null;
        final StringBuilder messageBuilder = new StringBuilder(6919638);
        for (int i = 0; i < 6919638; ++i) {
            messageBuilder.append("+");
        }
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity(messageBuilder.toString()));
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertTrue(messageBuilder.toString().equals(HttpClientUtils.readResponse(result)));
                return null;
            });
        }
    }
}
