/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.channels.ReadTimeoutException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * Tests blocking read timeout with a slow request
 *
 * @author Carter Kozak
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class BlockingReadTimeoutHandlerTestCase {

    private static final OutputStream STUB_OUTPUT_STREAM = new OutputStream() {
        @Override
        public void write(byte[] var1, int var2, int var3) throws IOException {

        }

        @Override
        public void write(int b) throws IOException {

        }
    };

    private volatile Exception exception;
    private static final CountDownLatch errorLatch = new CountDownLatch(1);

    @Test
    public void testReadTimeout() throws InterruptedException {
        DefaultServer.setRootHandler(BlockingReadTimeoutHandler.builder().nextHandler(new BlockingHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                try {
                    IOUtils.copyLarge(exchange.getInputStream(), STUB_OUTPUT_STREAM);
                    exchange.getOutputStream().write("COMPLETED".getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    exception = e;
                    errorLatch.countDown();
                }
            }
        })).readTimeout(Duration.ofMillis(1)).build());

        final TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL());
            post.setEntity(new AbstractHttpEntity() {

                @Override
                public InputStream getContent() throws IOException, IllegalStateException {
                    return null;
                }

                @Override
                public void writeTo(final OutputStream outstream) throws IOException {
                    for (int i = 0; i < 5; ++i) {
                        outstream.write('*');
                        outstream.flush();
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                @Override
                public boolean isStreaming() {
                    return true;
                }

                @Override
                public boolean isRepeatable() {
                    return false;
                }

                @Override
                public long getContentLength() {
                    return 5;
                }
            });
            post.addHeader(Headers.CONNECTION_STRING, "close");
            try {
                client.execute(post);
            } catch (IOException e) {

            }
            if (errorLatch.await(5, TimeUnit.SECONDS)) {
                Assert.assertEquals(ReadTimeoutException.class, exception.getClass());
            } else {
                Assert.fail("Read did not time out");
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
