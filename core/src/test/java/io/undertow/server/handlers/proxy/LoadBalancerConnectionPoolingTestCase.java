package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(DefaultServer.class)
public class LoadBalancerConnectionPoolingTestCase {

    public static final int TARGET_PORT = 18787;
    private static ChannelConnectCloseHttpHandler target = new ChannelConnectCloseHttpHandler();
    private static Undertow undertow;

    @BeforeClass
    public static void before() throws Exception {
        target.start(TARGET_PORT);

        ProxyHandler proxyHandler = new ProxyHandler(new LoadBalancingProxyClient()
                .setConnectionsPerThread(10)
                .setSoftMaxConnectionsPerThread(2)
                .setTtl(200)
                .addHost(new URI("http", null, "localhost", TARGET_PORT, null, null, null), "s1")
                , 10000, ResponseCodeHandler.HANDLE_404);

        // Default server uses 8 io threads which is hard to test against
        undertow = Undertow.builder()
                .setIoThreads(2)
                .addHttpListener(8888, "localhost")
                .setHandler(proxyHandler)
                .build();
        undertow.start();
    }

    @AfterClass
    public static void after() {
        target.shutdown();
        undertow.stop();
    }

    @Test
    public void shouldReduceConnectionPool() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        PoolingClientConnectionManager conman = new PoolingClientConnectionManager();
        conman.setDefaultMaxPerRoute(20);
        final TestHttpClient client = new TestHttpClient(conman);
        int requests = 1000;
        final CountDownLatch latch = new CountDownLatch(requests);
        try {
            for (int i = 0; i < requests; ++i) {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        HttpGet get = new HttpGet("http://localhost:8888");
                        try {
                            client.execute(get, new ResponseHandler<HttpResponse>() {
                                @Override
                                public HttpResponse handleResponse(HttpResponse response) throws IOException {
                                    latch.countDown();
                                    Assert.assertEquals(StatusCodes.OK, response.getStatusLine().getStatusCode());
                                    return response;
                                }
                            });
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            latch.await(2000, TimeUnit.MILLISECONDS);
        } finally {
            executorService.shutdownNow();
            client.getConnectionManager().shutdown();
        }

        Assert.assertEquals(10, target.getConnectionCount());
        Thread.sleep(2000);
        Assert.assertEquals(2, target.getConnectionCount());
    }
}
