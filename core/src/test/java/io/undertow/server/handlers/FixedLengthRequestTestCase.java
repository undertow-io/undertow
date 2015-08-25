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
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FixedLengthRequestTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    private static volatile ServerConnection connection;

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
                    } else if (!DefaultServer.isAjp()  && !DefaultServer.isProxy() && connection != exchange.getConnection()) {
                        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                        final OutputStream outputStream = exchange.getOutputStream();
                        outputStream.write("Connection not persistent".getBytes());
                        outputStream.close();
                        return;
                    }
                    final OutputStream outputStream = exchange.getOutputStream();
                    final InputStream inputStream =  exchange.getInputStream();
                    String m = HttpClientUtils.readResponse(inputStream);
                    Assert.assertEquals(message, m);
                    inputStream.close();
                    outputStream.close();
                } catch (IOException e) {
                    exchange.getResponseHeaders().put(Headers.CONNECTION, "close");
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testFixedLengthRequest() throws IOException {
        connection = null;
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            generateMessage(1);
            post.setEntity(new StringEntity(message));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            generateMessage(1000);
            post.setEntity(new StringEntity(message));
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {

            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @Ignore("sometimes the client attempts to re-use the same connection after the failure, but the server has already closed it")
    public void testMaxRequestSizeFixedLengthRequest() throws IOException {
        connection = null;
        OptionMap existing = DefaultServer.getUndertowOptions();
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        post.setHeader(HttpHeaders.CONNECTION, "close");
        TestHttpClient client = new TestHttpClient();
        try {
            generateMessage(1);
            post.setEntity(new StringEntity(message));
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, 3L));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            connection = null;
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, (long) message.length()));
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
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
