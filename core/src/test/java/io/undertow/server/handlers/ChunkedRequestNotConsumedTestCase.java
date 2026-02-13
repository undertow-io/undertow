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

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 *
 * See https://issues.jboss.org/browse/UNDERTOW-1011
 *
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ChunkedRequestNotConsumedTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(exchange -> {
            exchange.setResponseContentLength("message".length());
            exchange.getResponseSender().send("message", new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    exchange.dispatch(SameThreadExecutor.INSTANCE, () -> exchange.getIoThread()
                            .executeAfter(exchange::endExchange, 300, TimeUnit.MILLISECONDS));
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    exchange.endExchange();
                }
            });
        });
    }

    @Test
    public void testChunkedRequestNotConsumed() throws IOException {
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final Random random = new Random();
            final int seed = random.nextInt();
            System.out.print("Using Seed " + seed);
            random.setSeed(seed);


            for (int i = 0; i < 3; ++i) {
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
                        outstream.flush();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {

                        }
                        outstream.write(MESSAGE.getBytes(StandardCharsets.US_ASCII));
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
}
