/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.undertow.server.RequestStatistics;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DefaultServer.class)
public class ActiveRequestTrackerTest {
    @Test
    public void testRequestTracking() throws InterruptedException {
        final TestHttpClient client = new TestHttpClient();
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final ActiveRequestTrackerHandler handler = new ActiveRequestTrackerHandler(new DelayHandler(latch), null);
            DefaultServer.setRootHandler(handler);

            // start the thread doing the request
            final Thread t = new Thread(() -> makeRequest(client));
            t.start();

            List<RequestStatistics> trackedRequests = handler.getTrackedRequests();

            // This test is somewhat time-sensitive in that an in-flight request is needed to verify that the handler
            // is correctly tracking and returning active requests. To that end, the test submits a request in a
            // Thread (above) to let the request be made in a background thread while the main thread attempts to
            // get the active request information. The delay handler waits for termination via latch and
            // meanwhile this thread tries to retrieve the request statistics. There is a max waiting time of 5 seconds.
            try {
                while (trackedRequests.isEmpty() && t.isAlive()) {
                    TimeUnit.MILLISECONDS.sleep(100);
                    trackedRequests = handler.getTrackedRequests();
                }
            } finally {
                // finish request thread via latch once the tracked requests are set
                latch.countDown();
            }

            Assert.assertEquals("Expecting 1 tracked request", 1, trackedRequests.size());

            RequestStatistics request = trackedRequests.get(0);
            Assert.assertNotNull(request.getUri());
            Assert.assertNotNull(request.getMethod());
            Assert.assertNotNull(request.getProtocol());
            Assert.assertNotNull(request.getQueryString());
            Assert.assertNotNull(request.getRemoteAddress());
            Assert.assertNotSame(0, request.getBytesReceived());
            Assert.assertNotSame(0, request.getBytesSent());
            Assert.assertNotSame(0, request.getStartTime());
            Assert.assertNotSame(0, request.getProcessingTime());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void makeRequest(TestHttpClient client) {
        try {
            client.execute(new HttpGet(DefaultServer.getDefaultServerURL()));
        } catch (IOException e) {
            // ...
        }
    }

    private class DelayHandler implements HttpHandler {

        private final CountDownLatch latch;

        DelayHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            latch.await(5, TimeUnit.SECONDS);
        }
    }
}
