/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

package io.undertow.server.protocol.http2;

import static io.undertow.testutils.StopServerWithExternalWorkerUtils.stopWorker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.server.RequestParseErrorListener;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.UrlDecodeException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * Tests {@link RequestParseErrorListener} on the HTTP/2 connector, and pins the fix for the
 * {@link UrlDecodeException} that {@code Http2ReceiveListener} previously did not catch.
 * <p>
 * Before that fix, an invalid percent encoded sequence in the {@code :path} pseudo header propagated
 * to the frame level {@code catch (Throwable)}, which logged at {@code ERROR} and closed the whole
 * HTTP/2 connection -- taking down every other multiplexed stream on it and sending no response at
 * all. The connection reuse assertion below is what guards against a regression.
 * <p>
 * This uses {@code h2c-prior} (plaintext HTTP/2 with prior knowledge) rather than ALPN, so that it
 * runs everywhere, including on macOS where the ALPN based HTTP/2 tests are skipped (UNDERTOW-2635).
 *
 * @author krowles
 */
public class Http2RequestParseErrorListenerTestCase {

    private static final OptionMap CLIENT_OPTIONS = OptionMap.builder()
            .set(Options.WORKER_IO_THREADS, 4)
            .set(Options.TCP_NODELAY, true)
            .set(Options.KEEP_ALIVE, true)
            .set(Options.WORKER_NAME, "Client")
            .getMap();

    private Undertow undertow;
    private XnioWorker worker;

    @After
    public void cleanup() throws Exception {
        if (worker != null) {
            stopWorker(worker);
            worker = null;
        }
        if (undertow != null) {
            undertow.stop();
            undertow = null;
            // sleep to prevent BindException (Address already in use) when running the CI
            Thread.sleep(1000);
        }
    }

    @Test
    public void parseErrorIsReportedAndTheConnectionSurvives() throws Exception {
        final AtomicInteger invocations = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<String> transportProtocol = new AtomicReference<>();
        final AtomicReference<String> protocolSeenByHandler = new AtomicReference<>();

        undertow = Undertow.builder()
                .addHttpListener(0, DefaultServer.getHostAddress())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setRequestParseErrorListener((e, context) -> {
                    error.set(e);
                    transportProtocol.set(context.getConnection().getTransportProtocol());
                    invocations.incrementAndGet();
                })
                .setHandler(exchange -> {
                    protocolSeenByHandler.set(exchange.getProtocol().toString());
                    exchange.setStatusCode(StatusCodes.OK);
                    exchange.endExchange();
                })
                .build();
        undertow.start();

        final InetSocketAddress address = (InetSocketAddress) undertow.getListenerInfo().get(0).getAddress();
        final URI uri = new URI("h2c-prior", null, DefaultServer.getHostAddress(), address.getPort(), "/", null, null);

        worker = Xnio.getInstance().createWorker(null, CLIENT_OPTIONS);
        final ClientConnection connection = UndertowClient.getInstance()
                .connect(uri, worker, DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true))
                .get();
        try {
            //a valid request first, to confirm we really are speaking HTTP/2
            Assert.assertEquals(StatusCodes.OK, sendRequest(connection, "/ok"));
            Assert.assertEquals(Protocols.HTTP_2_0_STRING, protocolSeenByHandler.get());
            Assert.assertEquals("the listener must not fire for a request that parses", 0, invocations.get());

            //%%41 slips past the byte level validation and fails in URLUtils.decode
            Assert.assertEquals(StatusCodes.BAD_REQUEST, sendRequest(connection, "/somePath?a=%%41"));

            Assert.assertEquals(1, invocations.get());
            Assert.assertTrue("expected a UrlDecodeException but got " + error.get(),
                    error.get() instanceof UrlDecodeException);
            Assert.assertNotNull("the transport protocol should be reported", transportProtocol.get());

            //the regression assertion: the rejected stream must not have killed the connection
            Assert.assertTrue("the HTTP/2 connection was closed by a single malformed request",
                    connection.isOpen());
            Assert.assertEquals(StatusCodes.OK, sendRequest(connection, "/stillWorks"));
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    private static int sendRequest(final ClientConnection connection, final String path) throws Exception {
        final CompletableFuture<Integer> status = new CompletableFuture<>();
        connection.getIoThread().execute(() -> {
            final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(path);
            request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
            connection.sendRequest(request, new ClientCallback<ClientExchange>() {
                @Override
                public void completed(final ClientExchange result) {
                    result.setResponseListener(new ClientCallback<ClientExchange>() {
                        @Override
                        public void completed(final ClientExchange result) {
                            final int code = result.getResponse().getResponseCode();
                            new StringReadChannelListener(result.getConnection().getBufferPool()) {
                                @Override
                                protected void stringDone(final String string) {
                                    status.complete(code);
                                }

                                @Override
                                protected void error(final IOException e) {
                                    //the response body is not interesting; the status code is
                                    status.complete(code);
                                }
                            }.setup(result.getResponseChannel());
                        }

                        @Override
                        public void failed(final IOException e) {
                            status.completeExceptionally(e);
                        }
                    });
                    try {
                        result.getRequestChannel().shutdownWrites();
                        if (!result.getRequestChannel().flush()) {
                            result.getRequestChannel().getWriteSetter()
                                    .set(ChannelListeners.flushingChannelListener(null, null));
                            result.getRequestChannel().resumeWrites();
                        }
                    } catch (IOException e) {
                        status.completeExceptionally(e);
                    }
                }

                @Override
                public void failed(final IOException e) {
                    status.completeExceptionally(e);
                }
            });
        });
        return status.get(10, TimeUnit.SECONDS);
    }
}
