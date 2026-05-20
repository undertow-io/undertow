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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.channels.WriteTimeoutException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests blocking write timeout with a client that is slow to read the response
 *
 * @author Carter Kozak
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
@ProxyIgnore
public class BlockingWriteTimeoutHandlerTestCase {

    private volatile Exception exception;
    private static final CountDownLatch errorLatch = new CountDownLatch(1);

    @Test
    public void testWriteTimeout() throws InterruptedException, IOException {
        DefaultServer.setRootHandler(BlockingWriteTimeoutHandler.builder().nextHandler(new BlockingHandler(exchange -> {
            final int capacity = 1 * 1024 * 1024; // 1mb

            final byte[] data = new byte[capacity];
            for (int i = 0; i < capacity; ++i) {
                data[i] = (byte) '*';
            }

            try {
                // Must write enough data that it's not buffered
                for (int i = 0; i < 20; i++) {
                    exchange.getOutputStream().write(data);
                }
            } catch (IOException e) {
                exception = e;
                errorLatch.countDown();
            }
        })).writeTimeout(Duration.ofMillis(1)).build());

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            try {
                client.execute(get, result -> {
                    Assert.assertFalse("The result entity is buffered", result.getEntity().isRepeatable());
                    InputStream content = result.getEntity().getContent();
                    byte[] buffer = new byte[512];
                    int r = 0;
                    while ((r = content.read(buffer)) > 0) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        if (exception != null) {
                            Assert.assertEquals(WriteTimeoutException.class, exception.getClass());
                            return result;
                        }
                    }
                    Assert.fail("Write did not time out");
                    return null;
                });
            } catch (IOException e) {
                if (errorLatch.await(5, TimeUnit.SECONDS)) {
                    Assert.assertEquals(WriteTimeoutException.class, exception.getClass());
                } else {
                    Assert.fail("Write did not time out");
                }
            }
        }
    }
}
