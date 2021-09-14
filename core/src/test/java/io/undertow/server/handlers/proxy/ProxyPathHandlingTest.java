package io.undertow.server.handlers.proxy;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProxyPathHandlingTest {
    private final TargetServer targetServer = new TargetServer();
    private final ProxyServer proxyServer = new ProxyServer(targetServer.uri);

    @After
    public void cleanup() {
        targetServer.stop();
        proxyServer.stop();
        // add a 1s sleep time to prevent BindException (Address already in use) when restarting the server
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {

        }
    }

    @Test
    public void prefixRootToRoot() throws Exception {
        proxyServer.proxyPrefixPath("/", "/");
        isProxied("", "/");
        isProxied("/", "/");
        isProxied("/foo", "/foo");
    }

    @Test
    public void prefixRootToPath() throws Exception {
        proxyServer.proxyPrefixPath("/", "/path");
        isProxied("", "/path/");
        isProxied("/", "/path/");
        isProxied("/foo", "/path/foo");
    }

    @Test
    public void prefixPathToPath() throws Exception {
        proxyServer.proxyPrefixPath("/path", "/path");
        isProxied("/path", "/path");
        isProxied("/path/", "/path/");
        isProxied("/path/foo", "/path/foo");
        isNotProxied("");
        isNotProxied("/");
        isNotProxied("/foo");
    }

    @Test
    public void prefixPathToRoot() throws Exception {
        proxyServer.proxyPrefixPath("/path", "/");
        isProxied("/path", "/");
        isProxied("/path/", "/");
        isNotProxied("");
        isNotProxied("/");
        isNotProxied("/foo");
    }

    @Test
    public void prefixPathSlashToRoot() throws Exception {
        proxyServer.proxyPrefixPath("/path/", "/");
        isProxied("/path", "/");
        isProxied("/path/", "/");
        isNotProxied("");
        isNotProxied("/");
        isNotProxied("/foo");
    }

    @Test
    public void exactRootToRoot() throws Exception {
        proxyServer.proxyExactPath("/", "/");
        isProxied("", "/");
        isProxied("/", "/");
        isNotProxied("/foo");
    }

    @Test
    public void exactRootToPath() throws Exception {
        proxyServer.proxyExactPath("/", "/path");
        isProxied("", "/path");
        isProxied("/", "/path");
        isNotProxied("/foo");
    }

    @Test
    public void exactRootToPathSlash() throws Exception {
        proxyServer.proxyExactPath("/", "/path/");
        isProxied("", "/path/");
        isProxied("/", "/path/");
        isNotProxied("/foo");
    }

    @Test
    public void exactPathToRoot() throws Exception {
        proxyServer.proxyExactPath("/path", "/");
        isProxied("/path", "/");
        isProxied("/path/", "/");
        isNotProxied("");
        isNotProxied("/");
        isNotProxied("/foo");
        isNotProxied("/path/foo");
    }

    @Test
    public void exactPathSlashToRoot() throws Exception {
        proxyServer.proxyExactPath("/path/", "/");
        isProxied("/path", "/");
        isProxied("/path/", "/");
        isNotProxied("");
        isNotProxied("/");
        isNotProxied("/foo");
        isNotProxied("/path/foo");
    }

    @Test
    public void exactPathToPath() throws Exception {
        proxyServer.proxyExactPath("/path", "/path");
        isProxied("/path", "/path");
        isProxied("/path/", "/path");
        isNotProxied("");
        isNotProxied("/");
        isNotProxied("/foo");
        isNotProxied("/path/foo");
    }

    @Test
    public void exactPathToPathSlash() throws Exception {
        proxyServer.proxyExactPath("/path", "/path/");
        isProxied("/path", "/path/");
        isProxied("/path/", "/path/");
        isNotProxied("");
        isNotProxied("/");
        isNotProxied("/foo");
        isNotProxied("/path/foo");
    }


    private void isProxied(String requestPath, String expectedTargetPath) throws IOException {
        assertEquals(200, httpGet(requestPath));
        assertEquals(expectedTargetPath, targetServer.gotRequest(true));
    }

    private void isNotProxied(String requestPath) throws IOException {
        assertEquals(404, httpGet(requestPath));
        assertNull(targetServer.gotRequest(false));
    }

    private int httpGet(String path) throws IOException {
        TestHttpClient http = new TestHttpClient();
        HttpResponse response = http.execute(new HttpGet(proxyServer.uri + path));
        return response.getStatusLine().getStatusCode();
    }

    private static class ProxyServer {
        private final int port = FreePort.find();
        private final Undertow server;
        private final PathHandler pathHandler = Handlers.path();
        final String uri = "http://localhost:" + port;
        private final String targetUri;

        ProxyServer(String targetUri) {
            this.targetUri = targetUri;
            server = Undertow.builder()
                    .addHttpListener(port, "0.0.0.0")
                    .setHandler(pathHandler)
                    .build();
            server.start();
        }

        void proxyPrefixPath(String proxyPath, String targetPath) {
            pathHandler.addPrefixPath(proxyPath, proxyHandler(targetPath));
        }

        void proxyExactPath(String proxyPath, String targetPath) {
            pathHandler.addExactPath(proxyPath, proxyHandler(targetPath));
        }

        void stop() {
            server.stop();
        }

        private HttpHandler proxyHandler(String targetPath) {
            return ProxyHandler.builder().setProxyClient((
                    new SimpleProxyClientProvider(URI.create(targetUri + targetPath)))).build();
        }
    }

    private static class TargetServer {
        private final int port = FreePort.find();
        private final Undertow server;
        final String uri = "http://localhost:" + port;

        private final LinkedBlockingQueue<String> gotRequestQueue = new LinkedBlockingQueue<>();

        TargetServer() {
            server = Undertow.builder()
                    .addHttpListener(port, "0.0.0.0")
                    .setHandler(new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            gotRequestQueue.add(URI.create(exchange.getRequestURL()).getPath());
                        }
                    })
                    .build();
            server.start();
        }

        void stop() {
            server.stop();
        }

        String gotRequest(boolean wait) {
            String url = null;
            try {
                url = gotRequestQueue.poll( wait ? 10000 : 10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return url;
        }
    }

    private static class FreePort {
        static int find() {
            int port = 0;
            while (port == 0) {
                ServerSocket socket = null;
                try {
                    socket = new ServerSocket(0);
                    port = socket.getLocalPort();
                } catch (IOException e) {
                    throw new RuntimeException("Failed finding free port", e);
                } finally {
                    try {
                        if (socket != null) socket.close();
                    } catch (IOException ignore) {
                    }
                }
            }
            return port;
        }
    }
}
