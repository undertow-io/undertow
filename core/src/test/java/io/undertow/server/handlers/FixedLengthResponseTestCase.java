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

import io.undertow.io.Sender;
import io.undertow.server.ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests that persistent connections work with fixed length responses
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FixedLengthResponseTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    private static volatile ServerConnection connection;

    @Before
    public void reset() {
        connection = null;
    }

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(exchange -> {
            if (connection == null) {
                connection = exchange.getConnection();
            } else if (!DefaultServer.isAjp() && !DefaultServer.isProxy() && connection != exchange.getConnection()) {
                Sender sender = exchange.getResponseSender();
                sender.send("Connection not persistent");
                return;
            }
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
            final Sender sender = exchange.getResponseSender();
            sender.send(message);
        });
    }

    @Test
    public void sendHttpRequest() throws IOException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            generateMessage(1);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(message, HttpClientUtils.readResponse(result));
                return null;
            });

            generateMessage(1000);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(message, HttpClientUtils.readResponse(result));
                return null;
            });
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
