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

package io.undertow.server;

import io.undertow.UndertowMessages;
import io.undertow.util.HexConverter;
import org.xnio.SslClientAuthMode;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Basic SSL session information. This information is generally provided by a front end proxy.
 *
 * @author Stuart Douglas
 */
public class BasicSSLSessionInfo implements SSLSessionInfo {

    private final byte[] sessionId;
    private final String cypherSuite;
    private final java.security.cert.Certificate[] peerCertificate;
    private final X509Certificate[] certificate;
    private final Integer keySize;

    /**
     *
     * @param sessionId The SSL session ID
     * @param cypherSuite The cypher suite name
     * @param certificate A string representation of the client certificate
     * @param keySize The key-size used by the cypher
     * @throws java.security.cert.CertificateException If the client cert could not be decoded
     * @throws CertificateException If the client cert could not be decoded
     */
    public BasicSSLSessionInfo(byte[] sessionId, String cypherSuite, String certificate, Integer keySize) throws java.security.cert.CertificateException, CertificateException {
        this.sessionId = sessionId;
        this.cypherSuite = cypherSuite;
        this.keySize = keySize;
        if (certificate != null) {
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            byte[] certificateBytes = certificate.getBytes(StandardCharsets.US_ASCII);
            ByteArrayInputStream stream = new ByteArrayInputStream(certificateBytes);
            Collection<? extends java.security.cert.Certificate> certCol = cf.generateCertificates(stream);
            this.peerCertificate = new java.security.cert.Certificate[certCol.size()];
            X509Certificate[] legacyCertificate = new X509Certificate[certCol.size()];
            int i=0;
            for(java.security.cert.Certificate cert : certCol) {
                this.peerCertificate[i] = cert;
                if (legacyCertificate != null) {
                    try {
                        legacyCertificate[i] = X509Certificate.getInstance(cert.getEncoded());
                    } catch (CertificateException ce) {
                        // [UNDERTOW-1969] We don't care about deprecated JDK methods failure caused by the fact newer JDKs
                        // doesn't support them anymore. "this.certificate" is used only by deprecated method
                        // {@link SSLSessionInfo.getPeerCertificateChain()} which call should be avoided by API users.
                        legacyCertificate = null;
                    }
                }
                i++;
            }
            this.certificate = legacyCertificate;
        } else {
            this.peerCertificate = null;
            this.certificate = null;
        }
    }

    /**
     *
     * @param sessionId The SSL session ID
     * @param cypherSuite The cypher suite name
     * @param certificate A string representation of the client certificate
     * @throws java.security.cert.CertificateException If the client cert could not be decoded
     * @throws CertificateException If the client cert could not be decoded
     */
    public BasicSSLSessionInfo(byte[] sessionId, String cypherSuite, String certificate) throws java.security.cert.CertificateException, CertificateException {
        this(sessionId, cypherSuite, certificate, null);
    }

    /**
     *
     * @param sessionId The encoded SSL session ID
     * @param cypherSuite The cypher suite name
     * @param certificate A string representation of the client certificate
     * @throws java.security.cert.CertificateException If the client cert could not be decoded
     * @throws CertificateException If the client cert could not be decoded
     */
    public BasicSSLSessionInfo(String sessionId, String cypherSuite, String certificate) throws java.security.cert.CertificateException, CertificateException {
        this(sessionId == null ? null : fromHex(sessionId), cypherSuite, certificate, null);
    }

    /**
     *
     * @param sessionId The encoded SSL session ID
     * @param cypherSuite The cypher suite name
     * @param certificate A string representation of the client certificate
     * @param keySize The key-size used by the cypher
     * @throws java.security.cert.CertificateException If the client cert could not be decoded
     * @throws CertificateException If the client cert could not be decoded
     */
    public BasicSSLSessionInfo(String sessionId, String cypherSuite, String certificate, Integer keySize) throws java.security.cert.CertificateException, CertificateException {
        this(sessionId == null ? null : fromHex(sessionId), cypherSuite, certificate, keySize);
    }

    @Override
    public byte[] getSessionId() {
        if(sessionId == null) {
            return null;
        }
        final byte[] copy = new byte[sessionId.length];
        System.arraycopy(sessionId, 0, copy, 0, copy.length);
        return copy;
    }

    @Override
    public String getCipherSuite() {
        return cypherSuite;
    }

    @Override
    public int getKeySize() {
        if (keySize != null) {
            return keySize;
        } else {
            return SSLSessionInfo.super.getKeySize();
        }
    }

    @Override
    public java.security.cert.Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        if (peerCertificate == null) {
            throw UndertowMessages.MESSAGES.peerUnverified();
        }
        return peerCertificate;
    }

    @Deprecated
    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        if (certificate == null) {
            throw UndertowMessages.MESSAGES.peerUnverified();
        }
        return certificate;
    }

    @Override
    public void renegotiate(HttpServerExchange exchange, SslClientAuthMode sslClientAuthMode) throws IOException {
        throw UndertowMessages.MESSAGES.renegotiationNotSupported();
    }

    @Override
    public SSLSession getSSLSession() {
        return null;
    }

    private static byte[] fromHex(String sessionId) {
        try {
            return HexConverter.convertFromHex(sessionId);
        } catch (Exception e) {
            //can happen if the session id is invalid
            return null;
        }
    }
}
