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

import io.undertow.io.UndertowOutputStream;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Methods;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;

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
        blockingHandler.setRootHandler(new BlockingHttpHandler() {
            @Override
            public void handleBlockingRequest(final HttpServerExchange exchange) {
                try {
                    if (exchange.getRequestMethod().equals(Methods.POST)) {
                        //for a post we just echo back what was sent
                        //we need to fully buffer it, as otherwise the send buffer fills up, and the client will still be blocked
                        //on writing and will never read
                        byte[] buffer = new byte[1024];
                        final ByteArrayOutputStream b = new ByteArrayOutputStream();
                        int r = 0;
                        final OutputStream outputStream = new UndertowOutputStream(exchange);
                        final InputStream inputStream = new ChannelInputStream(exchange.getRequestChannel());
                        while ((r = inputStream.read(buffer)) > 0) {
                            b.write(buffer, 0 , r);
                        }
                        outputStream.write(b.toByteArray());
                        outputStream.close();
                    } else {
                        final OutputStream outputStream = new ChannelOutputStream(exchange.getResponseChannel());
                        outputStream.write(message.getBytes());
                        outputStream.close();
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
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity(messageBuilder.toString()));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals(messageBuilder.toString(), HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
