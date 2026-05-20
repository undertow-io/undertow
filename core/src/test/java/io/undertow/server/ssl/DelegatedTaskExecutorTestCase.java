/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package io.undertow.server.ssl;


import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Carter Kozak
 */
public class DelegatedTaskExecutorTestCase {

    @Test
    public void testDelegatedTaskExecutorIsUsed() throws Exception {
        ExecutorService delegatedTaskExecutor = Executors.newSingleThreadExecutor();
        AtomicInteger counter = new AtomicInteger();
        Undertow undertow = Undertow.builder()
                .addHttpsListener(0, null, DefaultServer.getServerSslContext())
                .setSslEngineDelegatedTaskExecutor(task -> {
                    counter.getAndIncrement();
                    delegatedTaskExecutor.execute(task);
                })
                .setHandler(ResponseCodeHandler.HANDLE_200)
                .build();

        undertow.start();
        int port = port(undertow);
        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            client.execute(new HttpGet("https://localhost:" + port), response -> {
                assertEquals(200, response.getCode());
                assertTrue("expected interactions with the delegated task executor", counter.get() > 0);
                return null;
            });
        } finally {
            undertow.stop();
            List<Runnable> tasks = delegatedTaskExecutor.shutdownNow();
            for (Runnable task : tasks) {
                task.run();
            }
            assertTrue(
                    "ExecutorService did not shut down in time",
                    delegatedTaskExecutor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testRejection() throws IOException {
        Undertow undertow = Undertow.builder()
                .addHttpsListener(0, null, DefaultServer.getServerSslContext())
                .setSslEngineDelegatedTaskExecutor(ignoredTask -> {
                    throw new RejectedExecutionException();
                })
                .setHandler(ResponseCodeHandler.HANDLE_200)
                .build();

        undertow.start();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            int port = port(undertow);
            HttpGet request = new HttpGet("https://localhost:" + port);
            try {
                client.execute(request, r -> null);
                fail("Expected an exception");
            } catch (SSLHandshakeException | HttpHostConnectException exception) {
                // expected one of:
                // - Remote host closed connection during handshake
                // - Remote host terminated the handshake
                // - Remote host refused connection (happens when client tries IPv6 after IPv4 failed)
                // This exception comes from the jvm and may change in future
                // releases so we don't verify an exact match.
                String message = exception.getMessage();
                System.out.println(message);
                assertTrue(
                        "message was: " + message,
                        message != null && (message.contains("closed") || message.contains("terminated") ||
                                message.contains("Connection refused")));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        } finally {
            undertow.stop();
            // sleep 1 s to prevent BindException (Address already in use) when running the CI
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        }
    }

    private static int port(Undertow undertow) {
        if (undertow.getListenerInfo().size() != 1) {
            throw new IllegalStateException("Expected exactly one listener");
        }
        InetSocketAddress address = (InetSocketAddress) undertow.getListenerInfo().get(0).getAddress();
        return address.getPort();
    }
}
