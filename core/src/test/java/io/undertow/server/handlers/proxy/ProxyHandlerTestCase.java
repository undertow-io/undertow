package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.ssl.JsseXnioSsl;

import java.net.URI;

import static io.undertow.Handlers.jvmRoute;
import static io.undertow.Handlers.path;

/**
 * Created by ivannagy on 8/26/14.
 */
@RunWith(DefaultServer.class)
public class ProxyHandlerTestCase {

    protected static Undertow server;
    protected static int port;
    protected static int sslPort;
    protected static int handlerPort;
    protected static JsseXnioSsl ssl;

    @BeforeClass
    public static void setup() throws Exception {

        port = DefaultServer.getHostPort("default");
        sslPort = port + 1;
        handlerPort = port + 2;

        DefaultServer.startSSLServer();
        ssl = new JsseXnioSsl(DefaultServer.getWorker().getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext());

        server = Undertow.builder()
            .addHttpsListener(handlerPort, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
            .setServerOption(UndertowOptions.ENABLE_SPDY, false)
            .setSocketOption(Options.REUSE_ADDRESSES, true)
            .setHandler(jvmRoute("JSESSIONID", "s1", path().addPrefixPath("/x-forwarded", new XForwardedHandler())))
            .build();

        server.start();

    }

    @AfterClass
    public static void teardown() throws Exception {
        DefaultServer.stopSSLServer();
        server.stop();
    }

    private static void setProxyHandler(boolean rewriteHostHeader, boolean reuseXForwarded) throws Exception {

        DefaultServer.setRootHandler(new ProxyHandler(new LoadBalancingProxyClient()
            .setConnectionsPerThread(1)
            .addHost(new URI("https", null, DefaultServer.getHostAddress("default"), handlerPort, null, null, null), "s1", ssl, OptionMap.create(UndertowOptions.ENABLE_SPDY, false))
            , 10000, ResponseCodeHandler.HANDLE_404, rewriteHostHeader, reuseXForwarded));

    }

    @Test
    public void testXForwarded() throws Exception {
        setProxyHandler(false, false);
        TestHttpClient client = new TestHttpClient();

        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/x-forwarded");

            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());

            Assert.assertEquals(port, Integer.parseInt(result.getFirstHeader(Headers.X_FORWARDED_PORT.toString()).getValue()));
            Assert.assertEquals("http", result.getFirstHeader(Headers.X_FORWARDED_PROTO.toString()).getValue());
            Assert.assertEquals("localhost", result.getFirstHeader(Headers.X_FORWARDED_HOST.toString()).getValue());
            Assert.assertEquals("127.0.0.1", result.getFirstHeader(Headers.X_FORWARDED_FOR.toString()).getValue());

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
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());

            Assert.assertEquals(sslPort, Integer.parseInt(result.getFirstHeader(Headers.X_FORWARDED_PORT.toString()).getValue()));
            Assert.assertEquals("https", result.getFirstHeader(Headers.X_FORWARDED_PROTO.toString()).getValue());
            Assert.assertEquals("localhost", result.getFirstHeader(Headers.X_FORWARDED_HOST.toString()).getValue());
            Assert.assertEquals("127.0.0.1", result.getFirstHeader(Headers.X_FORWARDED_FOR.toString()).getValue());

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
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());

            Assert.assertEquals(port, Integer.parseInt(result.getFirstHeader(Headers.X_FORWARDED_PORT.toString()).getValue()));
            Assert.assertEquals("http", result.getFirstHeader(Headers.X_FORWARDED_PROTO.toString()).getValue());
            Assert.assertEquals("localhost", result.getFirstHeader(Headers.X_FORWARDED_HOST.toString()).getValue());
            Assert.assertEquals("50.168.245.32,127.0.0.1", result.getFirstHeader(Headers.X_FORWARDED_FOR.toString()).getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testReqriteHostHeader() throws Exception {
        setProxyHandler(true, false);
        TestHttpClient client = new TestHttpClient();

        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/x-forwarded");
            get.addHeader(Headers.X_FORWARDED_FOR.toString(), "50.168.245.32");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());

            Assert.assertEquals(port, Integer.parseInt(result.getFirstHeader(Headers.X_FORWARDED_PORT.toString()).getValue()));
            Assert.assertEquals("http", result.getFirstHeader(Headers.X_FORWARDED_PROTO.toString()).getValue());
            Assert.assertEquals(String.format("localhost:%d", port), result.getFirstHeader(Headers.X_FORWARDED_HOST.toString()).getValue());
            Assert.assertEquals("127.0.0.1", result.getFirstHeader(Headers.X_FORWARDED_FOR.toString()).getValue());

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
