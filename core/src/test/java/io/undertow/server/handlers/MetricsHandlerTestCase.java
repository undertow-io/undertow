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
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MetricsHandlerTestCase {

    @Test
    public void testMetrics() throws IOException, InterruptedException {

        MetricsHandler metricsHandler;
        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(metricsHandler = new MetricsHandler(exchange -> {
            Thread.sleep(100);
            if (exchange.getQueryString().contains("error")) {
                throw new RuntimeException();
            }
            exchange.getResponseSender().send("Hello");
        })));
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
                return null;
            });
            latchHandler.await();
            latchHandler.reset();

            MetricsHandler.MetricResult metrics = metricsHandler.getMetrics();
            Assert.assertEquals(1, metrics.getTotalRequests());
            Assert.assertTrue(metrics.getMaxRequestTime() > 0);
            Assert.assertEquals(metrics.getMinRequestTime(), metrics.getMaxRequestTime());
            Assert.assertEquals(metrics.getMaxRequestTime(), metrics.getTotalRequestTime());

            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
                return null;
            });

            latchHandler.await();
            latchHandler.reset();

            metrics = metricsHandler.getMetrics();
            Assert.assertEquals(2, metrics.getTotalRequests());
            Assert.assertEquals(0, metrics.getTotalErrors());


            client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/path?error=true"), result -> {
                Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getCode());
                return HttpClientUtils.readResponse(result);
            });

            latchHandler.await();
            latchHandler.reset();

            metrics = metricsHandler.getMetrics();
            Assert.assertEquals(3, metrics.getTotalRequests());
            Assert.assertEquals(1, metrics.getTotalErrors());
        }
    }
}
