/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.server.protocol.http2;

import static io.undertow.testutils.StopServerWithExternalWorkerUtils.stopWorker;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

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
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;

public class HTTP2OnListenerTestCase {
    private static final Logger log = Logger.getLogger(Http2EndExchangeTestCase.class);
    private static final String MESSAGE = "/message";

    private static final OptionMap DEFAULT_OPTIONS;

    static {
        final OptionMap.Builder builder = OptionMap.builder().set(Options.WORKER_IO_THREADS, 8).set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true).set(Options.WORKER_NAME, "Client");

        DEFAULT_OPTIONS = builder.getMap();
    }

    @Test
    public void testExpectedHTTP2() throws Exception {
        doTest(true);
    }

    @Test
    public void testExpectedHTTP1() throws Exception {
        doTest(false);
    }

    public void doTest(final boolean isHttp2) throws Exception {

        int port = DefaultServer.getHostPort("default");

        final CountDownLatch requestStartedLatch = new CountDownLatch(1);

        final CompletableFuture<String> testResult = new CompletableFuture<>();

        Undertow server = Undertow.builder()
                .addListener(new Undertow.ListenerBuilder().setType(Undertow.ListenerType.HTTPS)
                        .setHost(DefaultServer.getHostAddress()).setPort(port + 1).setHttp2Enabled(isHttp2)
                        .setSslContext(DefaultServer.getServerSslContext()))
                .setSocketOption(Options.REUSE_ADDRESSES, true).setHandler(new BlockingHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        requestStartedLatch.countDown();
                        if (isHttp2 != exchange.getProtocol().equals(Protocols.HTTP_2_0)) {
                            testResult.completeExceptionally(new RuntimeException("Wrong protocol"));
                            return;
                        } else {
                            testResult.complete("PASSED");
                        }

                        exchange.setStatusCode(200);
                        exchange.getOutputStream().flush();
                    }
                })).build();
        server.start();
        URI ADDRESS = new URI("https://" + DefaultServer.getHostAddress() + ":" + (port + 1));

        // Create xnio worker
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null, DEFAULT_OPTIONS);
        try {

            final UndertowClient client = createClient();

            final ClientConnection connection = client.connect(ADDRESS, xnioWorker,
                    new UndertowXnioSsl(xnioWorker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext()),
                    DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
            try {
                connection.getIoThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(MESSAGE);
                        request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                        connection.sendRequest(request, new ClientCallback<ClientExchange>() {
                            @Override
                            public void completed(ClientExchange result) {
                                try {
                                    log.debug("Callback invoked");
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                requestStartedLatch.await(10, TimeUnit.SECONDS);
                                                result.getRequestChannel().getIoThread().execute(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        IoUtils.safeClose(result.getConnection());
                                                        log.debug("Closed Connection");
                                                    }
                                                });
                                            } catch (Exception e) {
                                                testResult.completeExceptionally(e);
                                            }

                                        }
                                    }).start();
                                } catch (Exception e) {
                                    testResult.completeExceptionally(e);
                                }
                            }

                            @Override
                            public void failed(IOException e) {
                                testResult.completeExceptionally(e);
                            }
                        });

                    }

                });

                Assert.assertEquals("PASSED", testResult.get(10, TimeUnit.SECONDS));
            } finally {
                IoUtils.safeClose(connection);
            }
        } finally {
            stopWorker(xnioWorker);
            server.stop();
            // sleep 1 s to prevent BindException (Address already in use) when running the CI
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        }
    }

    static UndertowClient createClient() {
        return UndertowClient.getInstance();
    }
}
