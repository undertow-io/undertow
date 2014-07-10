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

package io.undertow.testutils;

import io.undertow.UndertowOptions;
import io.undertow.security.impl.GSSAPIAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.OpenListener;
import io.undertow.server.handlers.ProxyPeerAddressHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.SSLHeaderHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.protocol.ajp.AjpOpenListener;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.server.protocol.http2.Http2OpenListener;
import io.undertow.server.protocol.spdy.SpdyOpenListener;
import io.undertow.server.protocol.spdy.SpdyPlainOpenListener;
import io.undertow.util.Headers;
import io.undertow.util.NetworkUtils;
import io.undertow.util.SingleByteStreamSinkConduit;
import io.undertow.util.SingleByteStreamSourceConduit;

import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import static io.undertow.server.handlers.ResponseCodeHandler.HANDLE_404;
import static org.xnio.Options.SSL_CLIENT_AUTH_MODE;
import static org.xnio.SslClientAuthMode.REQUESTED;

/**
 * A class that starts a server before the test suite. By swapping out the root handler
 * tests can test various server functionality without continually starting and stopping the server.
 *
 * @author Stuart Douglas
 */
public class DefaultServer extends BlockJUnit4ClassRunner {

    static final String DEFAULT = "default";
    private static final int PROXY_OFFSET = 1111;
    public static final int APACHE_PORT = 9080;
    public static final int APACHE_SSL_PORT = 9443;
    public static final int BUFFER_SIZE = Integer.getInteger("test.bufferSize", 8192);

    private static boolean first = true;
    private static OptionMap serverOptions;
    private static OpenListener openListener;
    private static ChannelListener acceptListener;
    private static OpenListener proxyOpenListener;
    private static ChannelListener proxyAcceptListener;
    private static XnioWorker worker;
    private static AcceptingChannel<? extends StreamConnection> server;
    private static AcceptingChannel<? extends StreamConnection> proxyServer;
    private static AcceptingChannel<? extends StreamConnection> sslServer;
    private static SSLContext clientSslContext;
    private static Xnio xnio;

    private static final String SERVER_KEY_STORE = "server.keystore";
    private static final String SERVER_TRUST_STORE = "server.truststore";
    private static final String CLIENT_KEY_STORE = "client.keystore";
    private static final String CLIENT_TRUST_STORE = "client.truststore";
    private static final char[] STORE_PASSWORD = "password".toCharArray();

    private static final boolean ajp = Boolean.getBoolean("test.ajp");
    private static final boolean spdy = Boolean.getBoolean("test.spdy");
    private static final boolean http2 = Boolean.getBoolean("test.http2");
    private static final boolean spdyPlain = Boolean.getBoolean("test.spdy-plain");
    private static final boolean https = Boolean.getBoolean("test.https");
    private static final boolean proxy = Boolean.getBoolean("test.proxy");
    private static final boolean dump = Boolean.getBoolean("test.dump");
    private static final boolean single = Boolean.getBoolean("test.single");
    private static final int runs = Integer.getInteger("test.runs", 1);

    private static final DelegatingHandler rootHandler = new DelegatingHandler();

