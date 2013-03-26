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

package io.undertow.test.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.HttpHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ChunkedRequestTransferCodingTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    private static volatile HttpServerConnection connection;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                try {
                    if (connection == null) {
                        connection = exchange.getConnection();
                    } else if (!DefaultServer.isAjp() && connection.getChannel() != exchange.getConnection().getChannel()) {
                        exchange.setResponseCode(500);
                        final OutputStream outputStream = exchange.getOutputStream();
                        outputStream.write("Connection not persistent".getBytes());
                        outputStream.close();
                        return;
                    }
                    final OutputStream outputStream = exchange.getOutputStream();
                    final InputStream inputSream = exchange.getInputStream();
                    String m = HttpClientUtils.readResponse(inputSream);
                    Assert.assertEquals(message, m);
                    inputSream.close();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testChunkedRequest() throws IOException {
        connection = null;
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            generateMessage(1);
            post.setEntity(new StringEntity(message) {
                @Override
                public long getContentLength() {
                    return -1;
                }
            });
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            generateMessage(1000);
            post.setEntity(new StringEntity(message) {
                @Override
                public long getContentLength() {
                    return -1;
                }

                @Override
                public boolean isChunked() {
                    return true;
                }

                @Override
                public void writeTo(OutputStream outstream) throws IOException {
                    int l = 0;
                    int i = 0;
                    Random random = new Random(1000);
                    while (i < message.length()) {
                        i += random.nextInt(100);
                        i = Math.min(i, message.length());
                        outstream.write(message.getBytes(), l, i - l);
                        l = i;
                        ++i;
                    }
                }
            });
            result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {

            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @Ignore("sometimes the client attempts to re-use the same connection after the failure, but the server has already closed it")
    public void testMaxRequestSizeChunkedRequest() throws IOException {
        connection = null;
        OptionMap existing = DefaultServer.getUndertowOptions();
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        post.setHeader(HttpHeaders.CONNECTION, "close");
        TestHttpClient client = new TestHttpClient();
        try {
            generateMessage(1);
            post.setEntity(new StringEntity(message) {
                @Override
                public long getContentLength() {
                    return -1;
                }
            });
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, 3L));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(500, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            connection = null;
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, (long) message.length()));
            result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

        } finally {
            DefaultServer.setUndertowOptions(existing);
            client.getConnectionManager().shutdown();
        }
    }


    private static void generateMessage(int repetitions) {
        final StringBuilder builder = new StringBuilder(repetitions * MESSAGE.length());
        for (int i = 0; i < repetitions; ++i) {
            builder.append(MESSAGE);
        }
        message = builder.toString();
    }
}
