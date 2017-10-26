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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http2.Http2ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;

/**
 * Tests the load balancing proxy
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class LoadBalancingProxyHTTP2TestCase extends AbstractLoadBalancingProxyTestCase {

    @BeforeClass
    public static void setup() throws URISyntaxException {
        int port = DefaultServer.getHostPort("default");
        final HttpHandler handler1 = getRootHandler("s1", "server1");
        server1 = Undertow.builder()
                .addHttpsListener(port + 1, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, IDLE_TIMEOUT)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        if (!(exchange.getConnection() instanceof Http2ServerConnection)) {
                            throw new RuntimeException("Not HTTP2");
                        }
                        exchange.getResponseHeaders().add(new HttpString("X-Custom-Header"), "foo");
                        handler1.handleRequest(exchange);
                    }
                })
                .build();

        final HttpHandler handler2 = getRootHandler("s2", "server2");
        server2 = Undertow.builder()
                .addHttpsListener(port + 2, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, IDLE_TIMEOUT)
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        if (!(exchange.getConnection() instanceof Http2ServerConnection)) {
                            throw new RuntimeException("Not HTTP2");
                        }
                        exchange.getResponseHeaders().add(new HttpString("X-Custom-Header"), "foo");
                        handler2.handleRequest(exchange);
                    }
                })
                .build();
        server1.start();
        server2.start();

        UndertowXnioSsl ssl = new UndertowXnioSsl(DefaultServer.getWorker().getXnio(), OptionMap.EMPTY, DefaultServer.SSL_BUFFER_POOL, DefaultServer.createClientSslContext());

        DefaultServer.setRootHandler(ProxyHandler.builder().setProxyClient(new LoadBalancingProxyClient()
                .setConnectionsPerThread(4)
                .addHost(new URI("https", null, DefaultServer.getHostAddress("default"), port + 1, null, null, null), "s1", ssl, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true))
                .addHost(new URI("https", null, DefaultServer.getHostAddress("default"), port + 2, null, null, null), "s2", ssl, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)))
                .setMaxRequestTime(10000)
                .setMaxConnectionRetries(2).build());
    }


    @Before
    public void requireAlpn() {
        DefaultServer.assumeAlpnEnabled();
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

    @Test
    public void testHttp2ClientMultipleStreamsThreadSafety() throws IOException, URISyntaxException, ExecutionException, InterruptedException, TimeoutException {
        //not actually a proxy test
        //but convent to put it here
        UndertowXnioSsl ssl = new UndertowXnioSsl(DefaultServer.getWorker().getXnio(), OptionMap.EMPTY, DefaultServer.SSL_BUFFER_POOL, DefaultServer.createClientSslContext());
        final UndertowClient client = UndertowClient.getInstance();
        final ClientConnection connection = client.connect(new URI("https", null, DefaultServer.getHostAddress(), DefaultServer.getHostPort() + 1, "/", null, null), DefaultServer.getWorker(), ssl, DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        final ExecutorService service = Executors.newFixedThreadPool(10);
        try {
            Deque<FutureResult<String>> futures = new ArrayDeque<>();
            for (int i = 0; i < 100; ++i) {
                final FutureResult<String> future = new FutureResult<>();
                futures.add(future);
                service.submit(new Callable<String>() {

                    @Override
                    public String call() throws Exception {
                        ClientRequest cr = new ClientRequest()
                                .setMethod(Methods.GET)
                                .setPath("/path")
                                .setProtocol(Protocols.HTTP_1_1);
                        connection.sendRequest(cr, new ClientCallback<ClientExchange>() {
                            @Override
                            public void completed(ClientExchange result) {
                                result.setResponseListener(new ClientCallback<ClientExchange>() {
                                    @Override
                                    public void completed(ClientExchange result) {
                                        new StringReadChannelListener(DefaultServer.getBufferPool()) {
                                            @Override
                                            protected void stringDone(String string) {
                                                future.setResult(string);
                                            }

                                            @Override
                                            protected void error(IOException e) {
                                                future.setException(e);
                                            }
                                        }.setup(result.getResponseChannel());
                                    }

                                    @Override
                                    public void failed(IOException e) {
                                        future.setException(e);
                                    }
                                });
                            }

                            @Override
                            public void failed(IOException e) {
                                future.setException(e);
                            }
                        });
                        return null;
                    }
                });
            }
            while (!futures.isEmpty()) {
                FutureResult<String> future = futures.poll();
                Assert.assertNotEquals(IoFuture.Status.WAITING, future.getIoFuture().awaitInterruptibly(10, TimeUnit.SECONDS));
                Assert.assertEquals("/path", future.getIoFuture().get());
            }
        } finally {
            service.shutdownNow();
        }
    }
}
