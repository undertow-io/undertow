/*
 * JBoss, Home of Professional Open Source. Copyright 2014 Red Hat, Inc., and individual contributors as indicated by
 * the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.undertow.examples.ssl;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.xnio.Options;
import org.xnio.Sequence;

import com.google.common.io.Resources;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.examples.UndertowExample;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
@UndertowExample("Hello World SSL")
public class HelloWorldSSL {

    public static void main(final String[] args) throws Exception {
        final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(getKeyManagers(System.getProperty("keyStorePath")),
                        getTrustManagers(System.getProperty("trustStorePath")), null);

        final List<String> cipherSuites = new ArrayList<>();
        cipherSuites.add("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        cipherSuites.add("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
        cipherSuites.add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256");
        cipherSuites.add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384");
        cipherSuites.add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA");
        cipherSuites.add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA");
        cipherSuites.add("TLS_RSA_WITH_AES_128_GCM_SHA256");
        cipherSuites.add("TLS_RSA_WITH_AES_256_GCM_SHA384");
        cipherSuites.add("TLS_RSA_WITH_AES_128_CBC_SHA256");
        cipherSuites.add("TLS_RSA_WITH_AES_256_CBC_SHA256");
        cipherSuites.add("TLS_RSA_WITH_AES_128_CBC_SHA");
        cipherSuites.add("TLS_RSA_WITH_AES_256_CBC_SHA");

        Undertow server = Undertow.builder().addHttpsListener(8443, "0.0.0.0", sslContext)
                        .setServerOption(UndertowOptions.SSL_USER_CIPHER_SUITES_ORDER, true)
                        .setServerOption(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(cipherSuites))
                        .setHandler(new HttpHandler() {
                            @Override
                            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                                exchange.getResponseSender().send("Hello World");
                            }
                        }).build();
        server.start();
    }

    /**
     * This could have been done through: -Djavax.net.ssl.trustStore=./truststore.jks
     * -Djavax.net.ssl.trustStorePassword=secret
     *
     * @param trustStorePath trust store path value
     * @return trust managers
     * @throws Exception
     */
    private static TrustManager[] getTrustManagers(final String trustStorePath) throws Exception {
        final KeyStore keystore = getKeyStore(trustStorePath);
        final TrustManagerFactory trustManagerFactory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        trustManagerFactory.init(keystore);
        return trustManagerFactory.getTrustManagers();

    }

    private static KeyStore getKeyStore(final String jksFilePath) throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("JKS");
        /// CLOVER:OFF
        if (Paths.get(jksFilePath).isAbsolute()) {
            // Can not cover this branch in unit test. Can not refer any files by absolute paths
            try (InputStream jksFileInputStream = new FileInputStream(jksFilePath)) {
                keyStore.load(jksFileInputStream, "abcdefghi".toCharArray());
                return keyStore;
            }
        }
        /// CLOVER:ON

        try (InputStream jksFileInputStream = Resources.getResource(jksFilePath).openStream()) {
            keyStore.load(jksFileInputStream, "abcdefghi".toCharArray());
            return keyStore;
        }
    }

    private static KeyManager[] getKeyManagers(final String sslKeyStore) throws Exception {
        final KeyStore keystore = getKeyStore(sslKeyStore);
        final KeyManagerFactory keyManagerFactory =
                        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keystore, "abcdefghi".toCharArray());
        return keyManagerFactory.getKeyManagers();
    }
}
