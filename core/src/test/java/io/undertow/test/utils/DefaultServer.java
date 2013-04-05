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

package io.undertow.test.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.undertow.UndertowOptions;
import io.undertow.ajp.AjpOpenListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
import io.undertow.server.OpenListener;
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

    private static boolean first = true;
    private static OptionMap serverOptions;
    private static OpenListener openListener;
    private static ChannelListener acceptListener;
    private static XnioWorker worker;
    private static AcceptingChannel<? extends StreamConnection> server;
    private static AcceptingChannel<? extends StreamConnection> sslServer;
    private static SSLContext clientSslContext;
    private static Xnio xnio;

    private static final String SERVER_KEY_STORE = "server.keystore";
    private static final String SERVER_TRUST_STORE = "server.truststore";
    private static final String CLIENT_KEY_STORE = "client.keystore";
    private static final String CLIENT_TRUST_STORE = "client.truststore";
    private static final char[] STORE_PASSWORD = "password".toCharArray();

    private static final boolean ajp = Boolean.getBoolean("ajp");

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
        return "http://" + getHostAddress(DEFAULT) + ":" + getHostPort(DEFAULT);
    }

    public static InetSocketAddress getDefaultServerAddress() {
        return new InetSocketAddress(DefaultServer.getHostAddress("default"), DefaultServer.getHostPort("default"));
    }

    public static String getDefaultServerSSLAddress() {
        if (sslServer == null) {
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
                if(ajp) {
                    openListener = new AjpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 100 * 8192), 8192);
                    acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), 7777), acceptListener, serverOptions);
                } else {
                    openListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 100 * 8192), OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), 8192);
                    acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)), getHostPort(DEFAULT)), acceptListener, serverOptions);
                }
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
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        if(ajp && (method.getAnnotation(AjpIgnore.class) != null || method.getMethod().getDeclaringClass().isAnnotationPresent(AjpIgnore.class))) {
            return;
        } else {
            super.runChild(method, notifier);
        }
    }


    /**
     * Sets the root handler for the default web server
     *
     * @param rootHandler The handler to use
     */
    public static void setRootHandler(HttpHandler rootHandler) {
        if(ajp) {
            openListener.setRootHandler(rootHandler);
        } else {
            openListener.setRootHandler(rootHandler);
        }
    }

    /**
     * When using the default SSL settings returns the corresponding client context.
     *
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
     *
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
     *        applicable.
     */
    public static void startSSLServer(final SSLContext context, final OptionMap options) throws IOException {
        OptionMap combined = OptionMap.builder().addAll(serverOptions).addAll(options).getMap();

        XnioSsl xnioSsl = new JsseXnioSsl(xnio, combined, context);
        sslServer = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(Inet4Address.getByName(getHostAddress(DEFAULT)),
                getHostSSLPort(DEFAULT)), acceptListener, combined);
        sslServer.resumeAccepts();
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
        return Integer.getInteger(serverName + ".server.port", 7777)  + (ajp ? 1111 : 0);
    }

    public static int getHostSSLPort(String serverName) {
        return Integer.getInteger(serverName + ".server.sslPort", 7778);
    }

    public static OptionMap getUndertowOptions() {
        return openListener.getUndertowOptions();
    }

    public static void setUndertowOptions(final OptionMap options) {
        openListener.setUndertowOptions(options);
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
}
