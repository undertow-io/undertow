package io.undertow.server.protocol.proxy;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.FileUtils;
import io.undertow.util.HttpString;

/**
 * Tests the proxy protocol
 *
 * @author Stuart Douglas
 * @author Jan Stourac
 */
public class ProxyProtocolTestCase {

    // Undertow with HTTP listener and proxy-protocol enabled
    private Undertow undertow = Undertow.builder().addListener(
            new Undertow.ListenerBuilder()
                    .setType(Undertow.ListenerType.HTTP)
                    .setHost(DefaultServer.getHostAddress())
                    .setUseProxyProtocol(true)
                    .setPort(0)
    ).setHandler(new HttpHandler() {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            exchange.setPersistent(false);
            exchange.getResponseHeaders().put(new HttpString("result"), exchange.getSourceAddress().toString()
                    + " " + exchange.getDestinationAddress().toString());
        }
    }).build();

    // Undertow with HTTPS listener and proxy-protocol enabled
    private Undertow undertowSsl = Undertow.builder().addListener(
            new Undertow.ListenerBuilder()
                    .setType(Undertow.ListenerType.HTTPS)
                    .setSslContext(DefaultServer.getServerSslContext())
                    .setHost(DefaultServer.getHostAddress())
                    .setUseProxyProtocol(true)
                    .setPort(0)
    ).setHandler(new HttpHandler() {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            exchange.setPersistent(false);
            exchange.getResponseHeaders().put(new HttpString("result"), exchange.getSourceAddress().toString()
                    + " " + exchange.getDestinationAddress().toString());
        }
    }).build();

    @Test
    public void testProxyProtocolTcp4() throws Exception {
        // simple valid request
        String request = "PROXY TCP4 1.2.3.4 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        String expectedResponse = "result: /1.2.3.4:444 /5.6.7.8:555";
        proxyProtocolRequestResponseCheck(request, expectedResponse);

        // check port range
        request = "PROXY TCP4 1.2.3.4 5.6.7.8 0 65535\r\nGET / HTTP/1.0\r\n\r\n";
        expectedResponse = "result: /1.2.3.4:0 /5.6.7.8:65535";
        proxyProtocolRequestResponseCheck(request, expectedResponse);
    }

    @Test
    public void testProxyProtocolTcp4Negative() throws Exception {
        // wrong number of spaces in requests
        String request = "PROXY  TCP4 1.2.3.4 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP4  1.2.3.4 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP4 1.2.3.4  5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP4 1.2.3.4 5.6.7.8  444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP4 1.2.3.4 5.6.7.8 444  555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // missing destination port
        request = "PROXY TCP4 1.2.3.4 5.6.7.8 444\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // missing destination address
        request = "PROXY TCP4 1.2.3.4 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // missing \n on the first line of the request
        request = "PROXY TCP4 1.2.3.4 5.6.7.8 444 555\rGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // missing \r on the first line of the request
        request = "PROXY TCP4 1.2.3.4 5.6.7.8 444 555\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // src address contains 0 characters at the beginning
        request = "PROXY TCP4 001.002.003.004 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // dst address contains '0' characters at the beginning
        request = "PROXY TCP4 1.2.3.4 005.006.007.008 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // src/dst ports out of range
        request = "PROXY TCP4 1.2.3.4 5.6.7.8 111444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP4 1.2.3.4 005.006.007.008 444 111555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP4 1.2.3.4 005.006.007.008 -444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP4 1.2.3.4 005.006.007.008 444 -555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // src/dst ports contains '0' characters at the beginning
        request = "PROXY TCP4 1.2.3.4 5.6.7.8 0444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP4 1.2.3.4 5.6.7.8 444 0555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // src address contains invalid characters
        request = "PROXY TCP4 277.2.3.4 5.6.7.8 444 555\r\nGET / " + "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // dst address contains invalid characters
        request = "PROXY TCP4 1.2.3.4 5d.6.7.8 444 555\r\nGET / " + "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // unallowed character after PROXY string
        request = "PROXY, TCP4 1.2.3.4 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // IPv6 address when TCP4 is used
        request = "PROXY TCP4 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");
    }

    @Test
    public void testProxyProtocolTcp6() throws Exception {
        // simple valid request
        String request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        String expectedResponse = "result: /fe80:0:0:0:56ee:75ff:fe44:85bc:444 /fe80:0:0:0:5ec5:d4ff:fede:66d8:555";
        proxyProtocolRequestResponseCheck(request, expectedResponse);

        // check port range
        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 0 65535\r\nGET / HTTP/1.0\r\n\r\n";
        expectedResponse = "result: /fe80:0:0:0:56ee:75ff:fe44:85bc:0 /fe80:0:0:0:5ec5:d4ff:fede:66d8:65535";
        proxyProtocolRequestResponseCheck(request, expectedResponse);
    }

    @Test
    public void testProxyProtocolTcp6Negative() throws Exception {
        // wrong number of spaces in requests
        String request = "PROXY  TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP6  fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc  fe80::5ec5:d4ff:fede:66d8 444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8  444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444  555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // missing destination port
        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444\r\nGET / " + "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // missing destination address
        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc 444 555\r\nGET / " + "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // missing \n on the first line of the request
        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 555\rGET / " + "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // missing \r on the first line of the request
        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 555\nGET / " + "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // src address contains invalid characters
        request = "PROXY TCP6 fz80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // dst address contains invalid characters
        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5zc5:d4ff:fede:66d8 444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // src/dst ports out of range
        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 111444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 111555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 -444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 -555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // src/dst ports contains '0' characters at the beginning
        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 0444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 0555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // unallowed character after PROXY string
        request = "PROXY, TCP6 fe80::56ee:75ff:fe44:85bc fe80::5ec5:d4ff:fede:66d8 444 555\r\nGET / " +
                "HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        // IPv6 address when TCP4 is used
        request = "PROXY TCP6 1.2.3.4 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");
    }

    /**
     * General negative tests for proxy-protocol. We expect that server closes connection sending no data.
     *
     * @throws Exception
     */
    @Test
    public void testProxyProtocolNegative() throws Exception {
        String request = "NONSENSE\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "NONSENSE TCP4 1.2.3.4 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "NONSENSE\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY NONSENSE\r\n";
        proxyProtocolRequestResponseCheck(request, "");

        request = "PROXY NONSENSE 1.2.3.4 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, "");
    }


    /**
     * Starts an undertow server with HTTP listener and performs request to the server with given request string.
     * Then response from the server is checked with given expected response string. Undertow is stopped in the end.
     *
     * @param request          request string that is send to server
     * @param expectedResponse expected response string that we expect from the server
     * @throws Exception
     */
    private void proxyProtocolRequestResponseCheck(String request, String expectedResponse) throws Exception {
        try {
            undertow.start();
            int port = ((InetSocketAddress) undertow.getListenerInfo().get(0).getAddress()).getPort();
            Socket s = new Socket(DefaultServer.getHostAddress(), port);
            s.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            String result = FileUtils.readFile(s.getInputStream());
            Assert.assertTrue(result, result.contains(expectedResponse));
        } finally {
            undertow.stop();
        }
    }

    /**
     * Main cases are covered in plain-text HTTP connection tests. So here is just simple check that connection can
     * be established also via HTTPS.
     *
     * @throws Exception
     */
    @Test
    public void testProxyProtocolSSl() throws Exception {
        String request = "PROXY TCP4 1.2.3.4 5.6.7.8 444 555\r\n";
        String requestHttp = "GET / HTTP/1.0\r\n\r\n";
        String expectedResponse = "result: /1.2.3.4:444 /5.6.7.8:555";
        proxyProtocolRequestResponseCheck(request, requestHttp, expectedResponse);

        // negative test
        request = "PROXY TCP4  1.2.3.4 5.6.7.8 444 555\r\n";
        requestHttp = "GET / HTTP/1.0\r\n\r\n";
        proxyProtocolRequestResponseCheck(request, requestHttp, "");
    }

    /**
     * Starts an undertow server with HTTPS listener and performs request to the server with given request proxy
     * string and HTTP request. Then response from the server is checked with given expected response string.
     * Undertow is stopped in the end.
     *
     * @param requestProxy     request string with proxy-protocol part
     * @param requestHttp      request string with HTTP part
     * @param expectedResponse expected response string that we expect from the server
     * @throws Exception
     */
    private void proxyProtocolRequestResponseCheck(String requestProxy, String requestHttp, String expectedResponse)
            throws Exception {
        try {
            undertowSsl.start();
            int port = ((InetSocketAddress) undertowSsl.getListenerInfo().get(0).getAddress()).getPort();
            Socket s = new Socket(DefaultServer.getHostAddress(), port);
            s.getOutputStream().write(requestProxy.getBytes(StandardCharsets.US_ASCII));
            // if expectedResponse is empty, we expect server to close connection due to bad request
            if (!expectedResponse.isEmpty()) {
                s = DefaultServer.getClientSSLContext().getSocketFactory().createSocket(s, DefaultServer
                        .getHostAddress(), port, true);
                s.getOutputStream().write(requestHttp.getBytes(StandardCharsets.US_ASCII));
            }
            String result = FileUtils.readFile(s.getInputStream());
            Assert.assertTrue(result, result.contains(expectedResponse));
        } finally {
            undertowSsl.stop();
        }
    }


    @Test
    public void testProxyProtocolUnknownEmpty() throws Exception {
        doTestProxyProtocolUnknown("");
    }

    @Test
    public void testProxyProtocolUnknownSpace() throws Exception {
        doTestProxyProtocolUnknown(" ");
    }

    @Test
    public void testProxyProtocolUnknownJunk() throws Exception {
        doTestProxyProtocolUnknown(" mekmitasdigoat");
    }

    /**
     * Starts an undertow server with HTTP listener and performs request to the server with request proxy with
     * UNKNOWN protocol string and HTTP request. Then response from the server is checked with given expected
     * response string. Undertow is stopped in the end.
     *
     * @param extra extra content added after protocol type - "UNKNOWN" - server should ignore this information
     * @throws Exception
     */
    public void doTestProxyProtocolUnknown(String extra) throws Exception {
        try {
            undertow.start();
            InetSocketAddress serverAddress = (InetSocketAddress) undertow.getListenerInfo().get(0).getAddress();
            Socket s = new Socket(serverAddress.getAddress(), serverAddress.getPort());
            String expected = String.format("result: /%s:%d /%s:%d", s.getLocalAddress().getHostAddress(), s
                    .getLocalPort(), serverAddress.getAddress().getHostAddress(), serverAddress.getPort());
            s.getOutputStream().write(("PROXY UNKNOWN" + extra + "\r\nGET / HTTP/1.0\r\n\r\n").getBytes
                    (StandardCharsets.US_ASCII));
            String result = FileUtils.readFile(s.getInputStream());
            Assert.assertTrue(result, result.contains(expected));
        } finally {
            undertow.stop();
        }
    }
}
