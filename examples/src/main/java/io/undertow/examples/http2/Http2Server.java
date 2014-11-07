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

package io.undertow.examples.http2;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.examples.UndertowExample;
import io.undertow.server.handlers.resource.FileResourceManager;
import org.xnio.IoUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;

import static io.undertow.Handlers.resource;

/**
 * @author Stuart Douglas
 */
@UndertowExample(value = "HTTP2", location = "https://localhost:8443")
public class Http2Server {

    private static final char[] STORE_PASSWORD = "password".toCharArray();

    public static void main(final String[] args) throws Exception {
        String version = System.getProperty("java.version");
        System.out.println("Java version " + version);
        if(version.charAt(0) == '1' && Integer.parseInt(version.charAt(2) + "") < 8 ) {
            System.out.println("This example requires Java 1.8 or later");
            System.out.println("The HTTP2 spec requires certain cyphers that are not present in older JVM's");
            System.out.println("See section 9.2.2 of the HTTP2 specification for details");
            System.exit(1);
        }
        Undertow server = Undertow.builder()
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setServerOption(UndertowOptions.ENABLE_SPDY, true)
                .addHttpsListener(8443, "localhost", createSSLContext(loadKeyStore("server.keystore"), loadKeyStore("server.truststore")))
                .setHandler(resource(new FileResourceManager(new File(System.getProperty("example.directory", System.getProperty("user.home"))), 100))
                        .setDirectoryListingEnabled(true))
                .build();
        server.start();
    }

    private static KeyStore loadKeyStore(String name) throws Exception {
        final InputStream stream = Http2Server.class.getResourceAsStream(name);
        try {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, STORE_PASSWORD);
            return loadedKeystore;
        } finally {
            IoUtils.safeClose(stream);
        }
    }


    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws Exception {
        KeyManager[] keyManagers;
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, STORE_PASSWORD);
        keyManagers = keyManagerFactory.getKeyManagers();

        TrustManager[] trustManagers = null;
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        trustManagers = trustManagerFactory.getTrustManagers();

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

}
