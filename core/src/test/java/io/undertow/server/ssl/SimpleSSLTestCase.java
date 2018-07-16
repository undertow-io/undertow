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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleSSLTestCase {

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
        runTest(32, new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                exchange.endExchange();
            }
        });
    }

    @Test
    public void parallelWithDispatch() throws Exception {
        runTest(32, new HttpHandler() {
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
        runTest(32, new HttpHandler() {
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
        try (CloseableHttpClient client = HttpClients.custom().disableConnectionState()
                .setSSLContext(DefaultServer.getClientSSLContext())
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(5000).build())
                .setMaxConnPerRoute(1000)
                .build()) {
            ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
            AtomicBoolean failed = new AtomicBoolean();
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
                    } catch (Throwable t) {
                        if (failed.compareAndSet(false, true)) {
                            t.printStackTrace();
                            executorService.shutdownNow();
                        }
                    }
                }
            };
            for (int i = 0; i < concurrency * 300; i++) {
                executorService.submit(task);
            }
            executorService.shutdown();
            Assert.assertTrue(executorService.awaitTermination(70, TimeUnit.SECONDS));
            Assert.assertFalse(failed.get());
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

}
