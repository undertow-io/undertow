package io.undertow.server.protocol.proxy;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.FileUtils;
import io.undertow.util.HttpString;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Tests the proxy protocol
 *
 * @author Stuart Douglas
 */
public class ProxyProtocolTestCase {

    @Test
    public void testProxyProtocol() throws Exception {
        Undertow undertow = Undertow.builder().addListener(
                new Undertow.ListenerBuilder()
                        .setType(Undertow.ListenerType.HTTP)
                        .setHost(DefaultServer.getHostAddress())
                        .setUseProxyProtocol(true)
                        .setPort(0)
        )
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.setPersistent(false);
                        exchange.getResponseHeaders().put(new HttpString("result"), exchange.getSourceAddress().toString() + " " + exchange.getDestinationAddress().toString());
                    }
                })
                .build();
        try {
            undertow.start();
            int port = ((InetSocketAddress) undertow.getListenerInfo().get(0).getAddress()).getPort();
            Socket s = new Socket(DefaultServer.getHostAddress(), port);
            s.getOutputStream().write("PROXY TCP4 1.2.3.4 5.6.7.8 444 555\r\nGET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            String result = FileUtils.readFile(s.getInputStream());
            Assert.assertTrue(result, result.contains("result: /1.2.3.4:444 /5.6.7.8:555"));
        } finally {
            undertow.stop();
        }
    }


    @Test
    public void testProxyProtocolSSl() throws Exception {
        Undertow undertow = Undertow.builder().addListener(
                new Undertow.ListenerBuilder()
                        .setType(Undertow.ListenerType.HTTPS)
                        .setSslContext(DefaultServer.getServerSslContext())
                        .setHost(DefaultServer.getHostAddress())
                        .setUseProxyProtocol(true)
                        .setPort(0)
        )
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.setPersistent(false);
                        exchange.getResponseHeaders().put(new HttpString("result"), exchange.getSourceAddress().toString() + " " + exchange.getDestinationAddress().toString());
                    }
                })
                .build();
        try {
            undertow.start();
            int port = ((InetSocketAddress) undertow.getListenerInfo().get(0).getAddress()).getPort();
            Socket s = new Socket(DefaultServer.getHostAddress(), port);
            s.getOutputStream().write("PROXY TCP4 1.2.3.4 5.6.7.8 444 555\r\n".getBytes(StandardCharsets.US_ASCII));
            s = DefaultServer.getClientSSLContext().getSocketFactory().createSocket(s, DefaultServer.getHostAddress(), port, true);
            s.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            String result = FileUtils.readFile(s.getInputStream());
            Assert.assertTrue(result, result.contains("result: /1.2.3.4:444 /5.6.7.8:555"));
        } finally {
            undertow.stop();
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

    public void doTestProxyProtocolUnknown(String extra) throws Exception {
        Undertow undertow = Undertow.builder().addListener(
                new Undertow.ListenerBuilder()
                        .setType(Undertow.ListenerType.HTTP)
                        .setHost(DefaultServer.getHostAddress())
                        .setUseProxyProtocol(true)
                        .setPort(0)
        )
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.setPersistent(false);
                        exchange.getResponseHeaders().put(new HttpString("result"), exchange.getSourceAddress().toString() + " " + exchange.getDestinationAddress().toString());
                    }
                })
                .build();
        try {
            undertow.start();
            InetSocketAddress serverAddress = (InetSocketAddress) undertow.getListenerInfo().get(0).getAddress();
            Socket s = new Socket(serverAddress.getAddress(), serverAddress.getPort());
            String expected = String.format("result: /%s:%d /%s:%d", s.getLocalAddress().getHostAddress(), s.getLocalPort(), serverAddress.getAddress().getHostAddress(), serverAddress.getPort());
            s.getOutputStream().write(("PROXY UNKNOWN" + extra + "\r\nGET / HTTP/1.0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String result = FileUtils.readFile(s.getInputStream());
            Assert.assertTrue(result, result.contains(expected));
        } finally {
            undertow.stop();
        }
    }
}
