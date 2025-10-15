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

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringWriteChannelListener;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ChunkedResponseTransferCodingTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    private static volatile ServerConnection connection;

    @Before
    public void reset() {
        connection = null;
    }

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(Handlers.path().addExactPath("/path", new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                try {
                    if(connection == null) {
                        connection = exchange.getConnection();
                    } else if(!DefaultServer.isAjp() && !DefaultServer.isProxy() && connection != exchange.getConnection()){
                        final OutputStream outputStream = exchange.getOutputStream();
                        outputStream.write("Connection not persistent".getBytes());
                        outputStream.close();
                        return;
                    }
                    new StringWriteChannelListener(message).setup(exchange.getResponseChannel());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        })
                .addExactPath("/buffers", new BlockingHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        ByteBuffer[] buffers = new ByteBuffer[] {
                                ByteBuffer.wrap("prefix".getBytes(StandardCharsets.UTF_8)),
                                ByteBuffer.wrap("hello, ".getBytes(StandardCharsets.UTF_8)),
                                ByteBuffer.wrap("world".getBytes(StandardCharsets.UTF_8)),
                                ByteBuffer.wrap("suffix".getBytes(StandardCharsets.UTF_8))
                        };
                        StreamSinkChannel channel = exchange.getResponseChannel();
                        Channels.writeBlocking(channel, buffers, 1, 2);
                        channel.shutdownWrites();
                        Channels.flushBlocking(channel);
                    }
                })));
    }

    @Test
    public void testMultiBufferWrite() throws IOException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/buffers");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("hello, world", HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void sendHttpRequest() throws IOException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {

            generateMessage(0);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));

            generateMessage(1);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));

            generateMessage(1000);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));
        } finally {
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
