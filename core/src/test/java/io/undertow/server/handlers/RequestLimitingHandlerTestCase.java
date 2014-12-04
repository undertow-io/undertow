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

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RequestLimitingHandlerTestCase {

    public static final int N_THREADS = 10;
    private static volatile CountDownLatch latch = new CountDownLatch(1);

    static final AtomicInteger count = new AtomicInteger();

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new BlockingHandler(Handlers.requestLimitingHandler(2, N_THREADS, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                int res = count.incrementAndGet();
                try {
                    if (!latch.await(20, TimeUnit.SECONDS)) {
                        exchange.setResponseCode(500);
                    } else {
                        exchange.getOutputStream().write(("" + res).getBytes("US-ASCII"));
                    }
                } finally {
                    count.decrementAndGet();
                }
            }
        })));

    }


    @Test
    public void testRateLimitingHandler() throws ExecutionException, InterruptedException {
        latch.countDown();
        latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        try {
            final List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < N_THREADS; ++i) {
                futures.add(executor.submit(new Callable<String>() {
                    @Override
                    public String call() {
                        TestHttpClient client = new TestHttpClient();
                        try {
                            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
                            HttpResponse result = client.execute(get);
                            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                            return HttpClientUtils.readResponse(result);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            client.getConnectionManager().shutdown();
                        }
                    }
                }));
            }
            Thread.sleep(300);
            latch.countDown();
            for (Future<?> future : futures) {
                String res = (String) future.get();
                Assert.assertTrue(res, res.equals("1") || res.equals("2"));
            }
        } finally {
            executor.shutdown();
        }

    }


    @Test
    public void testRateLimitingHandlerQueueFull() throws ExecutionException, InterruptedException {
        latch.countDown();
        latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(N_THREADS * 2);
        try {
            final List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < N_THREADS * 2; ++i) {
                futures.add(executor.submit(new Callable<String>() {
                    @Override
                    public String call() {
                        TestHttpClient client = new TestHttpClient();
                        try {
                            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
                            HttpResponse result = client.execute(get);
                            if(result.getStatusLine().getStatusCode() == 513) {
                                return "513";
                            }
                            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                            return HttpClientUtils.readResponse(result);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            client.getConnectionManager().shutdown();
                        }
                    }
                }));
            }
            Thread.sleep(300);
            latch.countDown();
            for (Future<?> future : futures) {
                String res = (String) future.get();
                Assert.assertTrue(res, res.equals("1") || res.equals("2") || res.equals("513"));
            }
            futures.clear();
            for (int i = 0; i < 2; ++i) {
                futures.add(executor.submit(new Callable<String>() {
                    @Override
                    public String call() {
                        TestHttpClient client = new TestHttpClient();
                        try {
                            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
                            HttpResponse result = client.execute(get);
                            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                            return HttpClientUtils.readResponse(result);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            client.getConnectionManager().shutdown();
                        }
                    }
                }));
            }

            for (Future<?> future : futures) {
                String res = (String) future.get();
                Assert.assertTrue(res, res.equals("1") || res.equals("2"));
            }

        } finally {
            executor.shutdown();
        }

    }

}
