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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class HttpContinueAcceptingHandlerTestCase {

    private static volatile boolean accept = false;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        final HttpContinueAcceptingHandler handler = new HttpContinueAcceptingHandler(blockingHandler, value -> accept);
        DefaultServer.setRootHandler(handler);
        blockingHandler.setRootHandler(exchange -> {
            try {
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
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Before
    public void before() {
        Assume.assumeFalse(DefaultServer.isAjp());
        Assume.assumeFalse(DefaultServer.isH2upgrade());
    }

    @Test
    public void testHttpContinueRejected() throws IOException {
        accept = false;
        String message = "My HTTP Request!";
        try (CloseableHttpClient client = TestHttpClient.custom()
                .setRequestExecutor(new HttpRequestExecutor(Http1Config.custom()
                        .setWaitForContinueTimeout(Timeout.INFINITE)
                        .build(),
                        null,
                        null)).build()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.EXPECTATION_FAILED, result.getCode());
                return null;
            });
        }
    }

    @Test
    public void testHttpContinueAccepted() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        try (CloseableHttpClient client = TestHttpClient.custom()
                .setRequestExecutor(new HttpRequestExecutor(Http1Config.custom()
                        .setWaitForContinueTimeout(Timeout.INFINITE)
                        .build(),
                        null,
                        null)).build()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(message, HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }
}
