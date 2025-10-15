/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package io.undertow.protocols.http2;

import io.undertow.Undertow;
import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.client.ALPNClientSelector;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientProvider;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.http.HttpClientProvider;
import io.undertow.client.http2.DoSHttp2ClientConnection;
import io.undertow.client.http2.Http2ClientConnection;
import io.undertow.connector.ByteBufferPool;
import io.undertow.io.Sender;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.category.UnitTest;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.ssl.SslConnection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ClosedChannelException;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.undertow.server.protocol.http2.Http2OpenListener.HTTP2;
import static io.undertow.testutils.StopServerWithExternalWorkerUtils.stopWorker;
import static java.security.AccessController.doPrivileged;

/**
 * Test that mimics the rapid reset DDoS attack. See UNDERTOW-2323.
 *
 * @author Flavia Rainone
 */
@Category(UnitTest.class)
public class RapidResetDDoSUnitTestCase {

    private static final String message = "Hello World!";
    public static final String MESSAGE = "/message";
    public static final String POST = "/post";
    private static XnioWorker worker;
    private static Undertow defaultConfigServer;
    private static Undertow overwrittenConfigServer;
    private static final OptionMap DEFAULT_OPTIONS;
    private static URI defaultConfigServerAddress;
    private static URI overwrittenConfigServerAddress;

    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

    private static volatile DoSHttp2ClientConnection clientConnection;
    private IOException exception;

    static {
        final OptionMap.Builder builder = OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .set(Options.WORKER_NAME, "Client");

        DEFAULT_OPTIONS = builder.getMap();
    }

