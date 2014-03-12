package io.undertow.server;

import org.xnio.SslClientAuthMode;

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

}
