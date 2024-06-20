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

package io.undertow.client.http;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClient;
import io.undertow.io.Receiver;
import io.undertow.io.Sender;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.StringWriteChannelListener;
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
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.undertow.testutils.StopServerWithExternalWorkerUtils.stopWorker;

/**
 * @author Emanuel Muckenhuber
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class HttpClientTestCase {

    private static final String message = "Hello World!";
    public static final String MESSAGE = "/message";
    public static final String READTIMEOUT = "/readtimeout";
    public static final String READTIMEOUT_AT_INIT = "/readtimeout-init";
    public static final String POST = "/post";
    private static XnioWorker worker;

    private static final OptionMap DEFAULT_OPTIONS;
    private static final URI ADDRESS;

    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

    private IOException exception;

    static {
        final OptionMap.Builder builder = OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .set(Options.WORKER_NAME, "Client");

        DEFAULT_OPTIONS = builder.getMap();
        try {
            ADDRESS = new URI(DefaultServer.getDefaultServerURL());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    static void sendMessage(final HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.OK);
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
        final Sender sender = exchange.getResponseSender();
        sender.send(message);
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        // Create xnio worker
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null, DEFAULT_OPTIONS);
        worker = xnioWorker;
        DefaultServer.setRootHandler(new PathHandler()
        .addExactPath(MESSAGE, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                sendMessage(exchange);
            }
        })
        .addExactPath(READTIMEOUT, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.setStatusCode(StatusCodes.OK);
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 5 + "");
                try (StreamSinkChannel responseChannel = exchange.getResponseChannel()) {
                    responseChannel.write(ByteBuffer.wrap(new byte[]{'a', 'b', 'c'}));
                    responseChannel.flush();
                    try {
                        //READ_TIMEOUT set as 600ms on the client side
                        //On the server side intentionally sleep 2000ms
                        //to make READ_TIMEOUT happening at client side
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    responseChannel.write(ByteBuffer.wrap(new byte[]{'d', 'e'}));
                }
            }
        })
        .addExactPath(READTIMEOUT_AT_INIT, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                try {
                    // Do the sleep before sending any data to the client
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                exchange.setStatusCode(StatusCodes.OK);
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "5");
                try (StreamSinkChannel responseChannel = exchange.getResponseChannel()) {
                    responseChannel.write(ByteBuffer.wrap(new byte[]{'a', 'b', 'c', 'd', 'e'}));
                }
            }
        })
        .addExactPath(POST, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getRequestReceiver().receiveFullString(new Receiver.FullStringCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, String message) {
                        exchange.getResponseSender().send(message);
                    }
                });
            }
        }));
    }

    @AfterClass
    public static void afterClass() {
        stopWorker(worker);
    }

    static UndertowClient createClient() {
        return createClient(OptionMap.EMPTY);
    }

    static UndertowClient createClient(final OptionMap options) {
        return UndertowClient.getInstance();
    }

    @Test
    public void testSimpleBasic() throws Exception {
        //
        final UndertowClient client = createClient();

        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(10);
        final ClientConnection connection = client.connect(ADDRESS, worker, DefaultServer.getBufferPool(), OptionMap.EMPTY).get();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(MESSAGE);
                        request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                        connection.sendRequest(request, createClientCallback(responses, latch));
                    }
                }

            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(10, responses.size());
            for (final ClientResponse response : responses) {
                Assert.assertEquals(message, response.getAttachment(RESPONSE_BODY));
            }
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    @Test
    public void testPostRequest() throws Exception {
        //
        final UndertowClient client = createClient();
        final String postMessage = "This is a post request";

        final List<String> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(10);
        final ClientConnection connection = client.connect(ADDRESS, worker, DefaultServer.getBufferPool(), OptionMap.EMPTY).get();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        final ClientRequest request = new ClientRequest().setMethod(Methods.POST).setPath(POST);
                        request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                        request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
                        connection.sendRequest(request, new ClientCallback<ClientExchange>() {
                            @Override
                            public void completed(ClientExchange result) {
                                new StringWriteChannelListener(postMessage).setup(result.getRequestChannel());
                                result.setResponseListener(new ClientCallback<ClientExchange>() {
                                    @Override
                                    public void completed(ClientExchange result) {
                                        new StringReadChannelListener(DefaultServer.getBufferPool()) {

                                            @Override
                                            protected void stringDone(String string) {
                                                responses.add(string);
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
                        });
                    }
                }

            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(10, responses.size());
            for (final String response : responses) {
                Assert.assertEquals(postMessage, response);
            }
        } finally {
            IoUtils.safeClose(connection);
        }
    }


    @Test
    public void testSsl() throws Exception {
        //
        final UndertowClient client = createClient();

        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(10);
        DefaultServer.startSSLServer();
        SSLContext context = DefaultServer.getClientSSLContext();
        XnioSsl ssl = new UndertowXnioSsl(DefaultServer.getWorker().getXnio(), OptionMap.EMPTY, DefaultServer.SSL_BUFFER_POOL, context);

        final ClientConnection connection = client.connect(new URI(DefaultServer.getDefaultServerSSLAddress()), worker, ssl, DefaultServer.getBufferPool(), OptionMap.EMPTY).get();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(MESSAGE);
                        request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                        connection.sendRequest(request, createClientCallback(responses, latch));
                    }
                }

            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(10, responses.size());
            for (final ClientResponse response : responses) {
                Assert.assertEquals(message, response.getAttachment(RESPONSE_BODY));
            }
        } finally {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    IoUtils.safeClose(connection);
                }
            });
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testSslServerIdentity() throws Exception {
        final UndertowClient client = createClient();
        exception = null;

        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        DefaultServer.startSSLServer();
        SSLContext context = DefaultServer.getClientSSLContext();
        XnioSsl ssl = new UndertowXnioSsl(DefaultServer.getWorker().getXnio(), OptionMap.EMPTY, DefaultServer.SSL_BUFFER_POOL, context);

        // change the URI to use the IP instead the "localhost" name set in the certificate
        URI uri = new URI(DefaultServer.getDefaultServerSSLAddress());
        InetAddress address = InetAddress.getByName(uri.getHost());
        String hostname = address instanceof Inet6Address? "[" + address.getHostAddress() + "]" : address.getHostAddress();
        uri = new URI(uri.getScheme(), uri.getUserInfo(), hostname, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());

        // this should fail as IP alternative name is not set in the certificate
        final ClientConnection connection = client.connect(uri, worker, ssl, DefaultServer.getBufferPool(), OptionMap.EMPTY).get();
        try {
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(MESSAGE);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                connection.sendRequest(request, createClientCallback(responses, latch));
            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(0, responses.size());
            Assert.assertTrue(exception instanceof ClosedChannelException);
        } finally {
            connection.getIoThread().execute(() -> IoUtils.safeClose(connection));
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testConnectionClose() throws Exception {
        //
        final UndertowClient client = createClient();

        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection = client.connect(ADDRESS, worker, DefaultServer.getBufferPool(), OptionMap.EMPTY).get();
        try {
            ClientRequest request = new ClientRequest().setPath(MESSAGE).setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
            final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
            request.getRequestHeaders().add(Headers.CONNECTION, Headers.CLOSE.toString());
            connection.sendRequest(request, createClientCallback(responses, latch));
            latch.await();
            final ClientResponse response = responses.iterator().next();
            Assert.assertEquals(message, response.getAttachment(RESPONSE_BODY));
            Assert.assertEquals(false, connection.isOpen());
        } finally {
            IoUtils.safeClose(connection);
        }

    }

    @Test
    public void testReadTimeout() throws Exception {
        //
        final UndertowClient client = createClient();
        exception = null;

        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        OptionMap.Builder builder = OptionMap.builder();
        builder.set(Options.READ_TIMEOUT, 600);
        final ClientConnection connection = client.connect(ADDRESS, worker, DefaultServer.getBufferPool(), builder.getMap()).get();
        try {
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(READTIMEOUT);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                connection.sendRequest(request, createClientCallback(responses, latch));
            });
            Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
            //exception expected because of read timeout
            Assert.assertTrue(exception instanceof ReadTimeoutException);
        } finally {
            connection.getIoThread().execute(() -> IoUtils.safeClose(connection));
        }
    }

    @Test
    public void testReadTimeoutAtInit() throws Exception {
        final UndertowClient client = createClient();
        exception = null;

        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        OptionMap.Builder builder = OptionMap.builder().set(Options.READ_TIMEOUT, 600);
        final ClientConnection connection = client.connect(ADDRESS, worker, DefaultServer.getBufferPool(), builder.getMap()).get();
        try {
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(READTIMEOUT_AT_INIT);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                connection.sendRequest(request, createClientCallback(responses, latch));
            });
            Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
            //exception expected because of read timeout
            Assert.assertTrue(exception instanceof ReadTimeoutException);
        } finally {
            connection.getIoThread().execute(() -> IoUtils.safeClose(connection));
        }
    }

    private ClientCallback<ClientExchange> createClientCallback(final List<ClientResponse> responses, final CountDownLatch latch) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(final ClientExchange result) {
                        new StringReadChannelListener(result.getConnection().getBufferPool()) {

                            @Override
                            protected void stringDone(String string) {
                                result.getResponse().putAttachment(RESPONSE_BODY, string);
                                // add response only if there is a string or error, or else
                                // we risk adding keep alive messages in timeout tests
                                responses.add(result.getResponse());
                                latch.countDown();
                            }

                            @Override
                            protected void error(IOException e) {
                                e.printStackTrace();
                                exception = e;
                                // add response only if there is a string or error, or else
                                // we risk adding keep alive messages in timeout tests
                                responses.add(result.getResponse());
                                latch.countDown();
                            }
                        }.setup(result.getResponseChannel());
                    }

                    @Override
                    public void failed(IOException e) {
                        e.printStackTrace();
                        exception = e;

                        latch.countDown();
                    }
                });
                try {
                    result.getRequestChannel().shutdownWrites();
                    if(!result.getRequestChannel().flush()) {
                        result.getRequestChannel().getWriteSetter().set(ChannelListeners.<StreamSinkChannel>flushingChannelListener(null, null));
                        result.getRequestChannel().resumeWrites();
                    }
                } catch (IOException e) {
                    exception = e;
                    e.printStackTrace();
                    latch.countDown();
                }
            }

            @Override
            public void failed(IOException e) {
                exception = e;
                e.printStackTrace();
                latch.countDown();
            }
        };
    }

}
