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

package io.undertow.servlet.handlers.security;

import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import javax.servlet.ServletRequest;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * Handler that associates SSL metadata with request
 * <p>
 * cipher suite - javax.servlet.request.cipher_suite String
 * bit size of the algorithm - javax.servlet.request.key_size Integer
 * SSL session id - javax.servlet.request.ssl_session_id String
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public class SSLInformationAssociationHandler implements HttpHandler {
    private final HttpHandler next;

    public SSLInformationAssociationHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     * Given the name of a TLS/SSL cipher suite, return an int representing it effective stream
     * cipher key strength. i.e. How much entropy material is in the key material being fed into the
     * encryption routines.
     * <p>
     * http://www.thesprawl.org/research/tls-and-ssl-cipher-suites/
     * </p>
     *
     * @param cipherSuite String name of the TLS cipher suite.
     * @return int indicating the effective key entropy bit-length.
     */
    public static int getKeyLength(String cipherSuite) {
        // Roughly ordered from most common to least common.
        if (cipherSuite == null) {
            return 0;
        } else if (cipherSuite.contains("WITH_AES_256_")) {
            return 256;
        } else if (cipherSuite.contains("WITH_RC4_128_")) {
            return 128;
        } else if (cipherSuite.contains("WITH_AES_128_")) {
            return 128;
        } else if (cipherSuite.contains("WITH_RC4_40_")) {
            return 40;
        } else if (cipherSuite.contains("WITH_3DES_EDE_CBC_")) {
            return 168;
        } else if (cipherSuite.contains("WITH_IDEA_CBC_")) {
            return 128;
        } else if (cipherSuite.contains("WITH_RC2_CBC_40_")) {
            return 40;
        } else if (cipherSuite.contains("WITH_DES40_CBC_")) {
            return 40;
        } else if (cipherSuite.contains("WITH_DES_CBC_")) {
            return 56;
        } else {
            return 0;
        }
    }


     /* ------------------------------------------------------------ */

    /**
     * Return the chain of X509 certificates used to negotiate the SSL Session.
     * <p>
     * We convert JSSE's javax.security.cert.X509Certificate[]  to servlet's  java.security.cert.X509Certificate[]
     *
     * @param session the   javax.net.ssl.SSLSession to use as the source of the cert chain.
     * @return the chain of java.security.cert.X509Certificates used to
     *         negotiate the SSL connection. <br>
     *         Will be null if the chain is missing or empty.
     */
    private X509Certificate[] getCerts(SSLSessionInfo session) {
        try {
            javax.security.cert.X509Certificate[] javaxCerts = session.getPeerCertificateChain();
            if (javaxCerts == null || javaxCerts.length == 0) {
                return null;
            }
            X509Certificate[] javaCerts = new X509Certificate[javaxCerts.length];
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            for (int i = 0; i < javaxCerts.length; i++) {
                byte[] bytes = javaxCerts[i].getEncoded();
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                javaCerts[i] = (X509Certificate) cf.generateCertificate(stream);
            }

            return javaCerts;
        } catch (Exception e) {
            return null;
        }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        ServletRequest request = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletRequest();
        SSLSessionInfo ssl = exchange.getConnection().getSslSessionInfo();
        if (ssl != null) {
            String cipherSuite = ssl.getCipherSuite();
            request.setAttribute("javax.servlet.request.cipher_suite", cipherSuite);
            request.setAttribute("javax.servlet.request.key_size", getKeyLength(cipherSuite));
            request.setAttribute("javax.servlet.request.ssl_session_id", ssl.getSessionId());
            X509Certificate[] certs = getCerts(ssl);
            if (certs != null) {
                request.setAttribute("javax.servlet.request.X509Certificate", certs);
            }

        }
        next.handleRequest(exchange);
    }

}
