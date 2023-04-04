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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.alpn.ALPNManager;
import io.undertow.protocols.alpn.ALPNProvider;
import io.undertow.protocols.alpn.JettyAlpnProvider;
import io.undertow.protocols.ssl.SNIContextMatcher;
import io.undertow.protocols.ssl.SNISSLContext;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.security.impl.GSSAPIAuthenticationMechanism;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.OpenListener;
import io.undertow.server.handlers.ProxyPeerAddressHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.SSLHeaderHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.protocol.ajp.AjpOpenListener;
import io.undertow.server.protocol.http.AlpnOpenListener;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.server.protocol.http2.Http2OpenListener;
import io.undertow.server.protocol.http2.Http2UpgradeHandler;
import io.undertow.util.Headers;
import io.undertow.util.NetworkUtils;
import io.undertow.util.SingleByteStreamSinkConduit;
import io.undertow.util.SingleByteStreamSourceConduit;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;
import org.wildfly.openssl.OpenSSLProvider;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.ssl.XnioSsl;

import static io.undertow.server.handlers.ResponseCodeHandler.HANDLE_404;
import static io.undertow.testutils.StopServerWithExternalWorkerUtils.stopWorker;
import static io.undertow.testutils.StopServerWithExternalWorkerUtils.waitWorkerRunnableCycle;
import static org.xnio.Options.SSL_CLIENT_AUTH_MODE;
import static org.xnio.SslClientAuthMode.REQUESTED;

/**
 * A class that starts a server before the test suite. By swapping out the root handler
 * tests can test various server functionality without continually starting and stopping the server.
 * <p>
 * This runner adds two Annotations for specifying invocation points: {@link DefaultServer.BeforeServerStarts}
 * and {@link DefaultServer.AfterServerStops}.
 * </p><p>When any of those annotated methods are defined in the test class, tests are run in the following order:</p>
 * <li>{@link DefaultServer.BeforeServerStarts @BeforeServerStarts} static methods are invoked</li>
 * <li>the server is {@link #startServer() started}, before any test method is invoked</li>
 * <li>{@code @BeforeClass} methods are invoked, meaning subclasses can rely on getter methods
 * returning values that were initialized in the previous step</li>
 * <li>all test methods are invoked, with @Before and @After methods being invoked before and after them as in
 * any normal JUnit test case</li>
 * <li>{@code @AfterClass} methods are invoked, so subclasses can still read values initialized when the server
 * started via getter methods in this class</li>
 * <li>server is finally {@link #stopServer() stopped}</li>
 * <li>{@link DefaultServer.AfterServerStops @AfterServerStops} methods are invoked</li>
 * </p>
 * <p>{@code @ClassRule}s are applied to the whole block above, meaning that the {@code Statement} passed to the
 * corresponding {@code TestRule} contains the steps as described in the above list.</p>
 *<p>
 * If no {@link DefaultServer.BeforeServerStarts @BeforeServerStarts}/{@link DefaultServer.AfterServerStops @AfterServerStops}
 * methods are specified, all methods in the test case are guaranteed to run in the context of a running {@code DefaultServer}.
 * Notice, however, that in this particular case the server might not be started just before a test case is run,
 * and will not be shutdown when the same test case is finished. The reason for this is that this runner prevents the
 * extra cost of starting and stopping the server several times when doing a test run if that step is not needed.
 * Usually, this runner will start the server only once for a whole test run. For the same reason, the server will be
 * stopped only after all test classes in the test run have finished. The only exception is when there is a test case that
 * contains a {@link DefaultServer.BeforeServerStarts @BeforeServerStarts} method. In that case, the server started by
 * previous test cases will be stopped before anything so that the {@code @BeforeServerStarts} method sticks to the
 * contract and can be run before the server starts. Likewise, the server is stopped before invoking {@code @AfterServerStops}
 * methods. Before proceeding to running the next test cases in the test run, this runner will restart the server once
 * more, but it will refrain from doing so again for the following test cases, unless it faces anoter test with the
 * {@code @BeforeServerStarts}/{@code @AfterServerStops} methods.
 * </p>
 * @author Stuart Douglas
 * @author Flavia Rainone
 */
