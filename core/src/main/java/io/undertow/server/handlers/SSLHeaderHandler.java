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

package io.undertow.server.handlers;

import static io.undertow.util.Headers.SSL_CIPHER;
import static io.undertow.util.Headers.SSL_CIPHER_USEKEYSIZE;
import static io.undertow.util.Headers.SSL_CLIENT_CERT;
import static io.undertow.util.Headers.SSL_SESSION_ID;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.UndertowLogger;
import io.undertow.server.BasicSSLSessionInfo;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.Certificates;
import io.undertow.util.HeaderMap;

/**
 * Handler that sets SSL information on the connection based on the following headers:
 * <p>
 * <ul>
 * <li>SSL_CLIENT_CERT</li>
 * <li>SSL_CIPHER</li>
 * <li>SSL_SESSION_ID</li>
 * </ul>
 * <p>
 * If this handler is present in the chain it will always override the SSL session information,
 * even if these headers are not present.
 * <p>
 * This handler MUST only be used on servers that are behind a reverse proxy, where the reverse proxy
 * has been configured to always set these header for EVERY request (or strip existing headers with these
 * names if no SSL information is present). Otherwise it may be possible for a malicious client to spoof
 * a SSL connection.
 *
 * @author Stuart Douglas
 */
public class SSLHeaderHandler implements HttpHandler {

    public static final String HTTPS = "https";
    private static final String NULL_VALUE = "(null)";

    private static final ExchangeCompletionListener CLEAR_SSL_LISTENER = new ExchangeCompletionListener() {
        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            exchange.getConnection().setSslSessionInfo(null);
            nextListener.proceed();
        }
    };

    private final HttpHandler next;

    public SSLHeaderHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        HeaderMap requestHeaders = exchange.getRequestHeaders();
        final String sessionId = requestHeaders.getFirst(SSL_SESSION_ID);
        final String cipher = requestHeaders.getFirst(SSL_CIPHER);
        String clientCert = requestHeaders.getFirst(SSL_CLIENT_CERT);
        String keySizeStr = requestHeaders.getFirst(SSL_CIPHER_USEKEYSIZE);
        Integer keySize = null;
        if (keySizeStr != null) {
            try {
                keySize = Integer.parseUnsignedInt(keySizeStr);
            } catch (NumberFormatException e) {
                UndertowLogger.REQUEST_LOGGER.debugf("Invalid SSL_CIPHER_USEKEYSIZE header %s", keySizeStr);
            }
        }
        if (clientCert != null || sessionId != null || cipher != null) {
            if (clientCert != null) {
                if (clientCert.isEmpty() || clientCert.equals(NULL_VALUE)) {
                    // SSL is in place but client cert was not sent
                    clientCert = null;
                } else if (clientCert.length() > 28 + 26) {
                    // the proxy client replaces \n with ' '
                    StringBuilder sb = new StringBuilder(clientCert.length() + 1);
                    sb.append(Certificates.BEGIN_CERT);
                    sb.append('\n');
                    sb.append(clientCert.replace(' ', '\n').substring(28, clientCert.length() - 26));//core certificate data
                    sb.append('\n');
                    sb.append(Certificates.END_CERT);
                    clientCert = sb.toString();
                }
            }
            try {
                SSLSessionInfo info = new BasicSSLSessionInfo(sessionId, cipher, clientCert, keySize);
                exchange.setRequestScheme(HTTPS);
                exchange.getConnection().setSslSessionInfo(info);
                exchange.addExchangeCompleteListener(CLEAR_SSL_LISTENER);
            } catch (java.security.cert.CertificateException e) {
                UndertowLogger.REQUEST_LOGGER.debugf(e, "Could not create certificate from header %s", clientCert);
            }
        }
        next.handleRequest(exchange);
    }

    @Override
    public String toString() {
        return "ssl-headers()";
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "ssl-headers";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper();
        }

    }

    private static class Wrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new SSLHeaderHandler(handler);
        }
    }
}
