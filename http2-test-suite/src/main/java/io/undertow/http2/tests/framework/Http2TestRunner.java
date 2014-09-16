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

package io.undertow.http2.tests.framework;

import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * A class that starts a server before the test suite. By swapping out the root handler
 * tests can test various server functionality without continually starting and stopping the server.
 *
 * @author Stuart Douglas
 */
public class Http2TestRunner extends BlockJUnit4ClassRunner {

    private static final String SERVER_KEY_STORE = "server.keystore";
    private static final String SERVER_TRUST_STORE = "server.truststore";
    private static final String CLIENT_KEY_STORE = "client.keystore";
    private static final String CLIENT_TRUST_STORE = "client.truststore";
    private static final char[] STORE_PASSWORD = "password".toCharArray();

    public static final int BUFFER_SIZE = Integer.getInteger("test.bufferSize", 8192);

    private static XnioWorker worker;

    private static boolean first = true;
    private static SSLContext clientSslContext;
    private static Xnio xnio;
    private static XnioSsl xnioSsl;
    private static Pool<ByteBuffer> bufferPool = new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, BUFFER_SIZE, BUFFER_SIZE);

    private static ServerController serverController;

    static {
        try {
            serverController = (ServerController) Http2TestRunner.class.getClassLoader().loadClass(System.getProperty("server.controller.class", "io.undertow.http2.tests.framework.UndertowTestServer")).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Logger log = Logger.getLogger(Http2TestRunner.class);

    public Http2TestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    public static Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
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
            assertAlpnEnabled();
            first = false;
            xnio = Xnio.getInstance("nio", Http2TestRunner.class.getClassLoader());
            try {
               worker = Xnio.getInstance().createWorker(OptionMap.EMPTY);
               serverController.start(getHostAddress(), getHostPort(), getHostSSLPort());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            notifier.addListener(new RunListener() {
                @Override
                public void testRunFinished(final Result result) throws Exception {
                    worker.shutdownNow();
                    serverController.stop();
                }
            });
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

    private static SSLContext createClientSslContext() {
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

    public static String getHostAddress() {
        return System.getProperty("server.address", "localhost");
    }

    public static int getHostPort() {
        return Integer.getInteger("server.port", 7777);
    }

    public static int getHostSSLPort() {
        return Integer.getInteger("server.sslPort", 7778);
    }

    public static XnioWorker getWorker() {
        return worker;
    }

    private static void assertAlpnEnabled() {
        try {
            Class c = Class.forName("org.eclipse.jetty.alpn.ALPN");

        } catch (ClassNotFoundException e) {
            Assert.fail("Jetty ALPN was not found on the boot class path, tests cannot be run");
        }
    }


    private static KeyStore loadKeyStore(final String name) throws IOException {
        final InputStream stream = Http2TestRunner.class.getClassLoader().getResourceAsStream(name);
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

    public static XnioSsl getClientXnioSsl() {
        if(xnioSsl == null) {
            xnioSsl = new JsseXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, getClientSSLContext());
        }
        return xnioSsl;
    }
}