public class DefaultServer extends BlockJUnit4ClassRunner {

    /**
     * Static methods marked with this annotation will be invoked before the server is started.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BeforeServerStarts {}

    /**
     * Static methods marked with this annotation will be invoked after the server is stopped.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AfterServerStops {}

    private static final Throwable OPENSSL_FAILURE;

    static {
        Throwable failure = null;
        try {
            OpenSSLProvider.register();
        } catch (Throwable t) {
            failure = t;
        }
        OPENSSL_FAILURE = failure;
    }

    static final String DEFAULT = "default";
    private static final int PROXY_OFFSET = 1111;
    public static final int APACHE_PORT = 9080;
    public static final int APACHE_SSL_PORT = 9443;
    public static final int BUFFER_SIZE = Integer.getInteger("test.bufferSize", 1024 * 16 - 20);
    public static final DebuggingSlicePool SSL_BUFFER_POOL = new DebuggingSlicePool(new DefaultByteBufferPool(true, 17 * 1024));

    private static OptionMap serverOptions;
    private static OptionMap.Builder serverOptionMapBuilder = OptionMap.builder();
    private static OpenListener openListener;
    private static ChannelListener acceptListener;
    private static OpenListener proxyOpenListener;
    private static ChannelListener proxyAcceptListener;
    private static XnioWorker worker;
    private static AcceptingChannel<? extends StreamConnection> server;
    private static AcceptingChannel<? extends StreamConnection> proxyServer;
    private static AcceptingChannel<? extends StreamConnection> sslServer;
    private static SSLContext clientSslContext;

    private static final String SERVER_KEY_STORE = "server.keystore";
    private static final String SERVER_TRUST_STORE = "server.truststore";
    private static final String CLIENT_KEY_STORE = "client.keystore";
    private static final String CLIENT_TRUST_STORE = "client.truststore";
    private static final char[] STORE_PASSWORD = "password".toCharArray();

    private static final boolean ajp = Boolean.getBoolean("test.ajp");
    private static final boolean h2 = Boolean.getBoolean("test.h2");
    private static final boolean h2c = Boolean.getBoolean("test.h2c");
    private static final boolean h2cUpgrade = Boolean.getBoolean("test.h2c-upgrade");
    private static final boolean https = Boolean.getBoolean("test.https");
    private static final boolean proxy = Boolean.getBoolean("test.proxy");
    private static final boolean apache = Boolean.getBoolean("test.apache");
    private static final boolean dump = Boolean.getBoolean("test.dump");
    private static final boolean single = Boolean.getBoolean("test.single");
    private static final boolean openssl = Boolean.getBoolean("test.openssl");
    private static final boolean ipv6 = Boolean.getBoolean("test.ipv6");
    private static final int runs = Integer.getInteger("test.runs", 1);

    private static final DelegatingHandler rootHandler = new DelegatingHandler();

    private static final DebuggingSlicePool pool = new DebuggingSlicePool(new DefaultByteBufferPool(true, BUFFER_SIZE, 1000, 10, 100));

    private static LoadBalancingProxyClient loadBalancingProxyClient;

    /** A link to the {@link #startServer()} method using JUnit framework method API. */
    private static final List<FrameworkMethod> startServerMethod;
    /** A link to the {@link #stopServer()}  method using JUnit framework method API. */
    private static final List<FrameworkMethod> stopServerMethod;

