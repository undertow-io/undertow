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

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.jboss.logging.Logger;
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
public class FixedLengthRequestTestCase {

    private static final Logger log = Logger.getLogger(FixedLengthRequestTestCase.class);

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    private static volatile HttpServerConnection connection;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new BlockingHttpHandler() {
            @Override
            public void handleRequest(final BlockingHttpServerExchange exchange) {
                try {
                    if (connection == null) {
                        connection = exchange.getExchange().getConnection();
                    } else if (!DefaultServer.isAjp() && connection.getChannel() != exchange.getExchange().getConnection().getChannel()) {
                        exchange.getExchange().setResponseCode(500);
                        exchange.getOutputStream().write("Connection not persistent".getBytes());
                        exchange.getOutputStream().close();
                        return;
                    }
                    String m = HttpClientUtils.readResponse(exchange.getInputStream());
                    Assert.assertEquals(message, m);
                    exchange.getInputStream().close();
                    exchange.getOutputStream().close();
                } catch (IOException e) {
                    exchange.getExchange().getResponseHeaders().put(Headers.CONNECTION, "close");
                    exchange.getExchange().setResponseCode(500);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testFixedLengthRequest() throws IOException {
        connection = null;
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            generateMessage(1);
            post.setEntity(new StringEntity(message));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            generateMessage(1000);
            post.setEntity(new StringEntity(message));
            result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
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
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/path");
        post.setHeader(HttpHeaders.CONNECTION, "close");
        TestHttpClient client = new TestHttpClient();
        try {
            generateMessage(1);
            post.setEntity(new StringEntity(message));
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, 3l));
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
