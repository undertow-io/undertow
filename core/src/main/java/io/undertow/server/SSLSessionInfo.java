package io.undertow.server;

/**
 * SSL session information.
 *
 * @author Stuart Douglas
 */
public interface SSLSessionInfo {

    byte[] getSessionId();

    java.lang.String getCipherSuite();

    java.security.cert.Certificate[] getPeerCertificates() throws javax.net.ssl.SSLPeerUnverifiedException;

    javax.security.cert.X509Certificate[] getPeerCertificateChain() throws javax.net.ssl.SSLPeerUnverifiedException;

}
