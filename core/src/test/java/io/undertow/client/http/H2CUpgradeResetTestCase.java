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
package io.undertow.client.http;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClient;
import io.undertow.io.Receiver;
import io.undertow.io.Sender;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.protocols.http2.Http2StreamSourceChannel;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.protocol.http2.Http2UpgradeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.StringWriteChannelListener;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * <p>Test for H2C that can send a RST to the just received request.</p>
 *
 * @author rmartinc
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class H2CUpgradeResetTestCase {

    private static final String ECHO_PATH = "/echo";
    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

    private static XnioWorker worker;
    private static Undertow server;

    /**
     * Just a handler that receives the request and sends back the same data
     * (empty response otherwise).
     * @param exchange The HttpServerExchange
     */
    private static void sendEchoResponse(final HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.OK);
        // response using echo or empty string
        if (exchange.getRequestContentLength() > 0) {
            exchange.getRequestReceiver().receiveFullString(new Receiver.FullStringCallback() {
                @Override
                public void handle(HttpServerExchange exchange, String message) {
                    exchange.getResponseSender().send(message);
                }
            });
        } else {
            final Sender sender = exchange.getResponseSender();
            sender.send("");
        }
    }

    /**
     * Initializes the server with the H2C handler and adds the echo handler to
     * manage the requests.
     * @throws IOException Some error
     */
    @BeforeClass
    public static void beforeClass() throws IOException {
        final PathHandler path = new PathHandler()
                .addExactPath(ECHO_PATH, new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        sendEchoResponse(exchange);
                    }
                });

        server = Undertow.builder()
                .addHttpListener(DefaultServer.getHostPort() + 1, DefaultServer.getHostAddress(), new Http2UpgradeHandler(path))
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .build();
        server.start();

        // Create xnio worker
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null, OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .getMap());
        worker = xnioWorker;
    }

    /**
     * Stops server and worker.
     */
    @AfterClass
    public static void afterClass() {
        if (server != null) {
            server.stop();
        }
        if (worker != null) {
            worker.shutdown();
        }
    }

    /**
     * Method that sends a POST request to the server with a message of
     * size equals to contentLength. If reset is true the callback will send
     * a RST after the response is received.
     * @param connection The connection to use
     * @param contentLength The content length to send
     * @param reset If true a reset is send to the received frame ID after the response is received
     * @throws Exception Some error
     */
    private void sendRequest(ClientConnection connection, int contentLength, boolean reset) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contentLength; i++) {
            sb.append(i % 10);
        }
        final String content = sb.length() > 0? sb.toString() : null;
        connection.getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                final ClientRequest request = new ClientRequest()
                        .setMethod(Methods.POST)
                        .setPath(ECHO_PATH);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                request.getRequestHeaders().put(Headers.CONTENT_LENGTH, contentLength);
                connection.sendRequest(request, createClientCallback(responses, latch, content, reset));
            }
        });

        latch.await(10, TimeUnit.SECONDS);

        Assert.assertEquals("No response received from server in 10s", 1, responses.size());
        Assert.assertEquals("Response code was not OK", StatusCodes.OK, responses.get(0).getResponseCode());
        Assert.assertEquals("Incorrect data received for response", contentLength > 0 ? content : "", responses.get(0).getAttachment(RESPONSE_BODY));
    }

    /**
     * The real test that sends several POST requests with and without reset.
     * @throws Exception Some error
     */
    @Test
    public void testUpgradeWithReset() throws Exception {
        final UndertowClient client = UndertowClient.getInstance();

        // the client connection uses the small byte-buffer of 1024 to force the continuation frames
        final ClientConnection connection = client.connect(
                new URI("http://" + DefaultServer.getHostAddress() + ":" + (DefaultServer.getHostPort() + 1)),
                worker, new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext()),
                DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            // the first request triggers the upgrade to H2C and sends a RST
            sendRequest(connection, 10, true);
            // send several requests with and without reset
            sendRequest(connection, 10, false);
            sendRequest(connection, 10, true);
            sendRequest(connection, 10, false);
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    /**
     * Create the callback to receive the response and assign it to the list.
     * @param responses The list where the response will be added
     * @param latch The latch to count down when the response is received
     * @param message The message to send if it's a POST message (if null nothing is send)
     * @param boolean reset if true a RST is sent for the received the frame ID after completed
     * @return The created callback
     */
    private static ClientCallback<ClientExchange> createClientCallback(final List<ClientResponse> responses, final CountDownLatch latch,
            String message, boolean reset) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                if (message != null) {
                    new StringWriteChannelListener(message).setup(result.getRequestChannel());
                }
                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(final ClientExchange result) {
                        responses.add(result.getResponse());
                        new StringReadChannelListener(result.getConnection().getBufferPool()) {

                            @Override
                            protected void stringDone(String string) {
                                result.getResponse().putAttachment(RESPONSE_BODY, string);
                                if (reset) {
                                    Http2StreamSourceChannel res = (Http2StreamSourceChannel) result.getResponseChannel();
                                    res.getHttp2Channel().sendRstStream(res.getStreamId(), Http2Channel.ERROR_STREAM_CLOSED);
                                }
                                latch.countDown();
                            }

                            @Override
                            protected void error(IOException e) {
                                e.printStackTrace();
                                latch.countDown();
                            }
                        }.setup(result.getResponseChannel());
                    }

                    @Override
                    public void failed(IOException e) {
                        e.printStackTrace();
                        latch.countDown();
                    }
                });
            }

            @Override
            public void failed(IOException e) {
                e.printStackTrace();
                latch.countDown();
            }
        };
    }
}