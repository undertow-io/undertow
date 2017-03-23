package io.undertow.client.http;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClient;
import io.undertow.client.UndertowHttp2Client;
import io.undertow.io.Receiver;
import io.undertow.io.Sender;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DebuggingSlicePool;
import io.undertow.testutils.DefaultServer;
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
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Steve Hu
 */
public class Http2ClientTestCase {
    static Undertow server;

    private static final String responseMessage = "Hello World!";
    private static final String requestMessage = "Hi";

    private static final String http2 = "HTTP/2.0";
    private static XnioWorker worker;

    private static final OptionMap DEFAULT_OPTIONS;
    private static final HttpHandler HTTP2_HANDLER;
    private static final char[] STORE_PASSWORD = "password".toCharArray();
    private static final DebuggingSlicePool pool = new DebuggingSlicePool(new DefaultByteBufferPool(true, 8192 * 3, 1000, 10, 100));


    private static final AttachmentKey<String> RESPONSE_BODY = AttachmentKey.create(String.class);

    static {

        final OptionMap.Builder builder = OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .set(Options.WORKER_NAME, "Client");

        DEFAULT_OPTIONS = builder.getMap();

        HTTP2_HANDLER = Handlers.routing()
                .add(Methods.GET, "/", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        sendMessage(exchange);
                    }
                })
                .add(Methods.POST, "/", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getRequestReceiver().receiveFullString(new Receiver.FullStringCallback() {
                            @Override
                            public void handle(HttpServerExchange exchange, String requestMessage) {
                                System.out.println("requestMessage = " + requestMessage);
                                exchange.getResponseSender().send(responseMessage);
                            }
                        });
                    }
                });
    }

    static void sendMessage(final HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.OK);
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, responseMessage.length() + "");
        final Sender sender = exchange.getResponseSender();
        sender.send(responseMessage);
    }

    @BeforeClass
    public static void setup() throws Exception {
        SSLContext sslContext = createSSLContext(loadKeyStore("server.keystore"), loadKeyStore("server.truststore"));
        server = Undertow.builder()
                .addHttpListener(7777, "localhost")
                .addHttpsListener(7778, "localhost", sslContext)
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setHandler(HTTP2_HANDLER)
                .build();
        server.start();

        // Create xnio worker
        final Xnio xnio = Xnio.getInstance();
        final XnioWorker xnioWorker = xnio.createWorker(null, DEFAULT_OPTIONS);
        worker = xnioWorker;

    }

    @AfterClass
    public static void stop() {
        server.stop();
        worker.shutdown();
    }

    static UndertowHttp2Client createClient() {
        return createClient(OptionMap.EMPTY);
    }

    static UndertowHttp2Client createClient(final OptionMap options) {
        return UndertowHttp2Client.getInstance();
    }

    @Test
    public void testSimpleHttp() throws Exception {
        final UndertowHttp2Client client = createClient();

        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(10);
        final ClientConnection connection = client.connect(new URI("http://localhost:7777"), worker, DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/");
                        request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                        connection.sendRequest(request, createClientCallback(responses, latch));
                    }
                }

            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(10, responses.size());
            for (final ClientResponse response : responses) {
                Assert.assertEquals(http2, response.getProtocol().toString());
                Assert.assertEquals(responseMessage, response.getAttachment(RESPONSE_BODY));
            }
            Assert.assertEquals(true, connection.isOpen());
        } finally {
            IoUtils.safeClose(connection);
        }
    }


    @Test
    public void testSimpleHttps() throws Exception {
        final UndertowHttp2Client client = createClient();
        final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(10);
        SSLContext clientSslContext = createSSLContext(loadKeyStore("client.keystore"), loadKeyStore("client.truststore"));
        XnioSsl ssl = new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, clientSslContext);
        final ClientConnection connection = client.connect(new URI("https://localhost:7778"), worker, ssl, pool, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/");
                        request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                        connection.sendRequest(request, createClientCallback(responses, latch));
                    }
                }
            });

            latch.await(10, TimeUnit.SECONDS);

            Assert.assertEquals(10, responses.size());
            for (final ClientResponse response : responses) {
                Assert.assertEquals(http2, response.getProtocol().toString());
                Assert.assertEquals(responseMessage, response.getAttachment(RESPONSE_BODY));
            }
            Assert.assertEquals(true, connection.isOpen());
        } finally {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    IoUtils.safeClose(connection);
                }
            });
        }
    }

    @Test
    public void testHttpGet() throws Exception {
        final UndertowHttp2Client client = createClient();

        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection = client.connect(new URI("http://localhost:7777"), worker, DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            ClientRequest request = new ClientRequest().setPath("/").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
            final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
            request.getRequestHeaders().add(Headers.CONNECTION, Headers.CLOSE.toString());
            connection.sendRequest(request, createClientCallback(responses, latch));
            latch.await();
            final ClientResponse response = responses.iterator().next();
            Assert.assertEquals(http2, response.getProtocol().toString());
            Assert.assertEquals(responseMessage, response.getAttachment(RESPONSE_BODY));
            Assert.assertEquals(true, connection.isOpen());
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    @Test
    public void testPostRequest() throws Exception {
        //
        final UndertowHttp2Client client = createClient();

        final List<String> responses = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(10);
        final ClientConnection connection = client.connect(new URI("http://localhost:7777"), worker, DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        final ClientRequest request = new ClientRequest().setMethod(Methods.POST).setPath("/");
                        request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
                        request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
                        connection.sendRequest(request, new ClientCallback<ClientExchange>() {
                            @Override
                            public void completed(ClientExchange result) {
                                new StringWriteChannelListener(requestMessage).setup(result.getRequestChannel());
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
                System.out.println("response = " + response);
                Assert.assertEquals(responseMessage, response);
            }
        } finally {
            IoUtils.safeClose(connection);
        }
    }

    /*

    @Test
    public void testHttpPost() throws Exception {
        final UndertowHttp2Client client = createClient();

        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection = client.connect(new URI("http://localhost:7777"), worker, DefaultServer.getBufferPool(), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            ClientRequest request = new ClientRequest().setPath("/").setMethod(Methods.POST);
            request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
            request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
            connection.sendRequest(request, new ClientCallback<ClientExchange>() {
                @Override
                public void completed(ClientExchange result) {
                    new StringWriteChannelListener(requestMessage).setup(result.getRequestChannel());
                    result.setResponseListener(new ClientCallback<ClientExchange>() {
                        @Override
                        public void completed(ClientExchange result) {
                            new StringReadChannelListener(DefaultServer.getBufferPool()) {

                                @Override
                                protected void stringDone(String string) {
                                    Assert.assertEquals(responseMessage, string);
                                    System.out.println("string = " + string);
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
            latch.await(2, TimeUnit.SECONDS);

            Assert.assertEquals(true, connection.isOpen());
        } finally {
            IoUtils.safeClose(connection);
        }
    }
    */

    @Test
    public void testHttpsGet() throws Exception {
        final UndertowHttp2Client client = createClient();

        final CountDownLatch latch = new CountDownLatch(1);
        SSLContext clientSslContext = createSSLContext(loadKeyStore("client.keystore"), loadKeyStore("client.truststore"));
        XnioSsl ssl = new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, clientSslContext);
        final ClientConnection connection = client.connect(new URI("https://localhost:7778"), worker, ssl, pool, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)).get();
        try {
            ClientRequest request = new ClientRequest().setPath("/").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, DefaultServer.getHostAddress());
            final List<ClientResponse> responses = new CopyOnWriteArrayList<>();
            request.getRequestHeaders().add(Headers.CONNECTION, Headers.CLOSE.toString());
            connection.sendRequest(request, createClientCallback(responses, latch));
            latch.await();
            final ClientResponse response = responses.iterator().next();
            Assert.assertEquals(http2, response.getProtocol().toString());
            Assert.assertEquals(responseMessage, response.getAttachment(RESPONSE_BODY));
            Assert.assertEquals(true, connection.isOpen());
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
                    if(!result.getRequestChannel().flush()) {
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

    private static KeyStore loadKeyStore(String name) throws Exception {
        String storeLoc = System.getProperty(name);
        final InputStream stream;
        if(storeLoc == null) {
            stream = Http2ClientTestCase.class.getClassLoader().getResourceAsStream(name);
        } else {
            stream = Files.newInputStream(Paths.get(storeLoc));
        }

        if(stream == null) {
            throw new RuntimeException("Could not load keystore");
        }
        try(InputStream is = stream) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(is, password(name));
            return loadedKeystore;
        }
    }

    static char[] password(String name) {
        String pw = System.getProperty(name + ".password");
        return pw != null ? pw.toCharArray() : STORE_PASSWORD;
    }


    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws Exception {
        KeyManager[] keyManagers;
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password("key"));
        keyManagers = keyManagerFactory.getKeyManagers();

        TrustManager[] trustManagers;
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        trustManagers = trustManagerFactory.getTrustManagers();

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

}
