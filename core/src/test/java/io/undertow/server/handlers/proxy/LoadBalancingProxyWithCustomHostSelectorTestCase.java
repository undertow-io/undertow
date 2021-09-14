package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.client.UndertowClient;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.Options;

import java.net.URI;
import java.net.URISyntaxException;

import static io.undertow.Handlers.jvmRoute;
import static io.undertow.Handlers.path;

@RunWith(DefaultServer.class)
public class LoadBalancingProxyWithCustomHostSelectorTestCase {

    protected static Undertow server1;
    protected static Undertow server2;

    @BeforeClass
    public static void setup() throws URISyntaxException {
        final SessionCookieConfig sessionConfig = new SessionCookieConfig();
        int port = DefaultServer.getHostPort("default");
        server1 = Undertow.builder()
                .addHttpListener(port + 1, DefaultServer.getHostAddress("default"))
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(jvmRoute("JSESSIONID", "s1", path()
                        .addPrefixPath("/session", new SessionAttachmentHandler(new AbstractLoadBalancingProxyTestCase.SessionTestHandler(sessionConfig), new InMemorySessionManager(""), sessionConfig))
                        .addPrefixPath("/name", new AbstractLoadBalancingProxyTestCase.StringSendHandler("server1"))))
                .build();

        server2 = Undertow.builder()
                .addHttpListener(port + 2, DefaultServer.getHostAddress("default"))
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(jvmRoute("JSESSIONID", "s2", path()
                        .addPrefixPath("/session", new SessionAttachmentHandler(new AbstractLoadBalancingProxyTestCase.SessionTestHandler(sessionConfig), new InMemorySessionManager(""), sessionConfig))
                        .addPrefixPath("/name", new AbstractLoadBalancingProxyTestCase.StringSendHandler("server2"))))
                .build();
        server1.start();
        server2.start();

        LoadBalancingProxyClient.HostSelector hostSelector = new LoadBalancingProxyClient.HostSelector() {
            @Override
            public int selectHost(LoadBalancingProxyClient.Host[] availableHosts) {
                return 0;
            }
        };

        DefaultServer.setRootHandler(ProxyHandler.builder().setProxyClient(new LoadBalancingProxyClient(UndertowClient.getInstance(), null, hostSelector)
                .setConnectionsPerThread(4)
                .addHost(new URI("http", null, DefaultServer.getHostAddress("default"), port + 1, null, null, null), "s1")
                .addHost(new URI("http", null, DefaultServer.getHostAddress("default"), port + 2, null, null, null), "s2"))
                .setMaxRequestTime(10000)
                .setMaxConnectionRetries(2).build());
    }

    @AfterClass
    public static void teardown() {
        server1.stop();
        server2.stop();
        // sleep 1 s to prevent BindException (Address already in use) when running the CI
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {}
    }

    // https://issues.jboss.org/browse/UNDERTOW-289
    @Test
    public void testDistributeLoadToGivenHost() throws Throwable {
        final StringBuilder resultString = new StringBuilder();

        for (int i = 0; i < 6; ++i) {
            TestHttpClient client = new TestHttpClient();
            try {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                resultString.append(HttpClientUtils.readResponse(result));
                resultString.append(' ');
            } finally {
                client.getConnectionManager().shutdown();
            }
        }
        Assert.assertTrue(resultString.toString().contains("server1"));
        Assert.assertFalse(resultString.toString().contains("server2"));
    }
}
