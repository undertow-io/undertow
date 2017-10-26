/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.examples.reverseproxy;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
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
                    .addHttpListener(8081, "localhost")
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
                    .addHttpListener(8082, "localhost")
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
                    .addHttpListener(8083, "localhost")
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
                    .addHttpListener(8080, "localhost")
                    .setIoThreads(4)
                    .setHandler(ProxyHandler.builder().setProxyClient(loadBalancer).setMaxRequestTime( 30000).build())
                    .build();
            reverseProxy.start();

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
