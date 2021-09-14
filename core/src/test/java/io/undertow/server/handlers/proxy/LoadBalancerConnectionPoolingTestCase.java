package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
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

        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                final ServerConnection con = exchange.getConnection();
                if(!activeConnections.contains(con)) {
                    System.out.println("added " + con);
                    activeConnections.add(con);
                    con.addCloseListener(new ServerConnection.CloseListener() {
                        @Override
                        public void closed(ServerConnection connection) {
                            System.out.println("Closed " + connection);
                            activeConnections.remove(connection);
                        }
                    });
                }
            }
        });
    }

    @AfterClass
    public static void after() {
        undertow.stop();
        // sleep 1 s to prevent BindException (Address already in use) when running the CI
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {}
    }

    @Test
    public void shouldReduceConnectionPool() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        PoolingClientConnectionManager conman = new PoolingClientConnectionManager();
        conman.setDefaultMaxPerRoute(20);
        final TestHttpClient client = new TestHttpClient(conman);
        int requests = 20;
        final CountDownLatch latch = new CountDownLatch(requests);
        long ttlStartExpire = TTL + System.currentTimeMillis();
        try {
            for (int i = 0; i < requests; ++i) {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        HttpGet get = new HttpGet("http://" + host + ":" + (port + 1));
                        try {
                            HttpResponse response = client.execute(get);
                            Assert.assertEquals(StatusCodes.OK, response.getStatusLine().getStatusCode());
                            HttpClientUtils.readResponse(response);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }
            if(!latch.await(2000, TimeUnit.MILLISECONDS)) {
                Assert.fail();
            }
        } finally {
            client.getConnectionManager().shutdown();
            executorService.shutdown();
        }

        if(activeConnections.size() != 1) {
            //if the test is slow this line could be hit after the expire time
            //uncommon, but we guard against it to prevent intermittent failures
            if(System.currentTimeMillis() < ttlStartExpire) {
                Assert.fail("there should still be a connection");
            }
        }
        long end = System.currentTimeMillis() + (TTL * 3);
        while (!activeConnections.isEmpty() && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        Assert.assertEquals(0, activeConnections.size());
    }
}
