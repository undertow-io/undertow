/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
import io.undertow.client.PushCallback;
import io.undertow.client.http.HttpClientProvider;
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
import org.jboss.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
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
import java.security.PrivilegedAction;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.undertow.server.protocol.http2.Http2OpenListener.HTTP2;
import static io.undertow.testutils.StopServerWithExternalWorkerUtils.stopWorker;
import static java.security.AccessController.doPrivileged;

/**
 * Test RST frames handling on push. This test mimics rapid refresh on client side, which will result in push requests from
 * client. This paired with client caching policies and possible violation of protocol( not expecting late DATA frames) can lead
 * to RST flame from client. In turn this would cause server to drop connection.
 */
@Category(UnitTest.class)
@RunWith(DefaultServer.class)
@Ignore
public class PushResourceRSTTestCase {
    private static final Logger log = Logger.getLogger(PushResourceRSTTestCase.class);
    private static final String PUSHER = "/pusher";
    private static final String PUSHER_MSG;
    private static final String TRIGGER = "/trigger";
    private static XnioWorker worker;
    private static Undertow defaultConfigServer;
    private static final OptionMap DEFAULT_OPTIONS;
    private static URI defaultConfigServerAddress;
    private static volatile Http2ClientConnection clientConnection;
    private IOException exception;
    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);
    static {
        final OptionMap.Builder builder = OptionMap.builder().set(Options.WORKER_IO_THREADS, 8).set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true).set(Options.WORKER_NAME, "Client");

        DEFAULT_OPTIONS = builder.getMap();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50000; i++) {
            sb.append(i).append('\n');
        }
        PUSHER_MSG = sb.toString();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        int port = DefaultServer.getHostPort("default");

        final PathHandler path = new PathHandler().addExactPath(PUSHER, PushResourceRSTTestCase::sendMessage)
                .addExactPath(TRIGGER, exchange -> {
                    exchange.getRequestReceiver().receiveFullString((exchange1, message) -> {
                        // NOTE: do not send response here, let server handle, so it marks it on client side as non final.
                        exchange1.getConnection().pushResource(PUSHER, Methods.GET, exchange1.getRequestHeaders());
                    });
                });

        defaultConfigServer = Undertow.builder()
                .addHttpsListener(port + 1, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setServerOption(UndertowOptions.RST_FRAMES_TIME_WINDOW, 5000)
                .setServerOption(UndertowOptions.MAX_RST_FRAMES_PER_WINDOW, 10).setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(path::handleRequest).build();
        defaultConfigServer.start();
        defaultConfigServerAddress = new URI("https://" + DefaultServer.getHostAddress() + ":" + (port + 1));
        worker = Xnio.getInstance().createWorker(null, DEFAULT_OPTIONS);
    }

    @AfterClass
    public static void afterClass() {
        if (defaultConfigServer != null)
            defaultConfigServer.stop();

        if (worker != null)
            stopWorker(worker);
    }

    static void sendMessage(final HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.OK);
        final Sender sender = exchange.getResponseSender();
        sender.send(PUSHER_MSG);
    }

    public static ALPNClientSelector.ALPNProtocol alpnProtocol(final ClientCallback<ClientConnection> listener,
            String defaultHost, ByteBufferPool bufferPool, OptionMap options) {
        return new ALPNClientSelector.ALPNProtocol(
                connection -> listener.completed(createHttp2Channel(connection, bufferPool, options, defaultHost)), HTTP2);
    }

    private static Http2ClientConnection createHttp2Channel(StreamConnection connection, ByteBufferPool bufferPool,
            OptionMap options, String defaultHost) {
        // first we set up statistics, if required
        Http2Channel http2Channel = new Http2Channel(connection, null, bufferPool, null, true, false, options);
        return clientConnection = new Http2ClientConnection(http2Channel, false, defaultHost, null, true);
    }

    @Test
    public void testRstOnPush() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        final int totalNumberOfRequests = 250;
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(totalNumberOfRequests * 2);
        final AtomicInteger pushRstCount = new AtomicInteger(0);

        ServiceLoader<ClientProvider> providers = doPrivileged(
                (PrivilegedAction<ServiceLoader<ClientProvider>>) () -> ServiceLoader.load(ClientProvider.class,
                        this.getClass().getClassLoader()));
        ClientProvider clientProvider = null;
        for (ClientProvider provider : providers) {
            for (String scheme : provider.handlesSchemes()) {
                if (scheme.equals(defaultConfigServerAddress.getScheme())) {
                    clientProvider = provider;
                    break;
                }
            }
        }
        Assert.assertNotNull(clientProvider);
        final FutureResult<ClientConnection> result = new FutureResult<>();
        ClientCallback<ClientConnection> listener = new ClientCallback<ClientConnection>() {
            @Override
            public void completed(ClientConnection r) {
                result.setResult(r);
            }

            @Override
            public void failed(IOException e) {
                result.setException(e);
            }
        };
        UndertowXnioSsl ssl = new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.getClientSSLContext());
        OptionMap tlsOptions = OptionMap.builder()
                .set(UndertowOptions.ENDPOINT_IDENTIFICATION_ALGORITHM,
                        HttpClientProvider.DISABLE_HTTPS_ENDPOINT_IDENTIFICATION ? "" : "HTTPS")
                .set(Options.SSL_STARTTLS, true).getMap();
        ChannelListener<StreamConnection> openListener = connection -> ALPNClientSelector.runAlpn((SslConnection) connection,
                connection1 -> {
                    UndertowLogger.ROOT_LOGGER.alpnConnectionFailed(connection1);
                    IoUtils.safeClose(connection1);
                }, listener,
                alpnProtocol(listener, defaultConfigServerAddress.getHost(), DefaultServer.getBufferPool(), tlsOptions));

        ssl.openSslConnection(worker,
                new InetSocketAddress(defaultConfigServerAddress.getHost(), defaultConfigServerAddress.getPort()), openListener,
                tlsOptions).addNotifier((IoFuture.Notifier<StreamConnection, Object>) (ioFuture, o) -> {
                    if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                        listener.failed(ioFuture.getException());
                    }
                }, null);

        final ClientConnection connection = result.getIoFuture().get();
        try {
            connection.getIoThread().execute(() -> {
                for (int i = 0; i < totalNumberOfRequests; i++) {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(TRIGGER);
                    request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                    connection.sendRequest(request, createClientCallback(responses, latch, pushRstCount));
                }
            });

            latch.await(25, TimeUnit.SECONDS);
            Assert.assertEquals(totalNumberOfRequests, pushRstCount.get());
            Assert.assertNull(exception);
            // could use local 'connection'
            Assert.assertTrue(clientConnection.isOpen());
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    private ClientCallback<ClientExchange> createClientCallback(final List<ClientResponse> responses,
            final CountDownLatch latch, final AtomicInteger pushRstCount) {
        return new ClientCallback<ClientExchange>() {
            @Override
            public void completed(final ClientExchange result) {
                result.setPushHandler(new PushCallback() {
                    @Override
                    public boolean handlePush(ClientExchange originalRequest, ClientExchange pushedRequest) {
                        pushRstCount.incrementAndGet();
                        log.debugf("Handling push %d", pushRstCount.get());
                        latch.countDown();
                        setUpResponseListenerAndShutdownWrites(result);
                        return false;
                    }
                });
            }
            @Override
            public void failed(IOException e) {
                e.printStackTrace();
                exception = e;
                latch.countDown();
            }

            private void setUpResponseListenerAndShutdownWrites(ClientExchange result) {
                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(final ClientExchange result) {
                        log.debugf("Got result %s", result);
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
                                exception = e;
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
                    if (!result.getRequestChannel().flush()) {
                        result.getRequestChannel().getWriteSetter()
                                .set(ChannelListeners.<StreamSinkChannel> flushingChannelListener(null, null));
                        result.getRequestChannel().resumeWrites();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    exception = e;
                    latch.countDown();
                }
            }
        };
    }
}
