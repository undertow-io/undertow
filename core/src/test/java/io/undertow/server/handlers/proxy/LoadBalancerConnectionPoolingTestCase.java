package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.server.ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(DefaultServer.class)
@ProxyIgnore
public class LoadBalancerConnectionPoolingTestCase {

    public static final int TTL = 2000;
    private static Undertow undertow;

    private static final Set<ServerConnection> activeConnections = Collections.newSetFromMap(new ConcurrentHashMap<>());

    static final String host = DefaultServer.getHostAddress("default");
    static int port = DefaultServer.getHostPort("default");

    @BeforeClass
    public static void before() throws Exception {

        ProxyHandler proxyHandler = ProxyHandler.builder().setProxyClient(new LoadBalancingProxyClient()
                        .setConnectionsPerThread(1)
                        .setSoftMaxConnectionsPerThread(0)
                        .setTtl(TTL)
                        .setMaxQueueSize(1000)
                        .addHost(new URI("http", null, host, port, null, null, null), "s1"))
                .setMaxRequestTime(10000).build();

        // Default server uses 8 io threads which is hard to test against
        undertow = Undertow.builder()
                .setIoThreads(1)
                .addHttpListener(port + 1, host)
                .setHandler(proxyHandler)
                .build();
        undertow.start();

        DefaultServer.setRootHandler(exchange -> {
            final ServerConnection con = exchange.getConnection();
            if (!activeConnections.contains(con)) {
                System.out.println("added " + con);
                activeConnections.add(con);
                con.addCloseListener(connection -> {
                    System.out.println("Closed " + connection);
                    activeConnections.remove(connection);
                });
            }
        });
    }

    @AfterClass
    public static void after() {
        undertow.stop();
        // sleep 1 s to prevent BindException (Address already in use) when running the CI
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {
        }
    }

    @Test
    public void shouldReduceConnectionPool() throws Exception {
        PoolingHttpClientConnectionManager conman = PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnPerRoute(20).build();
        int requests = 20;
        final CountDownLatch latch = new CountDownLatch(requests);
        long ttlStartExpire = TTL + System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try (CloseableHttpClient client = TestHttpClient.custom().setConnectionManager(conman).build()) {
            for (int i = 0; i < requests; ++i) {
                executorService.submit(() -> {
                    HttpGet get = new HttpGet("http://" + host + ":" + (port + 1));
                    try {
                        client.execute(get, response -> {
                            Assert.assertEquals(StatusCodes.OK, response.getCode());
                            HttpClientUtils.readResponse(response);
                            return null;
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            if (!latch.await(2000, TimeUnit.MILLISECONDS)) {
                Assert.fail();
            }
        }

        if (activeConnections.size() != 1) {
            //if the test is slow this line could be hit after the expire time
            //uncommon, but we guard against it to prevent intermittent failures
            if (System.currentTimeMillis() < ttlStartExpire) {
                Assert.fail("there should still be a connection");
            }
        }
        long end = System.currentTimeMillis() + (TTL * 30);
        while (!activeConnections.isEmpty() && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        Assert.assertEquals(0, activeConnections.size());
    }
}
