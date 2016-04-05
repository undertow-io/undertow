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

package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.protocol.http2.Http2ServerConnection;
import io.undertow.server.protocol.http2.Http2UpgradeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.Options;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Tests the load balancing proxy
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class LoadBalancingProxyHTTP2ViaUpgradeTestCase extends AbstractLoadBalancingProxyTestCase {

    @BeforeClass
    public static void setup() throws URISyntaxException {
        int port = DefaultServer.getHostPort("default");
        final HttpHandler handler1 = getRootHandler("s1", "server1");
        server1 = Undertow.builder()
                .addHttpListener(port + 1, DefaultServer.getHostAddress("default"))
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(new Http2UpgradeHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        if (!(exchange.getConnection() instanceof Http2ServerConnection)) {
                            throw new RuntimeException("Not HTTP2");
                        }
                        exchange.getResponseHeaders().add(new HttpString("X-Custom-Header"), "foo");
                        System.out.println("server1 " + exchange.getRequestHeaders());
                        handler1.handleRequest(exchange);
                    }
                }))
                .build();

        final HttpHandler handler2 = getRootHandler("s2", "server2");
        server2 = Undertow.builder()
                .addHttpListener(port + 2, DefaultServer.getHostAddress("default"))
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(new Http2UpgradeHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        if (!(exchange.getConnection() instanceof Http2ServerConnection)) {
                            throw new RuntimeException("Not HTTP2");
                        }
                        exchange.getResponseHeaders().add(new HttpString("X-Custom-Header"), "foo");
                        System.out.println("server2 " + exchange.getRequestHeaders());
                        handler2.handleRequest(exchange);
                    }
                }))
                .build();
        server1.start();
        server2.start();

        DefaultServer.setRootHandler(new ProxyHandler(new LoadBalancingProxyClient()
                .setConnectionsPerThread(4)
                .addHost(new URI("h2c", null, DefaultServer.getHostAddress("default"), port + 1, null, null, null), "s1")
                .addHost(new URI("h2c", null, DefaultServer.getHostAddress("default"), port + 2, null, null, null), "s2")
                , 10000, ResponseCodeHandler.HANDLE_404, false, false , 2));
    }

    @Test
    public void testHeadersAreLowercase() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/name");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Header header = result.getFirstHeader("x-custom-header");
            Assert.assertEquals("x-custom-header", header.getName());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
