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

package io.undertow.server.handlers;

import io.undertow.UndertowOptions;
import io.undertow.server.ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ChunkedRequestTransferCodingTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    private static volatile ServerConnection connection;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(exchange -> {
            try {
                if (connection == null) {
                    connection = exchange.getConnection();
                } else if (!DefaultServer.isAjp() && !DefaultServer.isProxy() && connection != exchange.getConnection()) {
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    final OutputStream outputStream = exchange.getOutputStream();
                    outputStream.write("Connection not persistent".getBytes());
                    outputStream.close();
                    return;
                }
                final OutputStream outputStream = exchange.getOutputStream();
                final InputStream inputStream = exchange.getInputStream();
                String m = HttpClientUtils.readResponse(inputStream);
                Assert.assertEquals(message.length(), m.length());
                Assert.assertEquals(message, m);
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testChunkedRequest() throws IOException {
        connection = null;
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            generateMessage(1);
            post.setEntity(new AbstractHttpEntity("", null, true) {
                @Override
                public void close() {
                }

                @Override
                public long getContentLength() {
                    return -1;
                }

                @Override
                public InputStream getContent() throws UnsupportedOperationException {
                    return null;
                }

                @Override
                public void writeTo(OutputStream outstream) throws IOException {
                    outstream.write(message.getBytes());
                    outstream.flush();
                }

                @Override
                public boolean isStreaming() {
                    return false;
                }
            });
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                return HttpClientUtils.readResponse(result);
            });

            final Random random = new Random();
            final int seed = random.nextInt();
            System.out.print("Using Seed " + seed);
            random.setSeed(seed);


            for (int i = 0; i < 10; ++i) {
                generateMessage(100 * i);
                post.setEntity(new AbstractHttpEntity("", null, true) {
                    @Override
                    public void close() {
                    }

                    @Override
                    public long getContentLength() {
                        return -1;
                    }

                    @Override
                    public InputStream getContent() throws UnsupportedOperationException {
                        return null;
                    }

                    @Override
                    public void writeTo(OutputStream outstream) throws IOException {
                        int l = 0;
                        int i = 0;
                        while (i <= message.length()) {
                            i += random.nextInt(1000);
                            i = Math.min(i, message.length());
                            outstream.write(message.getBytes(), l, i - l);
                            l = i;
                            ++i;
                        }
                    }

                    @Override
                    public boolean isStreaming() {
                        return false;
                    }
                });
                client.execute(post, result -> {
                    Assert.assertEquals(StatusCodes.OK, result.getCode());
                    return HttpClientUtils.readResponse(result);
                });
            }
        }
    }

    @Test
    @Ignore("sometimes the client attempts to re-use the same connection after the failure, but the server has already closed it")
    public void testMaxRequestSizeChunkedRequest() throws IOException {
        connection = null;
        OptionMap existing = DefaultServer.getUndertowOptions();
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        post.setHeader(HttpHeaders.CONNECTION, "close");
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            generateMessage(1);
            post.setEntity(new AbstractHttpEntity("", null, true) {
                @Override
                public void close() {
                }

                @Override
                public long getContentLength() {
                    return -1;
                }

                @Override
                public InputStream getContent() throws UnsupportedOperationException {
                    return null;
                }

                @Override
                public void writeTo(OutputStream outstream) throws IOException {
                    outstream.write(message.getBytes());
                    outstream.flush();
                }

                @Override
                public boolean isStreaming() {
                    return false;
                }
            });
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, 3L));
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getCode());
                return HttpClientUtils.readResponse(result);
            });
            connection = null;
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, (long) message.length()));
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                return HttpClientUtils.readResponse(result);
            });
        } finally {
            DefaultServer.setUndertowOptions(existing);
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
