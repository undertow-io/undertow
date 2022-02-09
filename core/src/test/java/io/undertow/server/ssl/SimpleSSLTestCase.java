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

package io.undertow.server.ssl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleSSLTestCase {

    // The concurrency is aligned to the #CPUs*8 up to a max of 32 threads
    private static final int CONCURRENCY = Math.min(32, Runtime.getRuntime().availableProcessors() * 8);
    private static final int REQUESTS_PER_THREAD = 300;

    @Test
    public void simpleSSLTestCase() throws IOException, GeneralSecurityException {

        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                exchange.endExchange();
            }
        });

        DefaultServer.startSSLServer();
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders("scheme");
            Assert.assertEquals("https", header[0].getValue());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }


    @Test
    public void testNonPersistentConnections() throws IOException, GeneralSecurityException {

        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                exchange.getResponseHeaders().put(Headers.CONNECTION, "close");
                exchange.endExchange();
            }
        });

        DefaultServer.startSSLServer();
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            for(int i = 0; i <5; ++ i) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                Header[] header = result.getHeaders("scheme");
                Assert.assertEquals("https", header[0].getValue());
                HttpClientUtils.readResponse(result);

            }
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void parallel() throws Exception {
        runTest(CONCURRENCY, new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                exchange.endExchange();
            }
        });
    }

    @Test
    public void parallelWithDispatch() throws Exception {
        runTest(CONCURRENCY, new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.dispatch(() -> {
                    exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                    exchange.endExchange();
                });
            }
        });
    }

    @Test
    public void parallelWithBlockingDispatch() throws Exception {
        runTest(CONCURRENCY, new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                if (exchange.isInIoThread()) {
                    exchange.dispatch(this);
                    return;
                }
                exchange.startBlocking();
                exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                exchange.endExchange();
            }
        });
    }

    private void runTest(int concurrency, HttpHandler handler) throws IOException, InterruptedException {
        DefaultServer.setRootHandler(handler);
        DefaultServer.startSSLServer();
        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        try (CloseableHttpClient client = HttpClients.custom().disableConnectionState()
                .setSSLContext(DefaultServer.getClientSSLContext())
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(60000).build())
                .setMaxConnPerRoute(1000)
                .build()) {
            AtomicBoolean failed = new AtomicBoolean();
            AtomicInteger processed = new AtomicInteger(0);
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    if (failed.get()) {
                        return;
                    }
                    try (CloseableHttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerSSLAddress()))) {
                        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                        Header[] header = result.getHeaders("scheme");
                        Assert.assertEquals("https", header[0].getValue());
                        EntityUtils.consumeQuietly(result.getEntity());
                        processed.incrementAndGet();
                    } catch (Throwable t) {
                        if (failed.compareAndSet(false, true)) {
                            t.printStackTrace();
                        }
                    }
                }
            };
            for (int i = 0; i < concurrency * REQUESTS_PER_THREAD; i++) {
                executorService.submit(task);
            }
            executorService.shutdown();
            int executedPrevTime = 0;
            while (!executorService.awaitTermination(10, TimeUnit.SECONDS) && !failed.get()) {
                int executed = processed.get();
                if (executedPrevTime == executed) {
                    failed.set(true);
                    Assert.fail("Executions hanged at " + executed);
                }
                executedPrevTime = executed;
            }
            Assert.assertFalse("A task failed! Check the stack-trace in the output file", failed.get());
            Assert.assertTrue(executorService.isTerminated());
        } finally {
            DefaultServer.stopSSLServer();
            if (!executorService.isTerminated()) {
                executorService.shutdownNow();
            }
        }
    }

}
