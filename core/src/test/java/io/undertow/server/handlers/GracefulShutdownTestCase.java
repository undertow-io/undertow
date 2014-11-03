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
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class GracefulShutdownTestCase {

    static final AtomicReference<CountDownLatch> latch1 = new AtomicReference<>();
    static final AtomicReference<CountDownLatch> latch2 = new AtomicReference<>();

    private static GracefulShutdownHandler shutdown;

    @BeforeClass
    public static void setup() {

        shutdown = Handlers.gracefulShutdown(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                final CountDownLatch countDownLatch = latch2.get();
                final CountDownLatch latch = latch1.get();
                if (latch != null) {
                    latch.countDown();
                }
                if (countDownLatch != null) {
                    countDownLatch.await();
                }
            }
        });
        DefaultServer.setRootHandler(shutdown);
    }

    @After
    public void after() {
        latch1.set(null);
        latch2.set(null);
        shutdown.start();
    }


    @Test
    public void simpleGracefulShutdownTestCase() throws IOException, InterruptedException {


        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            shutdown.shutdown();

            result = client.execute(get);
            Assert.assertEquals(StatusCodes.SERVICE_UNAVAILABLE, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            shutdown.start();

            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            CountDownLatch latch = new CountDownLatch(1);
            latch2.set(latch);

            latch1.set(new CountDownLatch(1));
            Thread t = new Thread(new RequestTask());
            t.start();
            latch1.get().await();
            shutdown.shutdown();

            Assert.assertFalse(shutdown.awaitShutdown(10));

            latch.countDown();

            Assert.assertTrue(shutdown.awaitShutdown(10000));

        } finally {
            client.getConnectionManager().shutdown();
        }

    }



    @Test
    public void gracefulShutdownListenerTestCase() throws IOException, InterruptedException {


        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            shutdown.shutdown();

            result = client.execute(get);
            Assert.assertEquals(StatusCodes.SERVICE_UNAVAILABLE, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            shutdown.start();

            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            CountDownLatch latch = new CountDownLatch(1);
            latch2.set(latch);


            latch1.set(new CountDownLatch(1));
            Thread t = new Thread(new RequestTask());
            t.start();
            latch1.get().await();

            ShutdownListener listener = new ShutdownListener();
            shutdown.shutdown();
            shutdown.addShutdownListener(listener);
            Assert.assertFalse(listener.invoked);

            latch.countDown();
            long end = System.currentTimeMillis() + 5000;
            while (!listener.invoked && System.currentTimeMillis() < end) {
                Thread.sleep(10);
            }
            Assert.assertTrue(listener.invoked);
        } finally {
            client.getConnectionManager().shutdown();
        }

    }

    private class ShutdownListener implements GracefulShutdownHandler.ShutdownListener {

        private volatile boolean invoked = false;

        @Override
        public synchronized void shutdown(boolean successful) {
            invoked = true;
        }
    }

    private final class RequestTask implements Runnable {

        @Override
        public void run() {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            TestHttpClient client = new TestHttpClient();
            try {
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                HttpClientUtils.readResponse(result);

            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                client.getConnectionManager().shutdown();
            }

        }
    }

}
