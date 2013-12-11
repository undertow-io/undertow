package io.undertow.examples.reverseproxy;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.Headers;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Stuart Douglas
 */
@UndertowExample("Reverse Proxy")
public class ReverseProxyServer {

    public static void main(final String[] args) {
        try {
            final Undertow server1 = Undertow.builder()
                    .addListener(8081, "localhost")
                    .setHandler(new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server1");
                        }
                    })
                    .build();

            server1.start();

            final Undertow server2 = Undertow.builder()
                    .addListener(8082, "localhost")
                    .setHandler(new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server2");
                        }
                    })
                    .build();
            server2.start();

            final Undertow server3 = Undertow.builder()
                    .addListener(8083, "localhost")
                    .setHandler(new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server3");
                        }
                    })
                    .build();

            server3.start();

            LoadBalancingProxyClient loadBalancer = new LoadBalancingProxyClient()
                    .addHost(new URI("http://localhost:8081"))
                    .addHost(new URI("http://localhost:8082"))
                    .addHost(new URI("http://localhost:8083"))
                    .setConnectionsPerThread(20);

            Undertow reverseProxy = Undertow.builder()
                    .addListener(8080, "localhost")
                    .setIoThreads(4)
                    .setHandler(new ProxyHandler(loadBalancer, 30000, ResponseCodeHandler.HANDLE_404))
                    .build();
            reverseProxy.start();

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
