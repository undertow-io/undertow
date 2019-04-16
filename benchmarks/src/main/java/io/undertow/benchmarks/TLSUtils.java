/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package io.undertow.benchmarks;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Helper utility to create {@link SSLContext} instances.
 *
 * @author Carter Kozak
 */
final class TLSUtils {

    private static final String SERVER_KEY_STORE = "server.keystore";
    private static final String SERVER_TRUST_STORE = "server.truststore";
    private static final String CLIENT_KEY_STORE = "client.keystore";
    private static final String CLIENT_TRUST_STORE = "client.truststore";
    private static final char[] STORE_PASSWORD = "password".toCharArray();

    static SSLContext newServerContext() {
        try {
            KeyStore keyStore = loadKeyStore(SERVER_KEY_STORE);
            KeyStore trustStore = loadKeyStore(SERVER_TRUST_STORE);
            return createSSLContext(keyStore, trustStore);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create server SSLContext", e);
        }
    }

    static SSLContext newClientContext() {
        try {
            KeyStore keyStore = loadKeyStore(CLIENT_KEY_STORE);
            KeyStore trustStore = loadKeyStore(CLIENT_TRUST_STORE);
            return createSSLContext(keyStore, trustStore);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create client SSLContext", e);
        }
    }


    private static KeyStore loadKeyStore(final String name) throws IOException {
        try (InputStream stream = TLSUtils.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) {
                throw new RuntimeException("Could not load keystore");
            }
            try {
                KeyStore loadedKeystore = KeyStore.getInstance("JKS");
                loadedKeystore.load(stream, STORE_PASSWORD);
                return loadedKeystore;
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
                throw new IOException("Unable to load KeyStore " + name, e);
            }
        }
    }

    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws IOException {
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, STORE_PASSWORD);
            keyManagers = keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new IOException("Unable to initialise KeyManager[]", e);
        }

        TrustManager[] trustManagers;
        try {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new IOException("Unable to initialise TrustManager[]", e);
        }

        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("Unable to create and initialise the SSLContext", e);
        }
    }

    private TLSUtils() {}
}
