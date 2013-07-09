package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MetricsHandlerTestCase {

    private static MetricsHandler metricsHandler;
    private static CompletionLatchHandler latchHandler;
    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(metricsHandler = new MetricsHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                Thread.sleep(100);
                exchange.getResponseSender().send("Hello");
            }
        })));
    }

    @Test
    public void testMetrics() throws IOException, InterruptedException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            latchHandler.reset();

            MetricsHandler.MetricResult metrics = metricsHandler.getMetrics();
            Assert.assertEquals(1, metrics.getTotalRequests());
            Assert.assertTrue(metrics.getMaxRequestTime() > 0);
            Assert.assertEquals(metrics.getMinRequestTime(), metrics.getMaxRequestTime());
            Assert.assertEquals(metrics.getMaxRequestTime(), metrics.getTotalRequestTime());

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));

            latchHandler.await();
            latchHandler.reset();

            metrics = metricsHandler.getMetrics();
            Assert.assertEquals(2, metrics.getTotalRequests());

        } finally {

            client.getConnectionManager().shutdown();
        }
    }
}