    static {
        startServerMethod = new ArrayList<>(1);
        try {
            startServerMethod.add(new FrameworkMethod(DefaultServer.class.getDeclaredMethod("startServer")));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        stopServerMethod = new ArrayList<>(1);
        try {
            stopServerMethod.add(new FrameworkMethod(DefaultServer.class.getDeclaredMethod("stopServer")));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static KeyStore loadKeyStore(final String name) throws IOException {
        final InputStream stream = DefaultServer.class.getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new RuntimeException("Could not load keystore");
        }
        try {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, STORE_PASSWORD);

            return loadedKeystore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new IOException(String.format("Unable to load KeyStore %s", name), e);
        } finally {
            IoUtils.safeClose(stream);
        }
    }

    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore, boolean client) throws IOException {
        return createSSLContext(keyStore, trustStore, "TLSv1.2", client);
    }

    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore, String protocol, boolean client) throws IOException {
        final KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, STORE_PASSWORD);
            keyManagers = keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        }

        final TrustManager[] trustManagers;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new IOException("Unable to initialise TrustManager[]", e);
        }

        final SSLContext sslContext;
        try {
            if (openssl && !client) {
                sslContext = SSLContext.getInstance("openssl.TLS");
            } else {
                sslContext = SSLContext.getInstance(protocol);
            }
            sslContext.init(keyManagers, trustManagers, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("Unable to create and initialise the SSLContext", e);
        }
        if (!client) {
            SNIContextMatcher matcher = new SNIContextMatcher.Builder().setDefaultContext(sslContext)
                    .addMatch("localhost", sslContext)
                    .build();
            return new SNISSLContext(matcher);
        } else {
            return sslContext;
        }
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

    @SuppressWarnings("deprecation")
    public static void setupProxyHandlerForSSL(ProxyHandler proxyHandler) {
        proxyHandler.addRequestHeader(Headers.SSL_CLIENT_CERT, "%{SSL_CLIENT_CERT}", DefaultServer.class.getClassLoader());
        proxyHandler.addRequestHeader(Headers.SSL_CIPHER, "%{SSL_CIPHER}", DefaultServer.class.getClassLoader());
        proxyHandler.addRequestHeader(Headers.SSL_SESSION_ID, "%{SSL_SESSION_ID}", DefaultServer.class.getClassLoader());
    }

    public static ByteBufferPool getBufferPool() {
        return pool;
    }

    public static Supplier<XnioWorker> getWorkerSupplier() {
        return DefaultServer::getWorker;
    }

    @Override
    public Description getDescription() {
        return super.getDescription();
    }

    /**
     * Returns a {@link Statement} that runs the test methods from within a
     * {@link RunDefaultServer} statement. If any {@link BeforeServerStarts @BeforeServerStarts}
     * methods are found in the test class, server is stopped if it is already running, and the
     * returned statement will invoke those methods before {@code RunDefaultServer} statement.
     * In the same way, if {@link AfterServerStops @AfterServerStops} methods are found in the
     * test class, the returned statement will cause the server to stop after this test case runs,
     * and will cause those methods to be invoked after that.
     */
    protected Statement classBlock(final RunNotifier notifier) {
        return createClassStatement(getTestClass(), notifier, super.classBlock(notifier));
    }

    private static Statement createClassStatement(final TestClass testClass, final RunNotifier notifier, Statement classBlock) {
        final RunDefaultServer defaultServerStatement = new RunDefaultServer(classBlock, notifier);
        Statement statement = defaultServerStatement;
        final List<FrameworkMethod> beforeServerStarts = testClass.getAnnotatedMethods(BeforeServerStarts.class);
        if (!beforeServerStarts.isEmpty()) {
            // stopServer that might be already up of we're running the full test suite instead of a single test case
            stopServer();
            statement = new RunBefores(statement, beforeServerStarts, null);
        }
        final List<FrameworkMethod> afterServerStops = testClass.getAnnotatedMethods(AfterServerStops.class);
        if (!afterServerStops.isEmpty()) {
            defaultServerStatement.stopTheServerWhenDone();
            statement = new RunAfters(statement, afterServerStops, null);
        }
        return statement;
    }

    public static AcceptingChannel<? extends StreamConnection> getProxyServer() {
        return proxyServer;
    }

    @Override
    public void run(final RunNotifier notifier) {
        addRunNotifierListener(notifier);
        super.run(notifier);
    }

    private static void addRunNotifierListener(final RunNotifier notifier) {
        notifier.addListener(new RunListener() {
            @Override
            public void testStarted(Description description) throws Exception {
                DebuggingSlicePool.currentLabel = description.getClassName() + "." + description.getMethodName();
                super.testStarted(description);
            }

            @Override
            public void testFinished(Description description) throws Exception {
                boolean empty = DebuggingSlicePool.BUFFERS.isEmpty();
                if (!empty) {
                    try {
                        long end = System.currentTimeMillis() + 20000;
                        do {
                            Thread.sleep(200);
                            empty = DebuggingSlicePool.BUFFERS.isEmpty();
                        } while (!empty && System.currentTimeMillis() < end);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (!empty) {
                        for (DebuggingSlicePool.DebuggingBuffer b : DebuggingSlicePool.BUFFERS) {
                            b.getAllocationPoint().printStackTrace();
                            notifier.fireTestFailure(new Failure(description, new RuntimeException("Buffer Leak " + b.getLabel(), b.getAllocationPoint())));
                        }
                        DebuggingSlicePool.BUFFERS.clear();
                    }
                }
                super.testFinished(description);
            }
       });
    }

    /**
     * Starts the server if it is not up, and initiates the static fields in this class. This method is invoked
     * automatically once before your tests are triggered when using {@code DefaultServer} as a {@code Runner}.
     * After this method executes, getter methods can be invoked safely.
     * <p>
     * To perform an action in your test before the server starts, such as {@link #setServerOptions(OptionMap)},
     * use {@link BeforeServerStarts @BeforeServerStarts} methods. To perform an action precisely after the server
     * starts and before the test method runs, use {@code @BeforeClass} or {@code @Before} methods.
     */
    public static boolean startServer() {
        if (openssl && OPENSSL_FAILURE != null) {
            throw new RuntimeException(OPENSSL_FAILURE);
        }
        if (server != null) {
            return false;
        }
        Xnio xnio = Xnio.getInstance("nio", DefaultServer.class.getClassLoader());
        try {
            worker = xnio.createWorker(
                    OptionMap.builder().set(Options.WORKER_IO_THREADS, 8).set(Options.CONNECTION_HIGH_WATER, 1000000).set(Options.CONNECTION_LOW_WATER, 1000000).set(Options.WORKER_TASK_CORE_THREADS, 30).set(Options.WORKER_TASK_MAX_THREADS, 30).set(Options.TCP_NODELAY, true).set(Options.CORK, true).getMap());

            serverOptions = serverOptionMapBuilder.set(Options.TCP_NODELAY, true).set(Options.BACKLOG, 1000).set(Options.REUSE_ADDRESSES, true).set(Options.BALANCING_TOKENS, 1).set(Options.BALANCING_CONNECTIONS, 2).getMap();
            final SSLContext serverContext = createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE), false);
            UndertowXnioSsl ssl = new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, SSL_BUFFER_POOL, serverContext);
            if (ajp) {
                openListener = new AjpOpenListener(pool);
                acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));
                if (apache) {
                    int port = 8888;
                    server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), port), acceptListener, serverOptions);
                } else {
                    server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), 7777 + PROXY_OFFSET), acceptListener, serverOptions);

                    proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true));
                    proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                    proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                    loadBalancingProxyClient = new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER)
                        .setMaxQueueSize(20)
                        .addHost(new URI("ajp", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null));
                    ProxyHandler proxyHandler = ProxyHandler.builder()
                        .setProxyClient(loadBalancingProxyClient)
                        .setMaxRequestTime(60000)
                        .setNext(HANDLE_404)
                        .setReuseXForwarded(true)
                        .build();
                    proxyOpenListener.setRootHandler(proxyHandler);
                    proxyServer.resumeAccepts();
                }
            } else if (h2 && isAlpnEnabled()) {
                openListener = new Http2OpenListener(pool, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true, UndertowOptions.HTTP2_PADDING_SIZE, 10));
                acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(new AlpnOpenListener(pool).addProtocol(Http2OpenListener.HTTP2, (io.undertow.server.DelegateOpenListener) openListener, 10)));

                SSLContext clientContext = createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE), true);
                server = ssl.createSslConnectionServer(worker, new InetSocketAddress(getHostAddress("default"), 7777 + PROXY_OFFSET), acceptListener, serverOptions);
                server.resumeAccepts();

                proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true));
                proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                loadBalancingProxyClient = new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER)
                    .setMaxQueueSize(20)
                    .addHost(new URI("h2", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null), null, new UndertowXnioSsl(xnio, OptionMap.EMPTY, SSL_BUFFER_POOL, clientContext), OptionMap.create(UndertowOptions.ENABLE_HTTP2, true));
                ProxyHandler proxyHandler = ProxyHandler.builder()
                    .setProxyClient(loadBalancingProxyClient)
                    .setMaxRequestTime(60000)
                    .setNext(HANDLE_404)
                    .setReuseXForwarded(true)
                    .build();
                setupProxyHandlerForSSL(proxyHandler);
                proxyOpenListener.setRootHandler(proxyHandler);
                proxyServer.resumeAccepts();
            } else if (h2c || h2cUpgrade) {
                openListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true, UndertowOptions.HTTP2_PADDING_SIZE, 10));
                acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));

                InetSocketAddress targetAddress = new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT) + PROXY_OFFSET);
                server = worker.createStreamConnectionServer(targetAddress, acceptListener, serverOptions);

                proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true));
                proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                loadBalancingProxyClient = new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER)
                        .setMaxQueueSize(20)
                        .addHost(new URI(h2cUpgrade ? "http" : "h2c-prior", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null), null, null, OptionMap.create(UndertowOptions.ENABLE_HTTP2, true));
                ProxyHandler proxyHandler = ProxyHandler.builder()
                    .setProxyClient(loadBalancingProxyClient)
                    .setMaxRequestTime(60000)
                    .setNext(HANDLE_404)
                    .setReuseXForwarded(true)
                    .build();
                setupProxyHandlerForSSL(proxyHandler);
                proxyOpenListener.setRootHandler(proxyHandler);
                proxyServer.resumeAccepts();

            } else if (https) {
                XnioSsl clientSsl = new UndertowXnioSsl(xnio, OptionMap.EMPTY, SSL_BUFFER_POOL, createClientSslContext());
                openListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true));
                acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));
                server = ssl.createSslConnectionServer(worker, new InetSocketAddress(getHostAddress("default"), 7777 + PROXY_OFFSET), acceptListener, serverOptions);
                server.getAcceptSetter().set(acceptListener);
                server.resumeAccepts();

                proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true));
                proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                loadBalancingProxyClient = new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER)
                    .setMaxQueueSize(20)
                    .addHost(new URI("https", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null), clientSsl);
                ProxyHandler proxyHandler = ProxyHandler.builder()
                    .setProxyClient(loadBalancingProxyClient)
                    .setMaxRequestTime(60000)
                    .setNext(HANDLE_404)
                    .setReuseXForwarded(true)
                    .build();
                setupProxyHandlerForSSL(proxyHandler);
                proxyOpenListener.setRootHandler(proxyHandler);
                proxyServer.resumeAccepts();

            } else {
                if (h2) {
                    UndertowLogger.ROOT_LOGGER.error("HTTP2 selected but Netty ALPN was not on the boot class path");
                }
                openListener = new HttpOpenListener(pool, OptionMap.builder().set(UndertowOptions.BUFFER_PIPELINED_DATA, true).set(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, true).set(UndertowOptions.REQUIRE_HOST_HTTP11, true).getMap());
                acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));
                if (!proxy) {
                    server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), acceptListener, serverOptions);
                } else {
                    InetSocketAddress targetAddress = new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT) + PROXY_OFFSET);
                    server = worker.createStreamConnectionServer(targetAddress, acceptListener, serverOptions);

                    proxyOpenListener = new HttpOpenListener(pool, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true));
                    proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                    proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                    loadBalancingProxyClient = new LoadBalancingProxyClient(GSSAPIAuthenticationMechanism.EXCLUSIVITY_CHECKER)
                        .setMaxQueueSize(20)
                        .addHost(new URI("http", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null));
                    ProxyHandler proxyHandler = ProxyHandler.builder()
                        .setProxyClient(loadBalancingProxyClient)
                        .setMaxRequestTime(60000)
                        .setNext(HANDLE_404)
                        .setReuseXForwarded(true)
                        .build();
                    setupProxyHandlerForSSL(proxyHandler);
                    proxyOpenListener.setRootHandler(proxyHandler);
                    proxyServer.resumeAccepts();
                }
            }
            if (h2cUpgrade) {
                openListener.setRootHandler(new Http2UpgradeHandler(rootHandler));
            } else {
                openListener.setRootHandler(rootHandler);
            }
            server.resumeAccepts();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    /**
     * Stops the server and resets the fields. This method is invoked automatically after all method tests have
     * run when using {@code DefaultServer} as a {@code Runner}. After this method executes, getter methods will
     * return {@code null}.
     * <p>
     * To do any after test cleanup before server actually stops, use either {@code @After} or {@code @AfterClass}
     * methods. To perform an action after the server stops, use {@link AfterServerStops @AfterServerStops} methods.
     */
    public static final void stopServer() {
        try {
            if (server != null) {
                server.close();
            }
            if (proxyServer != null) {
                proxyServer.close();
            }
            stopSSLServer();
            if (worker != null) {
                stopWorker(worker);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            worker = null;
            serverOptions = null;
            openListener = null;
            acceptListener = null;
            server = null;
            proxyOpenListener = null;
            proxyAcceptListener = null;
            proxyServer = null;
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
        return (StreamConnection channel) -> {
            channel.getSinkChannel().setConduit(new SingleByteStreamSinkConduit(channel.getSinkChannel().getConduit(), 10000));
            channel.getSourceChannel().setConduit(new SingleByteStreamSourceConduit(channel.getSourceChannel().getConduit(), 10000));
            listener.handleEvent(channel);
        };
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        AjpIgnore ajpIgnore = method.getAnnotation(AjpIgnore.class);
        if (ajpIgnore == null) {
            ajpIgnore = method.getMethod().getDeclaringClass().getAnnotation(AjpIgnore.class);
        }
        if (ajp && ajpIgnore != null) {
            if (apache || !ajpIgnore.apacheOnly()) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
        }
        if (h2 || h2c || ajp || h2cUpgrade) {
            //h2c-upgrade we still allow HTTP1
            HttpOneOnly httpOneOnly = method.getAnnotation(HttpOneOnly.class);
            if (httpOneOnly == null) {
                httpOneOnly = method.getMethod().getDeclaringClass().getAnnotation(HttpOneOnly.class);
            }
            if (httpOneOnly != null) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
            if (h2) {
                assumeAlpnEnabled();
            }
        }
        if (https) {
            HttpsIgnore httpsIgnore = method.getAnnotation(HttpsIgnore.class);
            if (httpsIgnore == null) {
                httpsIgnore = method.getMethod().getDeclaringClass().getAnnotation(HttpsIgnore.class);
            }
            if (httpsIgnore != null) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
        }
        if (isProxy()) {
            if (method.getAnnotation(ProxyIgnore.class) != null ||
                    method.getMethod().getDeclaringClass().isAnnotationPresent(ProxyIgnore.class) ||
                    getTestClass().getJavaClass().isAnnotationPresent(ProxyIgnore.class)) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
        }
        if (ipv6) {
            if (method.getAnnotation(IPv6Ignore.class) != null ||
                    method.getMethod().getDeclaringClass().isAnnotationPresent(IPv6Ignore.class) ||
                    getTestClass().getJavaClass().isAnnotationPresent(IPv6Ignore.class)) {
                notifier.fireTestIgnored(describeChild(method));
                return;
            }
        } else {
            if (method.getAnnotation(IPv6Only.class) != null ||
                    method.getMethod().getDeclaringClass().isAnnotationPresent(IPv6Only.class) ||
                    getTestClass().getJavaClass().isAnnotationPresent(IPv6Only.class)) {
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
        if (!isProxy()) {
            return super.testName(method);
        } else {
            StringBuilder sb = new StringBuilder(super.testName(method));
            if (isProxy()) {
                sb.append("[proxy]");
            }
            if (ajp) {
                sb.append("[ajp]");
            }
            if (https) {
                sb.append("[https]");
            }
            if (h2) {
                sb.append("[http2]");
            }
            if (h2c) {
                sb.append("[http2-clear]");
            }
            if (h2cUpgrade) {
                sb.append("[http2-clear-upgrade]");
            }
            if (ipv6) {
                sb.append("[ipv6]");
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
        if ((isProxy()) && !ajp) {
            //if we are testing HTTP proxy we always add the SSLHeaderHandler
            //this allows the SSL information to be propagated to the backend
            handler = new SSLHeaderHandler(new ProxyPeerAddressHandler(handler));
        }
        if (dump) {
            rootHandler.next = new RequestDumpingHandler(handler);
        } else {
            rootHandler.next = handler;
        }
    }

    /**
     * When using the default SSL settings returns the corresponding client context.
     * <p>
     * If a test case is initialising a custom server side SSLContext then the test case will be responsible for creating it's
     * own client side.
     *
     * @return The client side SSLContext.
     */
    public static SSLContext getClientSSLContext() {
        if (clientSslContext == null) {
            clientSslContext = createClientSslContext();
        }
        return clientSslContext;
    }

    /**
     * Start the SSL server using the default settings.
     * <p>
     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
     * single client, the client authentication mode is set to 'REQUESTED' to optionally allow progression to CLIENT-CERT
     * authentication.
     */
    public static void startSSLServer() throws IOException {
        SSLContext serverContext = getServerSslContext();
        getClientSSLContext();

        startSSLServer(serverContext, OptionMap.create(SSL_CLIENT_AUTH_MODE, REQUESTED, Options.SSL_ENABLED_PROTOCOLS, Sequence.of("TLSv1.2")));
    }

    public static SSLContext createClientSslContext() {
        return createClientSslContext("TLSv1.2");
    }

    public static SSLContext createClientSslContext(String protocol) {
        try {
            return createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE), protocol, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static SSLContext getServerSslContext() {
        try {
            return createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE), false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Start the SSL server using the default ssl context and the provided option map
     * <p>
     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
     * single client. Client cert mode is not set by default
     */
    public static void startSSLServer(OptionMap optionMap) throws IOException {
        clientSslContext = createClientSslContext();
        startSSLServer(optionMap, proxyAcceptListener != null ? proxyAcceptListener : acceptListener);
    }

    /**
     * Start the SSL server using the default ssl context and the provided option map
     * <p>
     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
     * single client. Client cert mode is not set by default
     */
    public static void startSSLServer(OptionMap optionMap, ChannelListener openListener) throws IOException {
        SSLContext serverContext = createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE), false);
        clientSslContext = createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE), true);
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

        UndertowXnioSsl ssl = new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, SSL_BUFFER_POOL, context);
        sslServer = ssl.createSslConnectionServer(worker, new InetSocketAddress(getHostAddress("default"), port), openListener, combined);
        sslServer.getAcceptSetter().set(openListener);
        sslServer.resumeAccepts();
    }

    private static boolean isApacheTest() {
        return apache;
    }

    /**
     * Stop any previously created SSL server - as this is for test clean up calling when no SSL server is running will not
     * cause an error.
     */
    public static void stopSSLServer() throws IOException {
        boolean shuttingDown = false;
        if (sslServer != null) {
            sslServer.close();
            sslServer = null;
            shuttingDown = true;
        }
        clientSslContext = null;
        if (proxyOpenListener != null) {
            proxyOpenListener.closeConnections();
            shuttingDown = true;
        }
        if (openListener != null) {
            openListener.closeConnections();
            shuttingDown = true;
        }
        if (shuttingDown) {
            // TODO replace this by the mechanism described in UNDERTOW-1648 once it is implemented
            waitWorkerRunnableCycle(worker);
        }
    }

    public static String getHostAddress(String serverName) {
        return System.getProperty(serverName + ".server.address", "localhost");
    }

    public static String getHostAddress() {
        return getHostAddress(DEFAULT);
    }

    public static int getHostPort(String serverName) {
        if (isApacheTest()) {
            return APACHE_PORT;
        }
        return Integer.getInteger(serverName + ".server.port", 7777);
    }

    public static int getHostPort() {
        return getHostPort(DEFAULT);
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
        OptionMap.Builder builder = OptionMap.builder().addAll(options);
        if (h2c) {
            builder.set(UndertowOptions.ENABLE_HTTP2, true);
        }
        if (openListener != null) {
            openListener.setUndertowOptions(builder.getMap());
            openListener.closeConnections();
            if (proxyOpenListener != null) {
                proxyOpenListener.closeConnections();
            }
            if (loadBalancingProxyClient != null) {
                loadBalancingProxyClient.closeCurrentConnections();
            }
            waitWorkerRunnableCycle(worker);
        }
    }

    public static OptionMap getProxyOptions() {
        if (proxyOpenListener != null) {
            return proxyOpenListener.getUndertowOptions();
        } else {
            return null;
        }
    }

    public static void setProxyOptions(final OptionMap options) {
        OptionMap.Builder builder = OptionMap.builder().addAll(options);
        builder = builder.set(UndertowOptions.BUFFER_PIPELINED_DATA, true);

        if (proxyOpenListener != null) {
            proxyOpenListener.setUndertowOptions(builder.getMap());
            proxyOpenListener.closeConnections();
            waitWorkerRunnableCycle(worker);
        }
    }

    public static void setServerOptions(final OptionMap options) {
        serverOptionMapBuilder = OptionMap.builder().addAll(options);
    }

    public static XnioWorker getWorker() {
        return worker;
    }

    /**
     * Runner that works in the same way as {@link DefaultServer} with added support to
     * parameterized tests.
     */
    public static class Parameterized extends org.junit.runners.Parameterized {

        public Parameterized(Class<?> klass) throws Throwable {
            super(klass);
        }

        @Override
        public void run(final RunNotifier notifier) {
            addRunNotifierListener(notifier);
            super.run(notifier);
        }

        @Override protected Statement classBlock(RunNotifier notifier) {
            return createClassStatement(getTestClass(), notifier, super.classBlock(notifier));
        }
    }

    public static boolean isAjp() {
        return ajp;
    }

    public static boolean isProxy() {
        return proxy || https || h2 || h2c || ajp || h2cUpgrade;
    }

    public static boolean isHttps() {
        return https;
    }

    public static boolean isH2() {
        return h2 || h2c || h2cUpgrade;
    }

    public static boolean isH2upgrade() {
        return h2cUpgrade;
    }

    public static boolean isIpv6() {
        return ipv6;
    }

    /**
     * The root handler is tied to the connection, and AJP can re-use connections for different tests, so we
     * use a delegating handler to chance the next handler after the root.
     * <p>
     * TODO: should we re-read the root handler for every request?
     */
    private static final class DelegatingHandler implements HttpHandler {

        volatile HttpHandler next;

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            next.handleRequest(exchange);
        }
    }

    private static Boolean alpnEnabled;

    private static boolean isAlpnEnabled() {
        if (alpnEnabled == null) {
            //we use the client context, as the server one is wrapped by a SNISSLEngine
            //so we can't tell that ALPN is enabled or now
            SSLEngine engine = getClientSSLContext().createSSLEngine();
            ALPNProvider provider = ALPNManager.INSTANCE.getProvider(engine);
            if (provider instanceof JettyAlpnProvider) {
                alpnEnabled = System.getProperty("alpn-boot-string") != null;
            } else {
                alpnEnabled = provider != null;
            }
        }
        return alpnEnabled;
    }

    public static void assumeAlpnEnabled() {
        Assume.assumeTrue(isAlpnEnabled());
    }
}
