package io.undertow.server;

/**
 * SSL session information.
 *
 * @author Stuart Douglas
 */
public interface SSLSessionInfo {

    byte[] getSessionId();

    java.lang.String getCipherSuite();

    /**
     * Gets the peer certificates. This may force SSL renegotiation.
     *
     * @return The peer certificates
     * @throws javax.net.ssl.SSLPeerUnverifiedException
     */
    java.security.cert.Certificate[] getPeerCertificates(boolean forceRenegotiation) throws javax.net.ssl.SSLPeerUnverifiedException;

    javax.security.cert.X509Certificate[] getPeerCertificateChain(boolean forceRenegotiation) throws javax.net.ssl.SSLPeerUnverifiedException;

}
