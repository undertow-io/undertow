/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
import io.undertow.connector.PooledByteBuffer;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.StopServerWithExternalWorkerUtils;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import io.undertow.util.WorkerUtils;
import io.undertow.websockets.core.UTF8Output;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.xnio.channels.StreamSourceChannel;

/**
 * Test class that emulates a client that does not update the window for some
 * time. The server is started with a WRITE_TIMEOUT set and different test
 * methods checks that the connection is closed after the timeout if not
 * updating the window.
 *
 * @author rmartinc
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
@HttpOneOnly
public class Http2WindowUpdateTimeoutTestCase {

    private static final String message = "01234567";
    public static final String MESSAGE = "/message";
    private static final int TEST_WRITE_TIMEOUT = 5000; // The timeout to set in the server

    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);
    private static final AttachmentKey<IOException> RESPONSE_EXCEPTION = AttachmentKey.create(IOException.class);

    private static final OptionMap DEFAULT_OPTIONS;
    private static XnioWorker worker;
    private static Undertow server;
    private static URL ADDRESS;


    static {
        final OptionMap.Builder builder = OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .set(Options.WORKER_NAME, "Client");

        DEFAULT_OPTIONS = builder.getMap();
    }

    @BeforeClass
    public static void beforeClass() throws IOException {

        int port = DefaultServer.getHostPort("default");

        final PathHandler path = new PathHandler()
                .addExactPath(MESSAGE, new HttpHandler() {

                    /**
                     * The method just returns the message string N times.
                     * Parameter <em>size</em> is the total size in bytes to
                     * return and <em>blocking</em> is used to use a blocking or
                     * async sender.
                     */
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        final boolean blocking = Boolean.parseBoolean(exchange.getQueryParameters().get("blocking").getFirst());
                        final int size = Integer.parseUnsignedInt(exchange.getQueryParameters().get("size").getFirst());

                        if (blocking) {
                            if (exchange.isInIoThread()) {
                                // do blocking
                                exchange.startBlocking();
                                exchange.dispatch(this);
                                return;
                            }
                        }
                        exchange.setStatusCode(StatusCodes.OK);
                        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, size);
                        final Sender sender = exchange.getResponseSender();
                        sender.send(message, new IoCallback() {

                            int remaining = size - message.length();

                            @Override
                            public void onComplete(HttpServerExchange exchange, Sender sender) {
                                int toWrite = remaining > message.length()? message.length() : remaining;
                                remaining = remaining - toWrite;
                                if (!exchange.isComplete()) {
                                    if (remaining > 0) {
                                        sender.send(message, this);
                                    } else {
                                        sender.send(message.substring(0, toWrite));
                                    }
                                } else {
                                    sender.close();
                                }
                            }

                            @Override
                            public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                                IoCallback.END_EXCHANGE.onException(exchange, sender, exception);
                            }
                        });
                    }
                });

        server = Undertow.builder()
                .setByteBufferPool(DefaultServer.getBufferPool())
                .addHttpsListener(port + 1, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.WRITE_TIMEOUT, TEST_WRITE_TIMEOUT) // set the timeout
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        if (!exchange.getProtocol().equals(Protocols.HTTP_2_0)) {
                            throw new RuntimeException("Not HTTP/2");
                        }
                        path.handleRequest(exchange);
                    }
                })
                .build();

        server.start();
        ADDRESS = new URL("https://" + DefaultServer.getHostAddress() + ":" + (port + 1));

        // Create xnio worker
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null, DEFAULT_OPTIONS);
        worker = xnioWorker;
    }

    @AfterClass
    public static void afterClass() {
        if (server != null) {
            server.stop();
        }
        if (worker != null) {
            StopServerWithExternalWorkerUtils.stopWorker(worker);
        }
    }

    /**
     * Test method that executes a GET to the message endpoint. If
     * <em>expectedTimeout</em> is true then the get is supposed to fail and a
     * IOException should be attached to the result. If <em>expectedTimeout</em>
     * is false then the get should finish OK with the body attached. The size
     * to be returned by the handler should be bigger than the buffer size
     * to trigger some different writes than can hang/timeout.
     *
     * @param blocking Blocking parameter to send
     * @param size Size parameter to send
     * @param timeout The timeout to emulate the non-updating client,
     * reads will be suspended for this time
     * @param repetitiveTimeout If the timeout should happen between each call
     * or just once at the beginning
     * @param expectedTimedout If the test expects a timeout exception or OK
     * @throws Exception Some unexpected error in the test
     */
    public void test(final boolean blocking, final int size, final int timeout, final boolean repetitiveTimeout, final boolean expectedTimedout) throws Exception {
        Assert.assertTrue("Size should be greater than " + message.length(), size >= message.length());

        // create the client with a small window size
        final UndertowClient client = UndertowClient.getInstance();
        final ClientConnection connection = client.connect(ADDRESS.toURI(), worker,
                new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext()),
                DefaultServer.getBufferPool(),
                OptionMap.builder().set(UndertowOptions.ENABLE_HTTP2, true)
                        .set(UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE, 2048).getMap()).get();

        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(MESSAGE + "?blocking=" + blocking + "&size=" + size);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                connection.sendRequest(request, createClientCallback(timeout, repetitiveTimeout, responses, latch));
            });

            Assert.assertTrue("Response did not come in the specified time", latch.await(TEST_WRITE_TIMEOUT * 4, TimeUnit.MILLISECONDS));
            Assert.assertEquals("Incorrect number of responses returned", 1, responses.size());
            ClientResponse response = responses.iterator().next();
            Assert.assertEquals("Incorrect status code", StatusCodes.OK, response.getResponseCode());
            final IOException exception = response.getAttachment(RESPONSE_EXCEPTION);
            final String body = response.getAttachment(RESPONSE_BODY);
            if (expectedTimedout) {
                // expected timeout so check exception and not the string body
                Assert.assertNull("Body was returned when timeout was expected", body);
                Assert.assertNotNull("Exception not present when timeout was expected", exception);
                Assert.assertTrue("The exception is not a reset", exception.getMessage().contains("Http2 stream was reset"));
            } else {
                // timeout not expected check response body
                Assert.assertNull("Exception was returned when timeout was not expected", exception);
                Assert.assertNotNull("Body not present when timeout was not expected", body);
                Assert.assertEquals("Incorrect reponse size", size, body.length());
                for (int i = 0; i < body.length(); i += message.length()) {
                    Assert.assertEquals("Incorect response at position=" + i,
                            message.substring(0, i + message.length() > body.length()? body.length() % message.length() : message.length()),
                            body.substring(i, i + message.length() > body.length()? body.length() : i + message.length()));
                }
            }
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    @Test
    public void testBlockingSuccess() throws Exception {
        // test blocking with a very low timeout, it should work OK
        test(true, DefaultServer.getBufferPool().getBufferSize() * 4, 100, false, false);
    }

    @Test
    public void testNonBlockingSuccess() throws Exception {
        // test non-blocking with a very low timeout, it should work OK
        test(false, DefaultServer.getBufferPool().getBufferSize() * 4, 100, false, false);
    }

    @Test
    public void testBlockingTimeout() throws Exception {
        // test blocking with a big timeout, it should fail by timeout
        test(true, DefaultServer.getBufferPool().getBufferSize() * 4, TEST_WRITE_TIMEOUT * 2, false, true);
    }

    @Test
    public void testNonBlockingTimeout() throws Exception {
        // test non-blocking with a big timeout, it should fail by timeout
        test(true, DefaultServer.getBufferPool().getBufferSize() * 4, TEST_WRITE_TIMEOUT * 2, false, true);
    }

    @Test
    public void testBlockingTimeoutRepetitive() throws Exception {
        // test blocking with timeout but repetitive, should work OK
        test(true, DefaultServer.getBufferPool().getBufferSize() * 4, TEST_WRITE_TIMEOUT / 10, true, false);
    }

    @Test
    public void testNonBlockingRepetitive() throws Exception {
        // test non-blocking with timeout but repetitive, should work OK
        test(false, DefaultServer.getBufferPool().getBufferSize() * 4, TEST_WRITE_TIMEOUT / 10, true, false);
    }

    private ClientCallback<ClientExchange> createClientCallback(final int timeout, boolean repetitiveTimeout,
            final List<ClientResponse> responses, final CountDownLatch latch) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                result.setResponseListener(new ClientCallback<ClientExchange>() {

                    @Override
                    public void completed(final ClientExchange result) {
                        responses.add(result.getResponse());
                        // suspend reads and emulate client not updating the window
                        result.getResponseChannel().suspendReads();
                        // attach the read listener
                        ReadChannelListener listener = new ReadChannelListener(result, latch, repetitiveTimeout? timeout : -1);
                        result.getResponseChannel().getReadSetter().set(listener);
                        // but just start the reads after the timeout
                        if (timeout > 0) {
                            WorkerUtils.executeAfter(result.getResponseChannel().getIoThread(), listener, timeout, TimeUnit.MILLISECONDS);
                        } else {
                            listener.run();
                        }
                    }

                    @Override
                    public void failed(IOException e) {
                        e.printStackTrace();
                        result.getResponse().putAttachment(RESPONSE_EXCEPTION, e);
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

    /**
     * Read listener that reads all the data in the channel and attach it to
     * the RESPONSE_BODY. If exception the exception is attached to the
     * RESPONSE_EXCEPTION. It also implements runnable to be used as a task.
     * If timeout is passed > 0 reads are never resumed and the channel is read
     * via tasks doing timeouts for every chunk/window read.
     */
    public static class ReadChannelListener implements ChannelListener<StreamSourceChannel>, Runnable {

        private final ClientExchange result;
        private final UTF8Output string;
        private final CountDownLatch latch;
        private final int timeout;

        public ReadChannelListener(final ClientExchange result, CountDownLatch latch, int timeout) {
            this.string = new UTF8Output();
            this.result = result;
            this.latch = latch;
            this.timeout = timeout;
        }

        @Override
        public void handleEvent(final StreamSourceChannel channel) {
            PooledByteBuffer resource = result.getConnection().getBufferPool().allocate();
            ByteBuffer buffer = resource.getBuffer();
            try {
                int r;
                do {
                    r = channel.read(buffer);
                    switch (r) {
                        case 0:
                            if (timeout > 0) {
                                channel.suspendReads();
                                WorkerUtils.executeAfter(result.getResponseChannel().getIoThread(), this, timeout, TimeUnit.MILLISECONDS);
                            } else if (!channel.isReadResumed()) {
                                channel.resumeReads();
                            }
                            return;
                        case -1:
                            result.getResponse().putAttachment(RESPONSE_BODY, string.extract());
                            IoUtils.safeClose(channel);
                            latch.countDown();
                            break;
                        default:
                            buffer.flip();
                            string.write(buffer);
                            if (timeout > 0) {
                                channel.suspendReads();
                                WorkerUtils.executeAfter(result.getResponseChannel().getIoThread(), this, timeout, TimeUnit.MILLISECONDS);
                                return;
                            }
                            break;
                    }
                } while (r > 0);
            } catch (IOException e) {
                e.printStackTrace();
                result.getResponse().putAttachment(RESPONSE_EXCEPTION, e);
                latch.countDown();
            } finally {
                resource.close();
            }
        }

        @Override
        public void run() {
            this.handleEvent(result.getResponseChannel());
        }
    }
}
