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
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.undertow.server.handlers.ForwardedHandler.parseAddress;
import static io.undertow.server.handlers.ForwardedHandler.parseHeader;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class ForwardedHandlerTestCase {

    @BeforeClass
    public static void setup() {
        final boolean DEFAULT_CHANGE_LOCAL_ADDR_PORT = Boolean.TRUE;
        DefaultServer.setRootHandler(new ForwardedHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send(
                        exchange.getRequestScheme()
                                + "|" + exchange.getHostAndPort()
                                + "|" + toJreNormalizedString(exchange.getDestinationAddress())
                                + "|" + toJreNormalizedString(exchange.getSourceAddress()));
            }
        }, DEFAULT_CHANGE_LOCAL_ADDR_PORT));
    }

    private static String toJreNormalizedString(InetSocketAddress address) {
        // https://mail.openjdk.java.net/pipermail/net-dev/2019-June/012741.html
        // https://bugs.openjdk.java.net/browse/JDK-8225499
        // Java 14 introduced a new component to the toString value to disambiguate ipv6 values
        return Objects.toString(address)
                .replace("/<unresolved>", "")
                .replace("[", "")
                .replace("]", "");
    }

    @Test
    public void testHeaderParsing() {
        Map<ForwardedHandler.Token, String> results = new HashMap<>();
        parseHeader("For=\"[2001:db8:cafe::17]:4711\"", results);
        Assert.assertEquals("[2001:db8:cafe::17]:4711", results.get(ForwardedHandler.Token.FOR));
        results.clear();
        parseHeader("for=192.0.2.60;proto=http;by=203.0.113.43", results);
        Assert.assertEquals("192.0.2.60", results.get(ForwardedHandler.Token.FOR));
        Assert.assertEquals("http", results.get(ForwardedHandler.Token.PROTO));
        Assert.assertEquals("203.0.113.43", results.get(ForwardedHandler.Token.BY));
        results.clear();
        parseHeader("for=192.0.2.43, for=198.51.100.17", results);
        Assert.assertEquals("192.0.2.43", results.get(ForwardedHandler.Token.FOR));
        results.clear();
        parseHeader("for=192.0.2.43, for=198.51.100.17;by=\"foo\"", results);
        Assert.assertEquals("192.0.2.43", results.get(ForwardedHandler.Token.FOR));
        Assert.assertEquals("foo", results.get(ForwardedHandler.Token.BY));
        results.clear();
    }

    @Test
    public void testAddressParsing() throws UnknownHostException {
        Assert.assertEquals(null, parseAddress("unknown"));
        Assert.assertEquals(null, parseAddress("_foo"));
        Assert.assertEquals(new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 192, (byte) 168, 1, 1}), 0), parseAddress("192.168.1.1"));
        Assert.assertEquals(new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 192, (byte) 168, 1, 1}), 8080), parseAddress("192.168.1.1:8080"));
        Assert.assertEquals(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 0), parseAddress("[::1]"));
        Assert.assertEquals(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 8080), parseAddress("[::1]:8080"));
    }


    @Test
    public void testForwardedHandler() throws IOException {
        String[] res = run();
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals( "/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);

        res = run("host=google.com");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( "google.com", res[1]);
        Assert.assertEquals( "google.com:80", res[2]);

        res = run("host=google.com, proto=https");
        Assert.assertEquals("https", res[0]);
        Assert.assertEquals( "google.com", res[1]);
        Assert.assertEquals( "google.com:80", res[2]);

        res = run("for=8.8.8.8:3545");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals( "/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals( "/8.8.8.8:3545", res[3]);

        res = run("for=8.8.8.8:3545, for=9.9.9.9:2343");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals( "/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals( "/8.8.8.8:3545", res[3]);

        res = run("for=[::1]:3545, for=9.9.9.9:2343");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals( "/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals( "/0:0:0:0:0:0:0:1:3545", res[3]);

        res = run("for=[::1]:_foo, for=9.9.9.9:2343");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals( "/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals( "/0:0:0:0:0:0:0:1:0", res[3]);

        res = run("for=[::1], for=9.9.9.9:2343");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals( "/" + InetAddress.getByName(DefaultServer.getHostAddress()).getHostAddress() + ":" + DefaultServer.getHostPort(), res[2]);
        Assert.assertEquals( "/0:0:0:0:0:0:0:1:0", res[3]);


        res = run("by=[::1]; for=9.9.9.9:2343");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort(), res[1]);
        Assert.assertEquals( "/0:0:0:0:0:0:0:1:0", res[2]);
        Assert.assertEquals( "/9.9.9.9:2343", res[3]);

        res = run("by=[::1]; for=9.9.9.9:2343; host=foo.com");
        Assert.assertEquals("http", res[0]);
        Assert.assertEquals( "foo.com", res[1]);
        Assert.assertEquals( "foo.com:80", res[2]);
        Assert.assertEquals( "/9.9.9.9:2343", res[3]);

    }

    private static String[] run(String ... headers) throws IOException {

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            for(String i : headers) {
                get.addHeader(Headers.FORWARDED_STRING, i);
            }
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            return HttpClientUtils.readResponse(result).split("\\|");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
