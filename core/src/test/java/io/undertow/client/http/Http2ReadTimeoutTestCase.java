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
import io.undertow.io.IoCallback;
import io.undertow.io.Receiver.ErrorCallback;
import io.undertow.io.Receiver.PartialBytesCallback;
import io.undertow.io.Sender;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.StopServerWithExternalWorkerUtils;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.WorkerUtils;
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
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;

/**
 * <p>Test class for the READ_TIMEOUT in the HTTP2 listener.</p>
 *
 * @author rmartinc
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class Http2ReadTimeoutTestCase {

    private static final String message = "012345678901234567890123456789";
    public static final String MESSAGE = "/message";

    private static final int READ_TIMEOUT = 5000;
    private static final OptionMap DEFAULT_OPTIONS;
    private static XnioWorker worker;
    private static Undertow server;
    private static URL ADDRESS;

    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

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
                     * The method just returns the size of the data received.
                     */
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        final boolean blocking = Boolean.parseBoolean(exchange.getQueryParameters().get("blocking").getFirst());
                        if (blocking) {
                            if (exchange.isInIoThread()) {
                                // do blocking
                                exchange.startBlocking();
                                exchange.dispatch(this);
                                return;
                            }
                        }
                        exchange.setStatusCode(StatusCodes.OK);
                        ReceiverCallback callback = new ReceiverCallback(exchange.getResponseSender());
                        exchange.getRequestReceiver().receivePartialBytes(callback, callback);
                    }
                });

        server = Undertow.builder()
                .setByteBufferPool(DefaultServer.getBufferPool())
                .addHttpsListener(port + 1, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.READ_TIMEOUT, READ_TIMEOUT)
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

    @Test
    public void testBlockingSuccess() throws Exception {
        // test blocking stopping writes less that READ_TIMEOUT
        test(true, message.length() * 3, READ_TIMEOUT / 2, false, false);
    }

    @Test
    public void testNonBlockingSuccess() throws Exception {
        // test non-blocking stopping writes less that READ_TIMEOUT
        test(false, message.length() * 3, READ_TIMEOUT / 2, false, false);
    }

    @Test
    public void testBlockingException() throws Exception {
        // test blocking stopping writes more that READ_TIMEOUT => exception expected
        test(true, message.length() * 3, READ_TIMEOUT * 2, false, true);
    }

    @Test
    public void testNonBlockingException() throws Exception {
        // test non-blocking stopping writes more that READ_TIMEOUT => exception expected
        test(false, message.length() * 3, READ_TIMEOUT * 2, false, true);
    }

    @Test
    public void testBlockingRepetitiveSuccess() throws Exception {
        // test blocking repetitive to check that tasks are updated
        test(true, message.length() * 7, READ_TIMEOUT / 5, true, false);
    }

    @Test
    public void testNonBlockingRepetitiveSuccess() throws Exception {
        // test non-blocking repetitive to check that tasks are updated
        test(false, message.length() * 7, READ_TIMEOUT / 5, true, false);
    }

    /**
     * The internal test method. The client sends a POST but it starts to write
     * the post data after a timeout. If repetitiveTimeout is true the client
     * writes in chunks (message size) waiting timeout millis between every
     * chunk. The test waits a max time of READ_TIME * 2.
     *
     * @param blocking true use blocking, false use non-blocking
     * @param size The size of the message to send
     * @param timeout The initial timeout before writing to the server
     * @param repetitiveTimeout If the timeout should be done repetitively
     * @param expectedException true if exception is expected, false if not
     * @throws Exception Some error
     */
    private void test(final boolean blocking, final int size, final int timeout, final boolean repetitiveTimeout, boolean expectedException) throws Exception {
        // create the client with a small window size
        final UndertowClient client = UndertowClient.getInstance();
        final ClientConnection connection = client.connect(ADDRESS.toURI(), worker,
                new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext()),
                DefaultServer.getBufferPool(),
                OptionMap.builder()
                        .set(UndertowOptions.ENABLE_HTTP2, true)
                        .getMap()
                ).get();

        final List<ClientResponseOrException> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            long startTime = System.currentTimeMillis();
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.POST).setPath(MESSAGE + "?blocking=" + blocking);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                request.getRequestHeaders().put(Headers.CONTENT_LENGTH, size);
                connection.sendRequest(request, createClientCallback(size, timeout, repetitiveTimeout, responses, latch));
            });

            Assert.assertTrue("Response did not come in the specified time", latch.await(READ_TIMEOUT * 2, TimeUnit.MILLISECONDS));
            Assert.assertEquals("Incorrect number of responses returned", 1, responses.size());
            ClientResponseOrException response = responses.iterator().next();
            if (expectedException) {
                Assert.assertFalse("Expected exception but was a response", response.isResponse());
                Assert.assertTrue("The timeout was not triggered at READ_TIMEOUT", System.currentTimeMillis() - startTime < timeout);
            } else {
                Assert.assertTrue("Expected response but was a exception", response.isResponse());
                Assert.assertEquals("Incorrect status code", StatusCodes.OK, response.getResponse().getResponseCode());
                final String body = response.getResponse().getAttachment(RESPONSE_BODY);
                Assert.assertEquals("Unexpected size received", size, Integer.parseInt(body));
            }
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    private ClientCallback<ClientExchange> createClientCallback(final int size, final int timeout,
            final boolean repetitiveTimeout, final List<ClientResponseOrException> responses, final CountDownLatch latch) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                WriteChannelListener writeListener = new WriteChannelListener(result, size, repetitiveTimeout? timeout : 0);
                result.getRequestChannel().suspendWrites();
                result.getRequestChannel().getWriteSetter().set(writeListener);
                if (timeout > 0) {
                    // if timeout starts the listener after it
                    WorkerUtils.executeAfter(result.getRequestChannel().getIoThread(), writeListener, timeout, TimeUnit.MILLISECONDS);
                } else {
                    // no timeout, just start writing
                    writeListener.run();
                }
                result.setResponseListener(new ClientCallback<ClientExchange>() {

                    @Override
                    public void completed(ClientExchange result) {
                        responses.add(new ClientResponseOrException(result.getResponse()));
                        new StringReadChannelListener(DefaultServer.getBufferPool()) {

                            @Override
                            protected void stringDone(String string) {
                                result.getResponse().putAttachment(RESPONSE_BODY, string);
                                latch.countDown();
                            }

                            @Override
                            protected void error(IOException e) {
                                responses.add(new ClientResponseOrException(e));
                                latch.countDown();
                            }
                        }.setup(result.getResponseChannel());
                    }

                    @Override
                    public void failed(IOException e) {
                        responses.add(new ClientResponseOrException(e));
                        latch.countDown();
                    }
                });
            }

            @Override
            public void failed(IOException e) {
                responses.add(new ClientResponseOrException(e));
                latch.countDown();
            }
        };
    }

    /**
     * A partial bytes callback that counts the bytes received and writes the
     * final size back as the response.
     */
    private static class ReceiverCallback implements PartialBytesCallback, ErrorCallback {

        private final Sender sender;
        private int size;

        ReceiverCallback(Sender sender) {
            this.sender = sender;
            size = 0;
        }

        @Override
        public void handle(HttpServerExchange exchange, byte[] message, boolean last) {
            size += message.length;
            if (last) {
                sender.send(Integer.toString(size));
            }
        }

        @Override
        public void error(HttpServerExchange exchange, IOException e) {
            IoCallback.END_EXCHANGE.onException(exchange, sender, e);
        }
    }

    /**
     * A channel listener that writes the message the times needed until
     * <em>size</em> bytes are sent. If a timeout is passed the listener
     * uses task to write the data waiting the timeout between writing every
     * message.
     */
    private class WriteChannelListener implements Runnable, ChannelListener<StreamSinkChannel> {

        private final ClientExchange result;
        private int size;
        private final int timeout;
        private final ByteBuffer buffer;

        WriteChannelListener(ClientExchange result, int size, int timeout) {
            this.result = result;
            this.size = size;
            this.timeout = timeout;
            this.buffer = ByteBuffer.wrap(message.getBytes());
            wrapBuffer();
        }

        @Override
        public void run() {
            this.handleEvent(result.getRequestChannel());
        }

        @Override
        public void handleEvent(StreamSinkChannel channel) {
            try {
                int c;
                do {
                    c = channel.write(buffer);
                    size = size - c;
                    if (!buffer.hasRemaining() && size > 0) {
                        wrapBuffer();
                        if (timeout > 0) {
                            if (!channel.flush()) {
                                // force resume writes
                                c = 0;
                            }
                            break;
                        }
                    }
                } while (c > 0);

                if (size == 0) {
                    writeDone(channel);
                } else if (c > 0 && timeout > 0) {
                    channel.suspendWrites();
                    WorkerUtils.executeAfter(channel.getIoThread(), this, timeout, TimeUnit.MILLISECONDS);
                } else if (!channel.isWriteResumed()) {
                    channel.resumeWrites();
                }
            } catch (IOException e) {
                IoUtils.safeClose(channel);
            }
        }

        private void wrapBuffer() {
            buffer.position(0);
            if (size < buffer.capacity()) {
                buffer.limit(size);
            } else {
                buffer.limit(buffer.capacity());
            }
        }

        private void writeDone(final StreamSinkChannel channel) {
            try {
                channel.shutdownWrites();
                if (!channel.flush()) {
                    channel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                        c -> IoUtils.safeClose(c),
                        ChannelListeners.closingChannelExceptionHandler()));
                    channel.resumeWrites();
                }
            } catch (IOException e) {
                IoUtils.safeClose(channel);
            }
        }
    }

    /**
     * Class to store the client response or the exception.
     */
    private class ClientResponseOrException {
        private final ClientResponse response;
        private final IOException exception;

        ClientResponseOrException(ClientResponse response) {
            this.response = response;
            this.exception = null;
        }

        ClientResponseOrException(IOException exception) {
            this.response = null;
            this.exception = exception;
        }

        public ClientResponse getResponse() {
            return response;
        }

        public IOException getException() {
            return exception;
        }

        public boolean isResponse() {
            return response != null;
        }
    }
}
