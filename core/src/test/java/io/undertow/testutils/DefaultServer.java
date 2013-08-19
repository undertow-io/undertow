/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.testutils;

import io.undertow.UndertowOptions;
import io.undertow.ajp.AjpOpenListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.OpenListener;
import io.undertow.server.handlers.RequestDumplingHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.proxy.SimpleProxyClientProvider;
import io.undertow.util.NetworkUtils;
import io.undertow.util.SingleByteStreamSinkConduit;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
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
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import static org.xnio.Options.SSL_CLIENT_AUTH_MODE;
import static org.xnio.SslClientAuthMode.REQUESTED;

/**
 * A class that starts a server before the test suite. By swapping out the root handler
 * tests can test various server functionality without continually starting and stopping the server.
 *
 * @author Stuart Douglas
 */
public class DefaultServer extends BlockJUnit4ClassRunner {

    private static final String DEFAULT = "default";
    private static final int PROXY_OFFSET = 1111;
    public static final int APACHE_PORT = 9080;
    public static final int APACHE_SSL_PORT = 9443;

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
    private static final boolean proxy = Boolean.getBoolean("test.proxy");
    private static final boolean dump = Boolean.getBoolean("test.dump");
    private static final boolean single = Boolean.getBoolean("test.single");

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
        return "https://" + getHostAddress(DEFAULT) + ":" + getHostSSLPort(DEFAULT);
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
                        .getMap();
                if (ajp) {
                    openListener = new AjpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 100 * 8192), 8192);
                    acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));
                    if (!proxy) {
                        int port = 8888;
                        server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), port), acceptListener, serverOptions);
                    } else {
                        server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), 7777 + PROXY_OFFSET), acceptListener, serverOptions);

                        proxyOpenListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 100 * 8192), OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), 8192);
                        proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                        proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                        proxyOpenListener.setRootHandler(new ProxyHandler(new SimpleProxyClientProvider(new URI("ajp", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null)), 120000));
                        proxyServer.resumeAccepts();

                    }
                } else {
                    openListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 100 * 8192), OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), 8192);
                    acceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(openListener));
                    if (!proxy) {
                        server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), acceptListener, serverOptions);
                    } else {
                        InetSocketAddress targetAddress = new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT) + PROXY_OFFSET);
                        server = worker.createStreamConnectionServer(targetAddress, acceptListener, serverOptions);

                        proxyOpenListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 100 * 8192), OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), 8192);
                        proxyAcceptListener = ChannelListeners.openListenerAdapter(wrapOpenListener(proxyOpenListener));
                        proxyServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), proxyAcceptListener, serverOptions);
                        proxyOpenListener.setRootHandler(new ProxyHandler(new SimpleProxyClientProvider(new URI("http", null, getHostAddress(DEFAULT), getHostPort(DEFAULT) + PROXY_OFFSET, "/", null, null)), 30000));
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

    private static ChannelListener<StreamConnection> wrapOpenListener(final ChannelListener<StreamConnection> listener) {
        if(!single) {
            return listener;
        }
        return new ChannelListener<StreamConnection>() {
            @Override
            public void handleEvent(StreamConnection channel) {
                channel.getSinkChannel().setConduit(new SingleByteStreamSinkConduit(channel.getSinkChannel().getConduit(), 100));
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
                return;
            }
        }
        if(proxy) {
            if(method.getAnnotation(ProxyIgnore.class) != null ||
                    method.getMethod().getDeclaringClass().isAnnotationPresent(ProxyIgnore.class)) {
                return;
            }
        }
        super.runChild(method, notifier);
    }


    /**
     * Sets the root handler for the default web server
     *
     * @param handler The handler to use
     */
    public static void setRootHandler(HttpHandler handler) {
        if (dump) {
            rootHandler.next = new RequestDumplingHandler(handler);
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
        SSLContext serverContext = createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE));
        clientSslContext = createSSLContext(loadKeyStore(CLIENT_KEY_STORE), loadKeyStore(CLIENT_TRUST_STORE));

        startSSLServer(serverContext, OptionMap.create(SSL_CLIENT_AUTH_MODE, REQUESTED));
    }

    /**
     * Start the SSL server using a custom SSLContext with additional options to pass to the JsseXnioSsl instance.
     *
     * @param context - The SSLContext to use for JsseXnioSsl initialisation.
     * @param options - Additional options to be passed to the JsseXnioSsl, this will be merged with the default options where
     *                applicable.
     */
    public static void startSSLServer(final SSLContext context, final OptionMap options) throws IOException {
        if(isApacheTest()) {
           return;
        }
        OptionMap combined = OptionMap.builder().addAll(serverOptions).addAll(options)
                .set(Options.USE_DIRECT_BUFFERS, true)
                .getMap();

        XnioSsl xnioSsl = new JsseXnioSsl(xnio, combined, context);
        sslServer = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)),
                getHostSSLPort(DEFAULT)), proxyAcceptListener != null ? proxyAcceptListener : acceptListener, combined);
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
        if(isApacheTest()) {
            return APACHE_PORT;
        }
        return Integer.getInteger(serverName + ".server.port", 7777);
    }

    public static int getHostSSLPort(String serverName) {
        if(isApacheTest()) {
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
