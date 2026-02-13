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

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.nio.charset.Charset;

/**
 * @author Stuart Douglas
 */
public class TestHttpClient {

    private static final HostnameVerifier NO_OP_VERIFIER = (s, sslSession) -> true;

    public static CloseableHttpClient defaultClient() {
        return custom().build();
    }

    public static HttpClientBuilder withSSLContext(SSLContext sslContext) {

        ClientTlsStrategyBuilder clientTlsStrategyBuilder = ClientTlsStrategyBuilder.create().setSslContext(sslContext);

        if (!DefaultServer.getHostAddress(DefaultServer.DEFAULT).equals("localhost")) {
            clientTlsStrategyBuilder.setHostnameVerifier(NO_OP_VERIFIER);
        }

        return custom().setConnectionManager(
                connectionManager()
                        .setTlsSocketStrategy(clientTlsStrategyBuilder.buildClassic())
                        .build());
    }

    public static HttpClientBuilder withEncoding(Charset charset) {
        return custom().setConnectionManager(
                connectionManager()
                        .setConnectionFactory(ManagedHttpClientConnectionFactory.builder()
                                .charCodingConfig(CharCodingConfig.custom()
                                        .setCharset(charset)
                                        .build())
                                .build())
                        .build());
    }

    public static HttpClientBuilder custom() {
        return HttpClients.custom()
                .setConnectionManager(connectionManager().build())
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(0, Timeout.ofSeconds(1)));
    }

    private static PoolingHttpClientConnectionManagerBuilder connectionManager() {
        // UNDERTOW-1929 prevent the SocketTimeoutException that we see recurring
        // in CI when running tests on proxy mode
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                        .buildClassic())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofMinutes(5))
                        .build());
    }
}
