package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
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

    @Test
    public void simpleGracefulShutdownTestCase() throws IOException, InterruptedException {
        final AtomicReference<CountDownLatch> otherLatch = new AtomicReference<CountDownLatch>();
        final AtomicReference<CountDownLatch> latchAtomicReference = new AtomicReference<CountDownLatch>();

        GracefulShutdownHandler shutdown = Handlers.shutdown(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                final CountDownLatch countDownLatch = latchAtomicReference.get();
                final CountDownLatch latch = otherLatch.get();
                if(latch != null) {
                    latch.countDown();
                }
                if (countDownLatch != null) {
                    countDownLatch.await();
                }
            }
        });
        DefaultServer.setRootHandler(shutdown);

        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            shutdown.shutdown();

            result = client.execute(get);
            Assert.assertEquals(503, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            shutdown.start();

            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            CountDownLatch latch = new CountDownLatch(1);
            latchAtomicReference.set(latch);

            otherLatch.set(new CountDownLatch(1));
            Thread t = new Thread(new RequestTask());
            t.start();
            otherLatch.get().await();
            shutdown.shutdown();

            Assert.assertFalse(shutdown.awaitShutdown(10));

            latch.countDown();

            Assert.assertTrue(shutdown.awaitShutdown(10000));

        } finally {
            client.getConnectionManager().shutdown();
        }

    }

    private final class RequestTask implements Runnable {

        @Override
        public void run() {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            TestHttpClient client = new TestHttpClient();
            try {
                HttpResponse result = client.execute(get);
                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
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
