package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.InetAddress;

@RunWith(DefaultServer.class)
@ProxyIgnore
public class ProxyPeerAddressHandlerTestCase {

    @BeforeClass
    public static void setup() {

    }

    private static String[] run(String forVal, String host, String port, String proto) throws IOException {

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            if (forVal != null) {
                get.addHeader(Headers.X_FORWARDED_FOR_STRING, forVal);
            }
            if (host != null) {
                get.addHeader(Headers.X_FORWARDED_HOST_STRING, host);
            }
            if (port != null) {
                get.addHeader(Headers.X_FORWARDED_PORT_STRING, port);
            }
            if (proto != null) {
                get.addHeader(Headers.X_FORWARDED_PROTO_STRING, proto);
            }
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            return HttpClientUtils.readResponse(result).split("\\|");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testProxyPeerAddressHandler() throws IOException {
        DefaultServer.setRootHandler(new ProxyPeerAddressHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send(exchange.getRequestScheme() + "|" + exchange.getHostAndPort() + "|" + exchange.getDestinationAddress() + "|" + exchange.getSourceAddress());
            }
        }).setDefaultAllow(true));

        String[] res = run(null, null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);

        res = run(null, "google.com", null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals("google.com", res[1]);
        Assert.assertEquals("google.com:80", res[2]);

        res = run(null, "google.com", null, "https");
        Assert.assertEquals("https", res[0]);
        Assert.assertEquals("google.com", res[1]);
        Assert.assertEquals("google.com:80", res[2]);

        res = run("8.8.8.8:3545", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/8.8.8.8:3545", res[3]);

        res = run("8.8.8.8:3545, 9.9.9.9:2343", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/8.8.8.8:3545", res[3]);

        res = run("[::1]:3545, 9.9.9.9:2343", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/0:0:0:0:0:0:0:1:3545", res[3]);

        res = run("[::1]:_foo, 9.9.9.9:2343", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/0:0:0:0:0:0:0:1:0", res[3]);

        res = run("[::1], 9.9.9.9:2343", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/0:0:0:0:0:0:0:1:0", res[3]);

        res = run("9.9.9.9:2343", "[::1]", null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals("[::1]", res[1]);
        Assert.assertEquals("[::1]:80", res[2]);
        Assert.assertEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "google.com", "0", null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals("google.com", res[1]);
        Assert.assertEquals("google.com:0", res[2]);
        Assert.assertEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "google.com", "80", "http");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals("google.com", res[1]);
        Assert.assertEquals("google.com:80", res[2]);
        Assert.assertEquals("/9.9.9.9:2343", res[3]);


        res = run("9.9.9.9:2343", "google.com", "443", "https");
        Assert.assertEquals("https", res[0]);
        Assert.assertEquals("google.com", res[1]);
        Assert.assertEquals("google.com:443", res[2]);
        Assert.assertEquals("/9.9.9.9:2343", res[3]);


        res = run("9.9.9.9:2343", "google.com", "8443", "https");
        Assert.assertEquals("https", res[0]);
        Assert.assertEquals("google.com:8443", res[1]);
        Assert.assertEquals("google.com:8443", res[2]);
        Assert.assertEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "google.com:8443", null, "https");
        Assert.assertEquals("https", res[0]);
        Assert.assertEquals("google.com:8443", res[1]);
        Assert.assertEquals("google.com:8443", res[2]);
        Assert.assertEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "google.com:8443", "8444", "https");
        Assert.assertEquals("https", res[0]);
        Assert.assertEquals("google.com:8443", res[1]);
        Assert.assertEquals("google.com:8443", res[2]);
        Assert.assertEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "[::1]:8443", null, "https");
        Assert.assertEquals("https", res[0]);
        Assert.assertEquals("[::1]:8443", res[1]);
        Assert.assertEquals("[::1]:8443", res[2]);
        Assert.assertEquals("/9.9.9.9:2343", res[3]);

    }

    @Test
    public void testProxyPeerAddressHandlerWithAllowedProxyPeer() throws IOException {
        ProxyPeerAddressHandler handler = new ProxyPeerAddressHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send(exchange.getRequestScheme() + "|" + exchange.getHostAndPort() + "|" + exchange.getDestinationAddress() + "|" + exchange.getSourceAddress());
            }
        }).setDefaultAllow(false);
        DefaultServer.setRootHandler(handler);

        String[] res = run(null, null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);

        res = run(null, "google.com", null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);

        res = run(null, "google.com", null, "https");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);

        handler.addAllow("127.0.0.1");

        res = run("8.8.8.8:3545", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/8.8.8.8:3545", res[3]);

        res = run("8.8.8.8:3545, 9.9.9.9:2343", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/8.8.8.8:3545", res[3]);

        res = run("[::1]:3545, 9.9.9.9:2343", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/0:0:0:0:0:0:0:1:3545", res[3]);

        res = run("[::1]:_foo, 9.9.9.9:2343", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/0:0:0:0:0:0:0:1:0", res[3]);

        res = run("[::1], 9.9.9.9:2343", null, null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals(DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals("/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals("/0:0:0:0:0:0:0:1:0", res[3]);

        handler.clearRules();
        handler.addDeny("127.0.0.1");

        res = run("9.9.9.9:2343", "[::1]", null, null);
        Assert.assertEquals("http", res[0]);
        Assert.assertNotEquals("[::1]", res[1]);
        Assert.assertNotEquals("[::1]:80", res[2]);
        Assert.assertNotEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "google.com", "0", null);
        Assert.assertEquals("http", res[0]);
        Assert.assertNotEquals("google.com", res[1]);
        Assert.assertNotEquals("google.com:0", res[2]);
        Assert.assertNotEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "google.com", "80", "http");
        Assert.assertEquals("http", res[0]);
        Assert.assertNotEquals("google.com", res[1]);
        Assert.assertNotEquals("google.com:80", res[2]);
        Assert.assertNotEquals("/9.9.9.9:2343", res[3]);


        res = run("9.9.9.9:2343", "google.com", "443", "https");
        Assert.assertNotEquals("https", res[0]);
        Assert.assertNotEquals("google.com", res[1]);
        Assert.assertNotEquals("google.com:443", res[2]);
        Assert.assertNotEquals("/9.9.9.9:2343", res[3]);


        res = run("9.9.9.9:2343", "google.com", "8443", "https");
        Assert.assertNotEquals("https", res[0]);
        Assert.assertNotEquals("google.com:8443", res[1]);
        Assert.assertNotEquals("google.com:8443", res[2]);
        Assert.assertNotEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "google.com:8443", null, "https");
        Assert.assertNotEquals("https", res[0]);
        Assert.assertNotEquals("google.com:8443", res[1]);
        Assert.assertNotEquals("google.com:8443", res[2]);
        Assert.assertNotEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "google.com:8443", "8444", "https");
        Assert.assertNotEquals("https", res[0]);
        Assert.assertNotEquals("google.com:8443", res[1]);
        Assert.assertNotEquals("google.com:8443", res[2]);
        Assert.assertNotEquals("/9.9.9.9:2343", res[3]);

        res = run("9.9.9.9:2343", "[::1]:8443", null, "https");
        Assert.assertNotEquals("https", res[0]);
        Assert.assertNotEquals("[::1]:8443", res[1]);
        Assert.assertNotEquals("[::1]:8443", res[2]);
        Assert.assertNotEquals("/9.9.9.9:2343", res[3]);

    }


}