    static void sendMessage(final HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.OK);
        final Sender sender = exchange.getResponseSender();
        sender.send(message);
    }

    @BeforeClass
    public static void beforeClass() throws Exception {

        int port = DefaultServer.getHostPort("default");

        final PathHandler path = new PathHandler()
                .addExactPath(MESSAGE, RapidResetDDoSUnitTestCase::sendMessage)
                .addExactPath(POST, exchange -> exchange.getRequestReceiver().receiveFullString(
                        (exchange1, message) -> exchange1.getResponseSender().send(message)));

        defaultConfigServer = Undertow.builder()
                .addHttpsListener(port + 1, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(path::handleRequest)
                .build();
        defaultConfigServer.start();

        overwrittenConfigServer = Undertow.builder()
                .addHttpsListener(port + 2, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setServerOption(UndertowOptions.RST_FRAMES_TIME_WINDOW, 5000)
                .setServerOption(UndertowOptions.MAX_RST_FRAMES_PER_WINDOW, 50)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(path::handleRequest)
                .build();
        overwrittenConfigServer.start();

        defaultConfigServerAddress = new URI("https://" + DefaultServer.getHostAddress() + ":" + (port + 1));
        overwrittenConfigServerAddress = new URI("https://" + DefaultServer.getHostAddress() + ":" + (port + 2));

        // Create xnio worker
        worker = Xnio.getInstance().createWorker(null, DEFAULT_OPTIONS);
    }

    @AfterClass
    public static void afterClass() {
        if (defaultConfigServer != null)
            defaultConfigServer.stop();
        if (overwrittenConfigServer != null)
            overwrittenConfigServer.stop();
        if (worker != null)
            stopWorker(worker);
    }

    @Test
    public void testGoAwayWithDefaultConfig() throws Exception {
        System.out.println("go away with default config");
        assertDoSRstFramesHandled(300, 200, true, defaultConfigServerAddress);
    }

    @Test
    public void testNoErrorWithDefaultConfig() throws Exception {
        System.out.println("no error with default config");
        assertDoSRstFramesHandled(150, 200, false, defaultConfigServerAddress);
    }

    @Test
    public void testGoAwayWithOverwrittenConfig() throws Exception {
        System.out.println("go away with overwritten config");
        assertDoSRstFramesHandled(100, 50, true, overwrittenConfigServerAddress);
    }

    @Test
    public void testNoErrorWithOverwrittenConfig() throws Exception {
        System.out.println("no error with overwritten config");
        assertDoSRstFramesHandled(50, 50, false, overwrittenConfigServerAddress);
    }

    public void assertDoSRstFramesHandled(int totalNumberOfRequests, int rstStreamLimit, boolean errorExpected, URI serverAddress) throws Exception {
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(totalNumberOfRequests);

        ServiceLoader<ClientProvider> providers = doPrivileged((PrivilegedAction<ServiceLoader<ClientProvider>>)
                () -> ServiceLoader.load(ClientProvider.class, this.getClass().getClassLoader()));
        ClientProvider clientProvider = null;
        for (ClientProvider provider : providers) {
            for (String scheme : provider.handlesSchemes()) {
                if (scheme.equals(serverAddress.getScheme())) {
                    clientProvider = provider;
                    break;
                }
            }
        }
        Assert.assertNotNull(clientProvider);
        final FutureResult<ClientConnection> result = new FutureResult<>();
        ClientCallback<ClientConnection> listener = new ClientCallback<>() {
            @Override public void completed(ClientConnection r) {
                result.setResult(r);
            }

            @Override public void failed(IOException e) {
                result.setException(e);
            }
        };
        UndertowXnioSsl ssl = new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext());
        OptionMap tlsOptions = OptionMap.builder()
                .set(UndertowOptions.ENDPOINT_IDENTIFICATION_ALGORITHM, HttpClientProvider.DISABLE_HTTPS_ENDPOINT_IDENTIFICATION? "" : "HTTPS")
                .set(Options.SSL_STARTTLS, true)
                .getMap();
        ChannelListener<StreamConnection> openListener = connection -> ALPNClientSelector.runAlpn((SslConnection) connection,
                connection1 -> {
                    UndertowLogger.ROOT_LOGGER.alpnConnectionFailed(connection1);
                    IoUtils.safeClose(connection1);
                }, listener, alpnProtocol(listener, serverAddress.getHost(), DefaultServer.getBufferPool(), tlsOptions));

        ssl.openSslConnection(worker, new InetSocketAddress(serverAddress.getHost(), serverAddress.getPort()), openListener, tlsOptions).addNotifier(
                (IoFuture.Notifier<StreamConnection, Object>) (ioFuture, o) -> {
                    if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                        listener.failed(ioFuture.getException());
                    }
                }, null);


        final ClientConnection connection = result.getIoFuture().get();
        try {
            connection.getIoThread().execute(() -> {
                for (int i = 0; i < totalNumberOfRequests; i++) {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(MESSAGE);
                    request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                    connection.sendRequest(request, createClientCallback(responses, latch));
                }
            });

            latch.await(200, TimeUnit.SECONDS);

            // server sent go away before processing and responding client frames, sometimes this happens, depends on the order of threads
            // being executed
            if (responses.size() < totalNumberOfRequests) {
                Assert.assertTrue(errorExpected);
                Assert.assertNotNull(exception);
                Assert.assertTrue(exception instanceof ClosedChannelException);
                return;
            }
            Assert.assertEquals(errorExpected ? rstStreamLimit + 1 : totalNumberOfRequests, responses.size());
            for (final ClientResponse response : responses) {
                final String responseBody = response.getAttachment(RESPONSE_BODY);
                Assert.assertTrue("Unexpected response body: " + responseBody, responseBody.isEmpty() || responseBody.equals(message));
            }
            if (errorExpected) {
                Assert.assertNotNull(exception);
                Assert.assertTrue(exception instanceof ClosedChannelException);
                Http2GoAwayStreamSourceChannel http2GoAwayStreamSourceChannel = clientConnection.getGoAwayStreamSourceChannel();
                Assert.assertNotNull(http2GoAwayStreamSourceChannel);
                Assert.assertEquals(11, http2GoAwayStreamSourceChannel.getStatus());
            } else {
                Assert.assertNull(exception);
                Assert.assertNull(clientConnection.getGoAwayStreamSourceChannel());
            }
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    public static ALPNClientSelector.ALPNProtocol alpnProtocol(final ClientCallback<ClientConnection> listener, String defaultHost, ByteBufferPool bufferPool, OptionMap options) {
        return new ALPNClientSelector.ALPNProtocol(
                connection -> listener.completed(createHttp2Channel(connection, bufferPool, options, defaultHost)), HTTP2);
    }

    private static Http2ClientConnection createHttp2Channel(StreamConnection connection, ByteBufferPool bufferPool, OptionMap options, String defaultHost) {
        //first we set up statistics, if required
        Http2Channel http2Channel = new Http2Channel(connection, null, bufferPool, null, true, false, options);
        return clientConnection =  new DoSHttp2ClientConnection(http2Channel, false, defaultHost, null, true);
    }

    private ClientCallback<ClientExchange> createClientCallback(final List<ClientResponse> responses, final CountDownLatch latch) {
        return new ClientCallback<>() {
            @Override public void completed(ClientExchange result) {
                result.setResponseListener(new ClientCallback<>() {
                    @Override public void completed(final ClientExchange result) {
                        responses.add(result.getResponse());
                        new StringReadChannelListener(result.getConnection().getBufferPool()) {

                            @Override protected void stringDone(String string) {
                                result.getResponse().putAttachment(RESPONSE_BODY, string);
                                latch.countDown();
                            }

                            @Override protected void error(IOException e) {
                                e.printStackTrace();
                                exception = e;
                                latch.countDown();
                            }
                        }.setup(result.getResponseChannel());
                    }

                    @Override public void failed(IOException e) {
                        e.printStackTrace();
                        exception = e;
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
                    exception = e;
                    latch.countDown();
                }
            }

            @Override public void failed(IOException e) {
                e.printStackTrace();
                exception = e;
                latch.countDown();
            }
        };
    }
}