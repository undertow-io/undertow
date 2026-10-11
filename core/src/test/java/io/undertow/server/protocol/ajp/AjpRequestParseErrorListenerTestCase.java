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

package io.undertow.server.protocol.ajp;

import static io.undertow.testutils.StopServerWithExternalWorkerUtils.stopWorker;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.Undertow;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.server.RequestParseErrorListener;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.UrlDecodeException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * Tests {@link RequestParseErrorListener} on the AJP connector.
 * <p>
 * AJP is the one protocol whose parser deliberately does not throw on a decode failure: it records
 * {@code AjpRequestParseState.badRequest} and lets the read listener produce the 400 through the real
 * exchange. This test therefore covers a different code path from the HTTP/1.1 one, and also pins the
 * fact that the original exception is carried through to the listener rather than being replaced by a
 * generic one.
 *
 * @author krowles
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class AjpRequestParseErrorListenerTestCase {

    private static final int AJP_PORT = DefaultServer.getHostPort() + 11;

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
    public void parseErrorIsReportedWithTheOriginalCause() throws Exception {
        final AtomicInteger invocations = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<String> transportProtocol = new AtomicReference<>();
        final AtomicReference<String> requestPath = new AtomicReference<>();

        undertow = Undertow.builder()
                .addListener(new Undertow.ListenerBuilder()
                        .setType(Undertow.ListenerType.AJP)
                        .setHost(DefaultServer.getHostAddress())
                        .setPort(AJP_PORT))
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setRequestParseErrorListener((e, context) -> {
                    error.set(e);
                    transportProtocol.set(context.getConnection().getTransportProtocol());
                    if (context.getExchange() != null) {
                        requestPath.set(context.getExchange().getRequestPath());
                    }
                    invocations.incrementAndGet();
                })
                .setHandler(exchange -> {
                    exchange.setStatusCode(StatusCodes.OK);
                    exchange.endExchange();
                })
                .build();
        undertow.start();

        worker = Xnio.getInstance().createWorker(null, CLIENT_OPTIONS);
        final URI address = new URI("ajp://" + DefaultServer.getHostAddress() + ":" + AJP_PORT);
        final ClientConnection connection = UndertowClient.getInstance()
                .connect(address, worker, DefaultServer.getBufferPool(), OptionMap.EMPTY)
                .get();
        try {
            Assert.assertEquals(StatusCodes.OK, sendRequest(connection, "/ok"));
            Assert.assertEquals("the listener must not fire for a request that parses", 0, invocations.get());

            Assert.assertEquals(StatusCodes.BAD_REQUEST, sendRequest(connection, "/somePath?a=%%41"));

            Assert.assertEquals(1, invocations.get());
            Assert.assertTrue("the cause captured by the AJP parser should be reported, but got " + error.get(),
                    error.get() instanceof UrlDecodeException);
            Assert.assertEquals("ajp", transportProtocol.get());
            Assert.assertEquals("/somePath", requestPath.get());
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
