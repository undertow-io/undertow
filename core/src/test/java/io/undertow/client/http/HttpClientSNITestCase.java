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
import io.undertow.io.Sender;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.StopServerWithExternalWorkerUtils;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringReadChannelListener;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
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
 * <p>Test class for the option SSL_SNI_HOSTNAME. The tests are assumed
 * if the server address cannot be converted into a hostname value.</p>
 *
 * @author rmartinc
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
public class HttpClientSNITestCase {

    public static final String SNI = "/sni";
    private static final OptionMap DEFAULT_OPTIONS;
    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

    private static URL ADDRESS;
    private static XnioWorker worker;
    private static Undertow server;

    static {
        DEFAULT_OPTIONS = OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .set(Options.WORKER_NAME, "Client").getMap();
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        // Create xnio worker
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null, DEFAULT_OPTIONS);
        worker = xnioWorker;
        // start server in https and add a handler that returns the SNI names separated by colon
        int port = DefaultServer.getHostPort("default") + 1;
        server = Undertow.builder()
                .setByteBufferPool(DefaultServer.getBufferPool())
                .addHttpsListener(port, DefaultServer.getHostAddress("default"), DefaultServer.getServerSslContext())
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(new PathHandler()
                        .addExactPath(SNI, exchange -> {
                            StringBuilder sb = new StringBuilder();
                            SSLSessionInfo ssl = exchange.getConnection().getSslSessionInfo();
                            if (ssl != null && ssl.getSSLSession() instanceof ExtendedSSLSession) {
                                List<SNIServerName> names = ((ExtendedSSLSession) ssl.getSSLSession()).getRequestedServerNames();
                                if (names != null) {
                                    for (SNIServerName name : names) {
                                        if (name instanceof SNIHostName) {
                                            if (sb.length() > 0) {
                                                sb.append(":");
                                            }
                                            sb.append(((SNIHostName) name).getAsciiName());
                                        }
                                    }
                                }
                            }
                            exchange.setStatusCode(StatusCodes.OK);
                            final Sender sender = exchange.getResponseSender();
                            sender.send(sb.toString(), StandardCharsets.UTF_8);
                        }))
                .build();

        server.start();
        ADDRESS = new URL("https://" + DefaultServer.getHostAddress() + ":" + port);
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
    public void testSNIWhenHostname() throws Exception {
        InetAddress address = InetAddress.getByName(ADDRESS.getHost());
        Assume.assumeTrue("Assuming the test if no resolution for the address", !address.getHostName().equals(address.getHostAddress()));

        final UndertowClient client = UndertowClient.getInstance();
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        // connect using the hostname, SNI expected
        final ClientConnection connection = client.connect(new URI("https://" + address.getHostName() + ":" + ADDRESS.getPort()), worker,
                new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.createClientSslContext()),
                DefaultServer.getBufferPool(), OptionMap.EMPTY).get();
        try {
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(SNI);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                connection.sendRequest(request, createClientCallback(responses, latch));
            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(1, responses.size());
            Assert.assertEquals(StatusCodes.OK, responses.get(0).getResponseCode());
            Assert.assertEquals(address.getHostName(), responses.get(0).getAttachment(RESPONSE_BODY));
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    @Test
    public void testNoSNIWhenIP() throws Exception {
        InetAddress address = InetAddress.getByName(ADDRESS.getHost());
        Assume.assumeTrue("Assuming the test if no resolution for the address", !address.getHostName().equals(address.getHostAddress()));
        String hostname = address instanceof Inet6Address? "[" + address.getHostAddress() + "]" : address.getHostAddress();

        final UndertowClient client = UndertowClient.getInstance();
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        // connect using the IP, no SNI expected
        final ClientConnection connection = client.connect(new URI("https://" + hostname + ":" + ADDRESS.getPort()), worker,
                new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.createClientSslContext()),
                DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENDPOINT_IDENTIFICATION_ALGORITHM, "")).get();
        try {
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(SNI);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                connection.sendRequest(request, createClientCallback(responses, latch));
            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(1, responses.size());
            Assert.assertEquals(StatusCodes.OK, responses.get(0).getResponseCode());
            Assert.assertEquals("", responses.get(0).getAttachment(RESPONSE_BODY));
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    @Test
    public void testForcingSNIForIP() throws Exception {
        InetAddress address = InetAddress.getByName(ADDRESS.getHost());
        Assume.assumeTrue("Assuming the test if no resolution for the address", !address.getHostName().equals(address.getHostAddress()));
        String hostname = address instanceof Inet6Address? "[" + address.getHostAddress() + "]" : address.getHostAddress();

        final UndertowClient client = UndertowClient.getInstance();
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        // connect using IP but adding hostname via option, SNI expected to the forced one
        final ClientConnection connection = client.connect(new URI("https://" + hostname + ":" + ADDRESS.getPort()), worker,
                new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.createClientSslContext()),
                DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.SSL_SNI_HOSTNAME, address.getHostName())).get();
        try {
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(SNI);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                connection.sendRequest(request, createClientCallback(responses, latch));
            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(1, responses.size());
            Assert.assertEquals(StatusCodes.OK, responses.get(0).getResponseCode());
            Assert.assertEquals(address.getHostName(), responses.get(0).getAttachment(RESPONSE_BODY));
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    @Test
    public void testForcingSNIForHostname() throws Exception {
        InetAddress address = InetAddress.getByName(ADDRESS.getHost());
        Assume.assumeTrue("Assuming the test if no resolution for the address", !address.getHostName().equals(address.getHostAddress()));

        final UndertowClient client = UndertowClient.getInstance();
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        // connect using hostname but add option to another hostname, SNI expected to the forced one
        final ClientConnection connection = client.connect(new URI("https://" + address.getHostName() + ":" + ADDRESS.getPort()), worker,
                new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, DefaultServer.createClientSslContext()),
                DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.SSL_SNI_HOSTNAME, "server", UndertowOptions.ENDPOINT_IDENTIFICATION_ALGORITHM, "")).get();
        try {
            connection.getIoThread().execute(() -> {
                final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(SNI);
                request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                connection.sendRequest(request, createClientCallback(responses, latch));
            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(1, responses.size());
            Assert.assertEquals(StatusCodes.OK, responses.get(0).getResponseCode());
            Assert.assertEquals("server", responses.get(0).getAttachment(RESPONSE_BODY));
        } finally {
            IoUtils.safeClose(connection);
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
