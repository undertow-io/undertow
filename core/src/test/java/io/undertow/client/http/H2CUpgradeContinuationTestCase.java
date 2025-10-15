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
import io.undertow.connector.ByteBufferPool;
import io.undertow.io.Receiver;
import io.undertow.io.Sender;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.protocol.http2.Http2UpgradeHandler;
import io.undertow.testutils.DebuggingSlicePool;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
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
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;

/**
 * <p>Test that uses H2C upgrade and tries to send different number of headers
 * for a GET/POST request. The idea is that the byte buffer used in the client
 * and server is small. That way when sending a big number of headers the
 * HEADERS frame is not enough to contain all the data and some CONTINUATION
 * frames are needed. The test method also tries with different sizes of DATA
 * to force several DATA frames to be sent when using POST method.</p>
 *
 * @author rmartinc
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class H2CUpgradeContinuationTestCase {

    private static final String HEADER_PREFFIX = "custom-header-";
    private static final String ECHO_PATH = "/echo";
    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

    private static ByteBufferPool smallPool;
    private static XnioWorker worker;
    private static Undertow server;

    /**
     * Just a handler that receives the request and sends back all the custom
     * headers received and (if data received) returns the same data (empty
     * response otherwise).
     * @param exchange The HttpServerExchange
     */
    private static void sendEchoResponse(final HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.OK);
        // add the custom headers received
        for (HeaderValues header : exchange.getRequestHeaders()) {
            if (header.getFirst().startsWith(HEADER_PREFFIX)) {
                exchange.getResponseHeaders().putAll(header.getHeaderName(), header.subList(0, header.size()));
            }
        }
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
        // server and client pool is using 1024 for the buffer size
        smallPool = new DebuggingSlicePool(new DefaultByteBufferPool(true, 1024, 1000, 10, 100));

        final PathHandler path = new PathHandler()
                .addExactPath(ECHO_PATH, new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        sendEchoResponse(exchange);
                    }
                });

        server = Undertow.builder()
                .setByteBufferPool(smallPool)
                .addHttpListener(DefaultServer.getHostPort("default") + 1, DefaultServer.getHostAddress("default"), new Http2UpgradeHandler(path))
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
        if (smallPool != null) {
            smallPool.close();
            smallPool = null;
        }
    }

    /**
     * Method that sends a GET or POST request adding count number of custom
     * headers and sending contentLength data. GET is used if no content length
     * is passed, POST if contentLength is greater than 0.
     * @param connection The connection to use
     * @param requestCount The number of requests to send
     * @param headersCount The number of custom headers to send
     * @param contentLength The content length to send (POST method used if >0)
     * @throws Exception Some error
     */
    private void sendRequest(ClientConnection connection, int requestCount, int headersCount, int contentLength) throws Exception {
        final CountDownLatch latch = new CountDownLatch(requestCount);
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contentLength; i++) {
            sb.append(i % 10);
        }
        final String content = sb.length() > 0? sb.toString() : null;
        connection.getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < requestCount; i++) {
                    final ClientRequest request = new ClientRequest()
                            .setMethod(contentLength > 0 ? Methods.POST : Methods.GET)
                            .setPath(ECHO_PATH);
                    request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                    if (contentLength > 0) {
                        request.getRequestHeaders().put(Headers.CONTENT_LENGTH, contentLength);
                    }
                    for (int j = 0; j < headersCount; j++) {
                        request.getRequestHeaders().put(new HttpString(HEADER_PREFFIX + j), HEADER_PREFFIX + j);
                    }
                    connection.sendRequest(request, createClientCallback(responses, latch, content));
                }
            }
        });

        latch.await(10, TimeUnit.SECONDS);

        Assert.assertEquals("No responses received from server in 10s", requestCount, responses.size());
        for (int i = 0; i < requestCount; i++) {
            Assert.assertEquals("Response " + i + " code was not OK", StatusCodes.OK, responses.get(i).getResponseCode());
            Assert.assertEquals("Incorrect data received for response " + i, contentLength > 0 ? content : "", responses.get(i).getAttachment(RESPONSE_BODY));
            int headersReturned = 0;
            for (HeaderValues header : responses.get(i).getResponseHeaders()) {
                if (header.getFirst().startsWith(HEADER_PREFFIX)) {
                    headersReturned += header.size();
                }
            }
            Assert.assertEquals("Incorrect number of headers returned for response " + i, headersCount, headersReturned);
        }
    }

    /**
     * The real test that sends several GET and POST requests with different
     * number of headers and different content length.
     * @throws Exception  Some error
     */
    @Test
    public void testDifferentSizes() throws Exception {
        final UndertowClient client = UndertowClient.getInstance();

        // the client connection uses the small byte-buffer of 1024 to force the continuation frames
        final ClientConnection connection = client.connect(
                new URI("http://" + DefaultServer.getHostAddress() + ":" + (DefaultServer.getHostPort("default") + 1)),
                worker, new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext()),
                smallPool, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            // the first request triggers the upgrade to H2C
            sendRequest(connection, 1, 0, 0);
            // send several requests with different sizes for headers and data
            sendRequest(connection, 10, 10, 0);
            sendRequest(connection, 10, 100, 0);
            sendRequest(connection, 10, 150, 0);
            sendRequest(connection, 10, 1, 10);
            sendRequest(connection, 10, 0, 2000);
            sendRequest(connection, 10, 150, 2000);
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    /**
     * Create the callback to receive the response and assign it to the list.
     * @param responses The list where the response will be added
     * @param latch The latch to count down when the response is received
     * @param message The message to send if it's a POST message (if null nothing is send)
     * @return The created callback
     */
    private static ClientCallback<ClientExchange> createClientCallback(final List<ClientResponse> responses, final CountDownLatch latch, String message) {
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
                try {
                    result.getRequestChannel().shutdownWrites();
                    if (!result.getRequestChannel().flush()) {
                        result.getRequestChannel().getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, null));
                        result.getRequestChannel().resumeWrites();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    latch.countDown();
                }
            }

            @Override
            public void failed(IOException e) {
                e.printStackTrace();
                latch.countDown();
            }
        };
    }
}
