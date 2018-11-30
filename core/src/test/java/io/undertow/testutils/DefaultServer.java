/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.function.Supplier;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Assume;
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
import org.wildfly.openssl.OpenSSLProvider;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.OpenListener;
import io.undertow.server.handlers.ProxyPeerAddressHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.SSLHeaderHandler;
import io.undertow.util.IoUtils;
import io.undertow.util.NetworkUtils;
import io.undertow.util.UndertowOptionMap;
import io.undertow.util.UndertowOptions;

/**
 * A class that starts a server before the test suite. By swapping out the root handler
 * tests can test various server functionality without continually starting and stopping the server.
 *
 * @author Stuart Douglas
 */
public class DefaultServer extends BlockJUnit4ClassRunner {

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

    private static boolean first = true;
    private static OpenListener openListener;
    private static OpenListener proxyOpenListener;
    private static SSLContext clientSslContext;
    private static Undertow undertow;

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
    private static final boolean dump = Boolean.getBoolean("test.dump");
    private static final boolean single = Boolean.getBoolean("test.single");
    private static final boolean openssl = Boolean.getBoolean("test.openssl");
    private static final int runs = Integer.getInteger("test.runs", 1);

    private static final DelegatingHandler rootHandler = new DelegatingHandler();


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
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, STORE_PASSWORD);
            keyManagers = keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        }

        TrustManager[] trustManagers = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new IOException("Unable to initialise TrustManager[]", e);
        }

        SSLContext sslContext;
        try {
            if (openssl && !client) {
                sslContext = SSLContext.getInstance("openssl.TLS");
            } else {
                sslContext = SSLContext.getInstance("TLSv1.2");
            }
            sslContext.init(keyManagers, trustManagers, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
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
//        if (sslServer == null && !isApacheTest()) {
//            throw new IllegalStateException("SSL Server not started.");
//        }
        return "https://" + NetworkUtils.formatPossibleIpv6Address(getHostAddress(DEFAULT)) + ":" + getHostSSLPort(DEFAULT);
    }

    public DefaultServer(Class<?> klass) throws InitializationError {
        super(klass);
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
//                DebuggingSlicePool.currentLabel = description.getClassName() + "." + description.getMethodName();
                super.testStarted(description);
            }

            @Override
            public void testFinished(Description description) throws Exception {

//                if (!DebuggingSlicePool.BUFFERS.isEmpty()) {
//                    try {
//                        Thread.sleep(200);
//                        long end = System.currentTimeMillis() + 20000;
//                        while (!DebuggingSlicePool.BUFFERS.isEmpty() && System.currentTimeMillis() < end) {
//                            Thread.sleep(200);
//                        }
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                    for (DebuggingSlicePool.DebuggingBuffer b : DebuggingSlicePool.BUFFERS) {
//                        b.getAllocationPoint().printStackTrace();
//                        notifier.fireTestFailure(new Failure(description, new RuntimeException("Buffer Leak " + b.getLabel(), b.getAllocationPoint())));
//                    }
//                    DebuggingSlicePool.BUFFERS.clear();
//                }
                super.testFinished(description);
            }
        });
        runInternal(notifier);
        super.run(notifier);
    }

    private static void runInternal(final RunNotifier notifier) {
        if (openssl && OPENSSL_FAILURE != null) {
            throw new RuntimeException(OPENSSL_FAILURE);
        }
        if (first) {
            first = false;
            undertow = Undertow.builder()
                    .setHandler(rootHandler)
                    .addHttpListener(getHostPort(DEFAULT), getHostAddress(DEFAULT))
                    .build();
            undertow.start();
            notifier.addListener(new RunListener() {
                @Override
                public void testRunFinished(Result result) throws Exception {
                    super.testRunFinished(result);
                    undertow.stop();
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

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        AjpIgnore ajpIgnore = method.getAnnotation(AjpIgnore.class);
        if (ajpIgnore == null) {
            ajpIgnore = method.getMethod().getDeclaringClass().getAnnotation(AjpIgnore.class);
        }
        if (ajp && ajpIgnore != null) {
            if (!ajpIgnore.apacheOnly()) {
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
            //this allows the SSL information to be propagated to be backend
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
     * <p/>
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
     * <p/>
     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
     * single client, the client authentication mode is set to 'REQUESTED' to optionally allow progression to CLIENT-CERT
     * authentication.
     */
    public static void startSSLServer() throws IOException {
        SSLContext serverContext = getServerSslContext();
        getClientSSLContext();

//        startSSLServer(serverContext, OptionMap.create(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED, Options.SSL_ENABLED_PROTOCOLS, Sequence.of("TLSv1.2")));
    }

    public static SSLContext createClientSslContext() {
        try {
            return createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE), true);
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
     * <p/>
     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
     * single client. Client cert mode is not set by default
     */
    public static void startSSLServer(UndertowOptionMap optionMap) throws IOException {
        clientSslContext = createClientSslContext();
//        startSSLServer(optionMap, proxyAcceptListener != null ? proxyAcceptListener : acceptListener);
    }

//    /**
//     * Start the SSL server using the default ssl context and the provided option map
//     * <p/>
//     * The default settings initialise a server with a key for 'localhost' and a trust store containing the certificate of a
//     * single client. Client cert mode is not set by default
//     */
//    public static void startSSLServer(OptionMap optionMap, ChannelListener openListener) throws IOException {
//        SSLContext serverContext = createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE), false);
//        clientSslContext = createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE), true);
//        startSSLServer(serverContext, optionMap, openListener);
//    }
//
//    /**
//     * Start the SSL server using a custom SSLContext with additional options to pass to the JsseXnioSsl instance.
//     *
//     * @param context - The SSLContext to use for JsseXnioSsl initialisation.
//     * @param options - Additional options to be passed to the JsseXnioSsl, this will be merged with the default options where
//     *                applicable.
//     */
//    public static void startSSLServer(final SSLContext context, final OptionMap options) throws IOException {
//        startSSLServer(context, options, proxyAcceptListener != null ? proxyAcceptListener : acceptListener);
//    }
//
//    /**
//     * Start the SSL server using a custom SSLContext with additional options to pass to the JsseXnioSsl instance.
//     *
//     * @param context - The SSLContext to use for JsseXnioSsl initialisation.
//     * @param options - Additional options to be passed to the JsseXnioSsl, this will be merged with the default options where
//     *                applicable.
//     */
//    public static void startSSLServer(final SSLContext context, final OptionMap options, ChannelListener openListener) throws IOException {
//        startSSLServer(context, options, openListener, getHostSSLPort(DEFAULT));
//    }

//
//    /**
//     * Start the SSL server using a custom SSLContext with additional options to pass to the JsseXnioSsl instance.
//     *
//     * @param context - The SSLContext to use for JsseXnioSsl initialisation.
//     * @param options - Additional options to be passed to the JsseXnioSsl, this will be merged with the default options where
//     *                applicable.
//     */
//    public static void startSSLServer(final SSLContext context, final OptionMap options, ChannelListener openListener, int port) throws IOException {
//        if (isApacheTest()) {
//            return;
//        }
//        OptionMap combined = OptionMap.builder().addAll(serverOptions).addAll(options)
//                .set(Options.USE_DIRECT_BUFFERS, true)
//                .getMap();
//
//        UndertowXnioSsl ssl = new UndertowXnioSsl(worker.getXnio(), OptionMap.EMPTY, SSL_BUFFER_POOL, context);
//        sslServer = ssl.createSslConnectionServer(worker, new InetSocketAddress(getHostAddress("default"), port), openListener, combined);
//        sslServer.getAcceptSetter().set(openListener);
//        sslServer.resumeAccepts();
//    }


    /**
     * Stop any previously created SSL server - as this is for test clean up calling when no SSL server is running will not
     * cause an error.
     */
    public static void stopSSLServer() throws IOException {
//        if (sslServer != null) {
//            sslServer.close();
//            sslServer = null;
//        }
//        clientSslContext = null;
//        if (proxyOpenListener != null) {
//            proxyOpenListener.closeConnections();
//        } else {
//            openListener.closeConnections();
//        }
    }

    public static String getHostAddress(String serverName) {
        return System.getProperty(serverName + ".server.address", "localhost");
    }

    public static String getHostAddress() {
        return getHostAddress(DEFAULT);
    }

    public static int getHostPort(String serverName) {
        return Integer.getInteger(serverName + ".server.port", 7777);
    }

    public static int getHostPort() {
        return getHostPort(DEFAULT);
    }

    public static int getHostSSLPort(String serverName) {
        return Integer.getInteger(serverName + ".server.sslPort", 7778);
    }

    public static UndertowOptionMap getUndertowOptions() {
        return openListener.getUndertowOptions();
    }

    public static void setUndertowOptions(final UndertowOptionMap options) {
        UndertowOptionMap.Builder builder = UndertowOptionMap.builder().addAll(options);
        if (h2c) {
            builder.set(UndertowOptions.ENABLE_HTTP2, true);
        }
        openListener.setUndertowOptions(builder.getMap());
        openListener.closeConnections();
        if (proxyOpenListener != null) {
            proxyOpenListener.closeConnections();
        }
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

    private static Boolean alpnEnabled;

    private static boolean isAlpnEnabled() {
        return false;
    }

    public static void assumeAlpnEnabled() {
        Assume.assumeTrue(isAlpnEnabled());
    }
}
