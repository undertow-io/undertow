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

import org.xnio.SslClientAuthMode;

import javax.net.ssl.SSLSession;
import java.io.IOException;

/**
 * SSL session information.
 *
 * @author Stuart Douglas
 */
public interface SSLSessionInfo {

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
    static int calculateKeySize(String cipherSuite) {
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

    /**
     *
     * @return The SSL session ID, or null if this could not be determined.
     */
    byte[] getSessionId();

    java.lang.String getCipherSuite();

    default int getKeySize() {
        return calculateKeySize(this.getCipherSuite());
    }

    /**
     * Gets the peer certificates. This may force SSL renegotiation.
     *
     * @return The peer certificates
     * @throws javax.net.ssl.SSLPeerUnverifiedException
     * @throws RenegotiationRequiredException If the session
     */
    java.security.cert.Certificate[] getPeerCertificates() throws javax.net.ssl.SSLPeerUnverifiedException, RenegotiationRequiredException;

    /**
     * This method is no longer supported on java 15 and should be avoided.
     * @deprecated in favor of {@link #getPeerCertificates()} because {@link SSLSession#getPeerCertificateChain()}
     *             throws java 15.
     * @see SSLSession#getPeerCertificateChain()
     */
    @Deprecated(since="2.0.33", forRemoval=true)
    javax.security.cert.X509Certificate[] getPeerCertificateChain() throws javax.net.ssl.SSLPeerUnverifiedException, RenegotiationRequiredException;

    /**
     * Renegotiate in a blocking manner. This will set the client aut
     *
     * TODO: we also need a non-blocking version
     *
     * @throws IOException
     * @param exchange The exchange
     * @param sslClientAuthMode The client cert mode to use when renegotiating
     */
    void renegotiate(HttpServerExchange exchange, SslClientAuthMode sslClientAuthMode) throws IOException;

    /**
     *
     * @return The SSL session, or null if it is not applicable
     */
    SSLSession getSSLSession();

}
