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
     *
     * @return The SSL session ID, or null if this could not be determined.
     */
    byte[] getSessionId();

    java.lang.String getCipherSuite();

    /**
     * Gets the peer certificates. This may force SSL renegotiation.
     *
     * @return The peer certificates
     * @throws javax.net.ssl.SSLPeerUnverifiedException
     * @throws RenegotiationRequiredException If the session
     */
    java.security.cert.Certificate[] getPeerCertificates() throws javax.net.ssl.SSLPeerUnverifiedException, RenegotiationRequiredException;

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
