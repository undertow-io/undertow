/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http2.Http2ServerConnection;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;

import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(DefaultServer.class)
public class GracefulShutdownHttp2TestCase {
    private static final Logger log = Logger.getLogger(GracefulShutdownHttp2TestCase.class);

    private static Undertow server;
    private static GracefulShutdownHandler shutdown;
    private static int port = DefaultServer.getHostPort("default") + 1;

    private static XnioWorker worker;

    private static final Set<Http2Channel> activeHttp2Channels = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final AtomicReference<CountDownLatch> connectionClosedLatch = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> requestReceivedLatch = new AtomicReference<>();

    @BeforeClass
    public static void setup() throws Exception {

        shutdown = Handlers.gracefulShutdown(new HttpHandler() {
                @Override
                public void handleRequest(HttpServerExchange exchange) throws Exception {
                    Assert.assertEquals(Protocols.HTTP_2_0, exchange.getProtocol());

                    requestReceivedLatch.get().countDown();

                    // Add a close listener latch for new http2 connections
                    if (exchange.getConnection() instanceof Http2ServerConnection) {
                        Http2Channel channel = ((Http2ServerConnection) exchange.getConnection()).getChannel();

                        if(!activeHttp2Channels.contains(channel)) {
                            log.debugf("Channel added: %s", channel);
                            activeHttp2Channels.add(channel);
                            channel.addCloseTask(new ChannelListener<Http2Channel>() {
                                    @Override
                                    public void handleEvent(Http2Channel c) {
                                        log.debugf("Channel closed: %s", c);
                                        activeHttp2Channels.remove(channel);
                                        connectionClosedLatch.get().countDown();
                                    }
                                });
                        }
                    }
                }
            });

        // Start server
        server = Undertow.builder()
            .addHttpsListener(port, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
            .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
            .setSocketOption(Options.REUSE_ADDRESSES, true)
            .setHandler(shutdown)
            .build();
        server.start();

        // Create a client worker
        final Xnio xnio = Xnio.getInstance();
        worker = xnio.createWorker(null,
                                   OptionMap.builder()
                                   .set(Options.WORKER_IO_THREADS, 8)
                                   .set(Options.TCP_NODELAY, true)
                                   .set(Options.KEEP_ALIVE, true)
                                   .set(Options.WORKER_NAME, "Client").getMap());
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        worker.shutdownNow();
        server.stop();
    }

    private static class ResponseListener implements ClientCallback<ClientExchange> {
        private final CountDownLatch latch;

        ResponseListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void completed(final ClientExchange result) {
            latch.countDown();
        }

        @Override
        public void failed(IOException e) {
            e.printStackTrace();
        }
    }

    private void sendRequest(ClientConnection connection)  throws Exception {
        requestReceivedLatch.set(new CountDownLatch(1));
        final CountDownLatch requestCompletedLatch = new CountDownLatch(1);
        connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/message");
                    request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                    connection.sendRequest(request, new ResponseListener(requestCompletedLatch));
                }
            });
        // Expect server to receive request
        requestReceivedLatch.get().await(10, TimeUnit.SECONDS);
        // Expect client to get a completed callback
        requestCompletedLatch.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void simpleGracefulShutdownTestCase() throws Exception {
        // Create a client
        final UndertowClient client = UndertowClient.getInstance();
        final URI uri = new URI("https://" + DefaultServer.getHostAddress() + ":" + port);
        final ClientConnection connection = client.connect(uri, worker, new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext()),
                                                           DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            // Expect one connection
            connectionClosedLatch.set(new CountDownLatch(1));

            // Send two requests from client
            sendRequest(connection);
            sendRequest(connection);

            // Trigger graceful shutdown
            shutdown.shutdown();

            // Expect client to disconnect by itself
            connectionClosedLatch.get().await(4, TimeUnit.SECONDS);
            Assert.assertEquals(0, activeHttp2Channels.size());

            // Wait for a complete shutdown to stop server safely
            shutdown.awaitShutdown(10);

            server.stop();

        } finally {
            IoUtils.safeClose(connection);
        }
    }
}