    private static KeyStore loadKeyStore(final String name) throws IOException {
        final InputStream stream = DefaultServer.class.getClassLoader().getResourceAsStream(name);
        try {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, STORE_PASSWORD);

            return loadedKeystore;
        } catch (KeyStoreException e) {
            throw new IOException(String.format("Unable to load KeyStore %s", name), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(String.format("Unable to load KeyStore %s", name), e);
        } catch (CertificateException e) {
            throw new IOException(String.format("Unable to load KeyStore %s", name), e);
        } finally {
            IoUtils.safeClose(stream);
        }
    }

    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws IOException {
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, STORE_PASSWORD);
            keyManagers = keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        } catch (UnrecoverableKeyException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        } catch (KeyStoreException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        }

        TrustManager[] trustManagers = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unable to initialise TrustManager[]", e);
        } catch (KeyStoreException e) {
            throw new IOException("Unable to initialise TrustManager[]", e);
        }

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unable to create and initialise the SSLContext", e);
        } catch (KeyManagementException e) {
            throw new IOException("Unable to create and initialise the SSLContext", e);
        }

        return sslContext;
    }

    /**
     * @return The base URL that can be used to make connections to this server
     */
    public static String getDefaultServerURL() {
        return "http://" + NetworkUtils.formatPossibleIpv6Address(getHostAddress(DEFAULT)) + ":" + getHostPort(DEFAULT);
    }

    public static InetSocketAddress getDefaultServerAddress() {
        return new InetSocketAddress(DefaultServer.getHostAddress("default"), DefaultServer.getHostPort("default"));
    }

    public static String getDefaultServerSSLAddress() {
        if (sslServer == null && !isApacheTest()) {
            throw new IllegalStateException("SSL Server not started.");
        }
        return "https://" + NetworkUtils.formatPossibleIpv6Address(getHostAddress(DEFAULT)) + ":" + getHostSSLPort(DEFAULT);
    }

    public DefaultServer(Class<?> klass) throws InitializationError {
        super(klass);
    }

    public static void setupProxyHandlerForSSL(ProxyHandler proxyHandler) {
        proxyHandler.addRequestHeader(Headers.SSL_CLIENT_CERT, "%{SSL_CLIENT_CERT}", DefaultServer.class.getClassLoader());
        proxyHandler.addRequestHeader(Headers.SSL_CIPHER, "%{SSL_CIPHER}", DefaultServer.class.getClassLoader());
        proxyHandler.addRequestHeader(Headers.SSL_SESSION_ID, "%{SSL_SESSION_ID}", DefaultServer.class.getClassLoader());
    }

    public static Pool<ByteBuffer> getBufferPool() {
        return openListener.getBufferPool();
    }

    @Override
    public Description getDescription() {
        return super.getDescription();
    }

    @Override
    public void run(final RunNotifier notifier) {
        notifier.addListener(new RunListener() {
            @Override
            public void testStarted(Description description) throws Exception {
                DebuggingSlicePool.currentLabel = description.getClassName() + "." + description.getMethodName();
                super.testStarted(description);
            }

            @Override
            public void testFinished(Description description) throws Exception {

                if(!DebuggingSlicePool.BUFFERS.isEmpty()) {
                    try {
                        Thread.sleep(200);
                        if(!DebuggingSlicePool.BUFFERS.isEmpty()) {
                            Thread.sleep(2000);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    for(DebuggingSlicePool.DebuggingBuffer b : DebuggingSlicePool.BUFFERS) {
                        b.getAllocationPoint().printStackTrace();
                        notifier.fireTestFailure(new Failure(description,  new RuntimeException("Buffer Leak " + b.getLabel(), b.getAllocationPoint())));
                    }
                    DebuggingSlicePool.BUFFERS.clear();
                }
                super.testFinished(description);
            }
        });
        runInternal(notifier);
        super.run(notifier);
    }

    private static void runInternal(final RunNotifier notifier) {
        if (first) {
            first = false;
            xnio = Xnio.getInstance("nio", DefaultServer.class.getClassLoader());
            try {
                worker = xnio.createWorker(OptionMap.builder()
                        .set(Options.WORKER_IO_THREADS, 8)
                        .set(Options.CONNECTION_HIGH_WATER, 1000000)
                        .set(Options.CONNECTION_LOW_WATER, 1000000)
                        .set(Options.WORKER_TASK_CORE_THREADS, 30)
                        .set(Options.WORKER_TASK_MAX_THREADS, 30)
                        .set(Options.TCP_NODELAY, true)
                        .set(Options.CORK, true)
                        .getMap());

                serverOptions = OptionMap.builder()
                        .set(Options.TCP_NODELAY, true)
                        .set(Options.REUSE_ADDRESSES, true)
                        .set(Options.BALANCING_TOKENS, 1)
                        .set(Options.BALANCING_CONNECTIONS, 2)
                        .getMap();
                DebuggingSlicePool pool = new DebuggingSlicePool(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, BUFFER_SIZE, 100 * BUFFER_SIZE));
                if (ajp) {
                    openListener = new AjpOpenListener(pool, BUFFER_SIZE);
                    acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));
                    if (!proxy) {
                        int port = 8888;
                        server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), port), acceptListener, serverOptions);
                    } else {
                        server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), 7777 + PROXY_OFFSET), acceptListener, serverOptions);

                        proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), BUFFER_SIZE);
                        proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                        proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                        proxyOpenListener.setRootHandler(new ProxyHandler(new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER).addHost(new URI("ajp", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null)), 120000, HANDLE_404));
                        proxyServer.resumeAccepts();

                    }
                } else if (spdy) {
                    openListener = new SpdyOpenListener(new DebuggingSlicePool(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 2* BUFFER_SIZE, 100 * BUFFER_SIZE)), new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, BUFFER_SIZE, BUFFER_SIZE), OptionMap.create(UndertowOptions.ENABLE_SPDY, true), BUFFER_SIZE);
                    acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));

                    SSLContext serverContext = createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE));
                    SSLContext clientContext = createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE));
                    XnioSsl xnioSsl = new JsseXnioSsl(xnio, OptionMap.EMPTY, serverContext);
                    server = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(getHostAddress("default"), 7777 + PROXY_OFFSET), acceptListener, OptionMap.EMPTY);
                    server.resumeAccepts();

                    proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), BUFFER_SIZE);
                    proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                    proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                    ProxyHandler proxyHandler = new ProxyHandler(new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER).addHost(new URI("spdy", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null), null, new JsseXnioSsl(xnio, OptionMap.EMPTY, clientContext), OptionMap.create(UndertowOptions.ENABLE_SPDY, true)), 120000, HANDLE_404);
                    setupProxyHandlerForSSL(proxyHandler);
                    proxyOpenListener.setRootHandler(proxyHandler);
                    proxyServer.resumeAccepts();


                } else if (http2) {
                    openListener = new Http2OpenListener(new DebuggingSlicePool(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 2* BUFFER_SIZE, 100 * BUFFER_SIZE)), OptionMap.create(UndertowOptions.ENABLE_SPDY, true), BUFFER_SIZE);
                    acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));

                    SSLContext serverContext = createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE));
                    SSLContext clientContext = createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE));
                    XnioSsl xnioSsl = new JsseXnioSsl(xnio, OptionMap.EMPTY, serverContext);
                    server = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(getHostAddress("default"), 7777 + PROXY_OFFSET), acceptListener, OptionMap.EMPTY);
                    server.resumeAccepts();

                    proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), BUFFER_SIZE);
                    proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                    proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                    ProxyHandler proxyHandler = new ProxyHandler(new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER).addHost(new URI("http2", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null), null, new JsseXnioSsl(xnio, OptionMap.EMPTY, clientContext), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true)), 120000, HANDLE_404);
                    setupProxyHandlerForSSL(proxyHandler);
                    proxyOpenListener.setRootHandler(proxyHandler);
                    proxyServer.resumeAccepts();


                } else if (spdyPlain) {
                    openListener = new SpdyPlainOpenListener(new DebuggingSlicePool(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 2* BUFFER_SIZE, 100 * BUFFER_SIZE)), new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, BUFFER_SIZE, BUFFER_SIZE), OptionMap.create(UndertowOptions.ENABLE_SPDY, true), BUFFER_SIZE);
                    acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));

                    server = worker.createStreamConnectionServer(new InetSocketAddress(getHostAddress("default"), 7777 + PROXY_OFFSET), acceptListener, OptionMap.EMPTY);
                    server.resumeAccepts();

                    proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), BUFFER_SIZE);
                    proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                    proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                    ProxyHandler proxyHandler = new ProxyHandler(new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER).addHost(new URI("spdy-plain", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null), null, null, OptionMap.create(UndertowOptions.ENABLE_SPDY, true)), 120000, HANDLE_404);
                    setupProxyHandlerForSSL(proxyHandler);
                    proxyOpenListener.setRootHandler(proxyHandler);
                    proxyServer.resumeAccepts();


                } else if (https) {

                    XnioSsl xnioSsl = new JsseXnioSsl(xnio, OptionMap.EMPTY, getServerSslContext());
                    XnioSsl clientSsl = new JsseXnioSsl(xnio, OptionMap.EMPTY, createClientSslContext());
                    openListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), BUFFER_SIZE);
                    acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));

                    InetSocketAddress targetAddress = new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT) + PROXY_OFFSET);
                    server = xnioSsl.createSslConnectionServer(worker, targetAddress, acceptListener, serverOptions);

                    proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), BUFFER_SIZE);
                    proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                    proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                    ProxyHandler proxyHandler = new ProxyHandler(new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER).addHost(new URI("https", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null), clientSsl), 30000, HANDLE_404);
                    setupProxyHandlerForSSL(proxyHandler);
                    proxyOpenListener.setRootHandler(proxyHandler);
                    proxyServer.resumeAccepts();


                } else {
                    openListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), BUFFER_SIZE);
                    acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));
                    if (!proxy) {
                        server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), acceptListener, serverOptions);
                    } else {
                        InetSocketAddress targetAddress = new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT) + PROXY_OFFSET);
                        server = worker.createStreamConnectionServer(targetAddress, acceptListener, serverOptions);

                        proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), BUFFER_SIZE);
                        proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                        proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                        ProxyHandler proxyHandler = new ProxyHandler(new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER).addHost(new URI("http", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null)), 30000, HANDLE_404);
                        setupProxyHandlerForSSL(proxyHandler);
                        proxyOpenListener.setRootHandler(proxyHandler);
                        proxyServer.resumeAccepts();
                    }

                }
                openListener.setRootHandler(rootHandler);
                server.resumeAccepts();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            notifier.addListener(new RunListener() {
                @Override
                public void testRunFinished(final Result result) throws Exception {
                    server.close();
                    stopSSLServer();
                    worker.shutdown();
                }
            });
        }
    }


    @Override
    protected Description describeChild(FrameworkMethod method) {
        if (runs > 1 && method.getAnnotation(Ignore.class) == null) {
            return describeRepeatTest(method);
        }
        return super.describeChild(method);
    }

    private Description describeRepeatTest(FrameworkMethod method) {

        Description description = Description.createSuiteDescription(
                testName(method) + " [" + runs + " times]",
                method.getAnnotations());

        for (int i = 1; i <= runs; i++) {
            description.addChild(Description.createTestDescription(
                    getTestClass().getJavaClass(),
                    "[" + i + "] " + testName(method)));
        }
        return description;
    }

    private static ChannelListener<StreamConnection> wrapOpenListener(final ChannelListener<StreamConnection> listener) {
        if (!single) {
            return listener;
        }
        return new ChannelListener<StreamConnection>() {
            @Override
            public void handleEvent(StreamConnection channel) {
                channel.getSinkChannel().setConduit(new SingleByteStreamSinkConduit(channel.getSinkChannel().getConduit(), 10000));
                channel.getSourceChannel().setConduit(new SingleByteStreamSourceConduit(channel.getSourceChannel().getConduit(), 10000));
                listener.handleEvent(channel);
            }
        };
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        AjpIgnore ajpIgnore = method.getAnnotation(AjpIgnore.class);
        if (ajpIgnore == null) {
            ajpIgnore = method.getMethod().getDeclaringClass().getAnnotation(AjpIgnore.class);
        }
        if (ajp && ajpIgnore != null) {
            if (!proxy || !ajpIgnore.apacheOnly()) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
        }
        if(spdy || spdyPlain || http2) {
            SpdyIgnore spdyIgnore = method.getAnnotation(SpdyIgnore.class);
            if(spdyIgnore == null) {
                spdyIgnore = method.getMethod().getDeclaringClass().getAnnotation(SpdyIgnore.class);
            }
            if(spdyIgnore != null) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
        }
        if(https) {
            HttpsIgnore httpsIgnore = method.getAnnotation(HttpsIgnore.class);
            if(httpsIgnore == null) {
                httpsIgnore = method.getMethod().getDeclaringClass().getAnnotation(HttpsIgnore.class);
            }
            if(httpsIgnore != null) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
        }
        if (proxy) {
            if (method.getAnnotation(ProxyIgnore.class) != null ||
                    method.getMethod().getDeclaringClass().isAnnotationPresent(ProxyIgnore.class)) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
        }
        try {
            if (runs > 1) {
                Statement statement = methodBlock(method);
                Description description = describeChild(method);
                for (Description desc : description.getChildren()) {
                    runLeaf(statement, desc, notifier);
                }
            } else {
                super.runChild(method, notifier);
            }
        } finally {
            TestHttpClient.afterTest();
        }
    }

    @Override
    protected String testName(FrameworkMethod method) {
        if (!proxy && !ajp) {
            return super.testName(method);
        } else {
            StringBuilder sb = new StringBuilder(super.testName(method));
            if (proxy) {
                sb.append("{proxy}");
            }
            if (ajp) {
                sb.append("{ajp}");
            }
            if(spdy) {
                sb.append("{spdy}");
            }
            if(spdyPlain) {
                sb.append("{spdy-plain}");
            }
            if(https) {
                sb.append("{https}");
            }
            if(http2) {
                sb.append("{http2}");
            }
            return sb.toString();
        }
    }


    /**
     * Sets the root handler for the default web server
     *
     * @param handler The handler to use
     */
    public static void setRootHandler(HttpHandler handler) {
        if ((proxy || spdy || spdyPlain) && !ajp) {
            //if we are testing HTTP proxy we always add the SSLHeaderHandler
            //this allows the SSL information to be propagated to be backend
            handler = new SSLHeaderHandler(new ProxyPeerAddressHandler(handler));
        }
        if(spdy) {
            final HttpHandler existing = handler;
            handler = new HttpHandler() {
                @Override
                public void handleRequest(HttpServerExchange exchange) throws Exception {
                    if(!exchange.getRequestHeaders().contains(":method")) {
                        //make sure we have not fallen back to a stanard HTTPS connection
                        throw new RuntimeException("Not a SPDY connection");
                    }
                    existing.handleRequest(exchange);
                }
            };
        }
        if (dump) {
            rootHandler.next = new RequestDumpingHandler(handler);
        } else {
            rootHandler.next = handler;
        }
    }

    /**
     * When using the default SSL settings returns the corresponding client context.
     * <p/>
     * If a test case is initialising a custom server side SSLContext then the test case will be responsible for creating it's
     * own client side.
     *
     * @return The client side SSLContext.
     */
    public static SSLContext getClientSSLContext() {
        return clientSslContext;
    }

    /**
     * Start the SSL server using the default settings.
     * <p/>
     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
     * single client, the client authentication mode is set to 'REQUESTED' to optionally allow progression to CLIENT-CERT
     * authentication.
     */
    public static void startSSLServer() throws IOException {
        SSLContext serverContext = getServerSslContext();
        clientSslContext = createClientSslContext();

        startSSLServer(serverContext, OptionMap.create(SSL_CLIENT_AUTH_MODE, REQUESTED));
    }

    public static SSLContext createClientSslContext() {
        try {
            return createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static SSLContext getServerSslContext() {
        try {
            return createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Start the SSL server using the default ssl context and the provided option map
     * <p/>
     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
     * single client. Client cert mode is not set by default
     */
    public static void startSSLServer(OptionMap optionMap) throws IOException {
        SSLContext serverContext = getServerSslContext();
        clientSslContext = createClientSslContext();
        startSSLServer(optionMap, proxyAcceptListener != null ? proxyAcceptListener : acceptListener);
    }

    /**
     * Start the SSL server using the default ssl context and the provided option map
     * <p/>
     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
     * single client. Client cert mode is not set by default
     */
    public static void startSSLServer(OptionMap optionMap, ChannelListener openListener) throws IOException {
        SSLContext serverContext = createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE));
        clientSslContext = createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE));
        startSSLServer(serverContext, optionMap, openListener);
    }

    /**
     * Start the SSL server using a custom SSLContext with additional options to pass to the JsseXnioSsl instance.
     *
     * @param context - The SSLContext to use for JsseXnioSsl initialisation.
     * @param options - Additional options to be passed to the JsseXnioSsl, this will be merged with the default options where
     *                applicable.
     */
    public static void startSSLServer(final SSLContext context, final OptionMap options) throws IOException {
        startSSLServer(context, options, proxyAcceptListener != null ? proxyAcceptListener : acceptListener);
    }

    /**
     * Start the SSL server using a custom SSLContext with additional options to pass to the JsseXnioSsl instance.
     *
     * @param context - The SSLContext to use for JsseXnioSsl initialisation.
     * @param options - Additional options to be passed to the JsseXnioSsl, this will be merged with the default options where
     *                applicable.
     */
    public static void startSSLServer(final SSLContext context, final OptionMap options, ChannelListener openListener) throws IOException {
        startSSLServer(context, options, openListener, getHostSSLPort(DEFAULT));
    }


    /**
     * Start the SSL server using a custom SSLContext with additional options to pass to the JsseXnioSsl instance.
     *
     * @param context - The SSLContext to use for JsseXnioSsl initialisation.
     * @param options - Additional options to be passed to the JsseXnioSsl, this will be merged with the default options where
     *                applicable.
     */
    public static void startSSLServer(final SSLContext context, final OptionMap options, ChannelListener openListener, int port) throws IOException {
        if (isApacheTest()) {
            return;
        }
        OptionMap combined = OptionMap.builder().addAll(serverOptions).addAll(options)
                .set(Options.USE_DIRECT_BUFFERS, true)
                .getMap();

        XnioSsl xnioSsl = new JsseXnioSsl(xnio, combined, context);
        sslServer = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)),
                port), openListener, combined);
        sslServer.resumeAccepts();
    }

    private static boolean isApacheTest() {
        return ajp && !proxy;
    }

    /**
     * Stop any previously created SSL server - as this is for test clean up calling when no SSL server is running will not
     * cause an error.
     */
    public static void stopSSLServer() throws IOException {
        if (sslServer != null) {
            sslServer.close();
            sslServer = null;
        }
        clientSslContext = null;
    }

    public static String getHostAddress(String serverName) {
        return System.getProperty(serverName + ".server.address", "localhost");
    }

    public static int getHostPort(String serverName) {
        if (isApacheTest()) {
            return APACHE_PORT;
        }
        return Integer.getInteger(serverName + ".server.port", 7777);
    }

    public static int getHostSSLPort(String serverName) {
        if (isApacheTest()) {
            return APACHE_SSL_PORT;
        }
        return Integer.getInteger(serverName + ".server.sslPort", 7778);
    }

    public static OptionMap getUndertowOptions() {
        return openListener.getUndertowOptions();
    }

    public static void setUndertowOptions(final OptionMap options) {
        openListener.setUndertowOptions(options);
    }

    public static XnioWorker getWorker() {
        return worker;
    }

    public static class Parameterized extends org.junit.runners.Parameterized {

        public Parameterized(Class<?> klass) throws Throwable {
            super(klass);
        }

        @Override
        public void run(final RunNotifier notifier) {
            runInternal(notifier);
            super.run(notifier);
        }
    }

    public static boolean isAjp() {
        return ajp;
    }

    public static boolean isProxy() {
        return proxy;
    }

    public static boolean isSpdy() {
        return spdy || spdyPlain;
    }

    /**
     * The root handler is tied to the connection, and AJP can re-use connections for different tests, so we
     * use a delegating handler to chance the next handler after the root.
     * <p/>
     * TODO: should we re-read the root handler for every request?
     */
    private static final class DelegatingHandler implements HttpHandler {

        volatile HttpHandler next;

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            next.handleRequest(exchange);
        }
    }
}
