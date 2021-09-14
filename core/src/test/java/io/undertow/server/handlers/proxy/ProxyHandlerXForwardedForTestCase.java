package io.undertow.server.handlers.proxy;

import static io.undertow.Handlers.jvmRoute;
import static io.undertow.Handlers.path;

import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import org.xnio.Options;
import io.undertow.Undertow;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * Created by ivannagy on 8/26/14.
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
@ProxyIgnore
public class ProxyHandlerXForwardedForTestCase {

    protected static Undertow server;
    protected static int port;
    protected static int sslPort;
    protected static int handlerPort;
    protected static UndertowXnioSsl ssl;

    @BeforeClass
    public static void setup() throws Exception {

        port = DefaultServer.getHostPort("default");
        sslPort = port + 1;
        handlerPort = port + 2;

        DefaultServer.startSSLServer();
        ssl = new UndertowXnioSsl(DefaultServer.getWorker().getXnio(), OptionMap.EMPTY, DefaultServer.SSL_BUFFER_POOL, DefaultServer.getClientSSLContext());

        server = Undertow.builder()
                .addHttpsListener(handlerPort, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(jvmRoute("JSESSIONID", "s1", path().addPrefixPath("/x-forwarded", new XForwardedHandler())))
                .build();

        server.start();

    }

    @AfterClass
    public static void teardown() throws Exception {
        DefaultServer.stopSSLServer();
        server.stop();
        // sleep 1 s to prevent BindException (Address already in use) when running the CI
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {}

    }

    private static void setProxyHandler(boolean rewriteHostHeader, boolean reuseXForwarded) throws Exception {

        DefaultServer.setRootHandler(ProxyHandler.builder().setProxyClient((new LoadBalancingProxyClient()
                .setConnectionsPerThread(4)
                .addHost(new URI("https", null, DefaultServer.getHostAddress("default"), handlerPort, null, null, null), "s1", ssl)))
                .setMaxRequestTime(10000)
                .setRewriteHostHeader(rewriteHostHeader)
                .setReuseXForwarded(reuseXForwarded).build());

    }

    @Test
    public void testXForwarded() throws Exception {
        setProxyHandler(false, false);
        TestHttpClient client = new TestHttpClient();

        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/x-forwarded");

            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            Assert.assertEquals(port, Integer.parseInt(result.getFirstHeader(Headers.X_FORWARDED_PORT.toString()).getValue()));
            Assert.assertEquals("http", result.getFirstHeader(Headers.X_FORWARDED_PROTO.toString()).getValue());
            Assert.assertEquals(DefaultServer.getHostAddress(), result.getFirstHeader(Headers.X_FORWARDED_HOST.toString()).getValue());
            Assert.assertEquals(DefaultServer.getDefaultServerAddress().getAddress().getHostAddress(), result.getFirstHeader(Headers.X_FORWARDED_FOR.toString()).getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testXForwardedSsl() throws Exception {
        setProxyHandler(false, false);
        TestHttpClient client = new TestHttpClient();

        try {
            client.setSSLContext(DefaultServer.getClientSSLContext());
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/x-forwarded");

            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            Assert.assertEquals(sslPort, Integer.parseInt(result.getFirstHeader(Headers.X_FORWARDED_PORT.toString()).getValue()));
            Assert.assertEquals("https", result.getFirstHeader(Headers.X_FORWARDED_PROTO.toString()).getValue());
            Assert.assertEquals(DefaultServer.getHostAddress(), result.getFirstHeader(Headers.X_FORWARDED_HOST.toString()).getValue());
            Assert.assertEquals(DefaultServer.getDefaultServerAddress().getAddress().getHostAddress(), result.getFirstHeader(Headers.X_FORWARDED_FOR.toString()).getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testReuseXForwarded() throws Exception {
        setProxyHandler(false, true);
        TestHttpClient client = new TestHttpClient();

        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/x-forwarded");
            get.addHeader(Headers.X_FORWARDED_FOR.toString(), "50.168.245.32");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            Assert.assertEquals(port, Integer.parseInt(result.getFirstHeader(Headers.X_FORWARDED_PORT.toString()).getValue()));
            Assert.assertEquals("http", result.getFirstHeader(Headers.X_FORWARDED_PROTO.toString()).getValue());
            Assert.assertEquals(DefaultServer.getHostAddress(), result.getFirstHeader(Headers.X_FORWARDED_HOST.toString()).getValue());
            Assert.assertEquals("50.168.245.32," + DefaultServer.getDefaultServerAddress().getAddress().getHostAddress(), result.getFirstHeader(Headers.X_FORWARDED_FOR.toString()).getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testRewriteHostHeader() throws Exception {
        setProxyHandler(true, false);
        TestHttpClient client = new TestHttpClient();

        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/x-forwarded");
            get.addHeader(Headers.X_FORWARDED_FOR.toString(), "50.168.245.32");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            Assert.assertEquals(port, Integer.parseInt(result.getFirstHeader(Headers.X_FORWARDED_PORT.toString()).getValue()));
            Assert.assertEquals("http", result.getFirstHeader(Headers.X_FORWARDED_PROTO.toString()).getValue());
            Assert.assertEquals(String.format("%s:%d", DefaultServer.getHostAddress(), port), result.getFirstHeader(Headers.X_FORWARDED_HOST.toString()).getValue());
            Assert.assertEquals(DefaultServer.getDefaultServerAddress().getAddress().getHostAddress(), result.getFirstHeader(Headers.X_FORWARDED_FOR.toString()).getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    protected static final class XForwardedHandler implements HttpHandler {

        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            // Copy the X-Fowarded* headers into the response
            if (exchange.getRequestHeaders().contains(Headers.X_FORWARDED_FOR))
                exchange.getResponseHeaders().put(Headers.X_FORWARDED_FOR, exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_FOR));

            if (exchange.getRequestHeaders().contains(Headers.X_FORWARDED_PROTO))
                exchange.getResponseHeaders().put(Headers.X_FORWARDED_PROTO, exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PROTO));

            if (exchange.getRequestHeaders().contains(Headers.X_FORWARDED_HOST))
                exchange.getResponseHeaders().put(Headers.X_FORWARDED_HOST, exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_HOST));

            if (exchange.getRequestHeaders().contains(Headers.X_FORWARDED_PORT))
                exchange.getResponseHeaders().put(Headers.X_FORWARDED_PORT, exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_PORT));
        }
    }
}
