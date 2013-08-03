package io.undertow.ajp;

import io.undertow.server.SSLSessionInfo;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;

/**
 * @author Stuart Douglas
 */
public class AjpSSLSessionInfo implements SSLSessionInfo {

    private final byte[] id;
    private final String cypherSuite;
    private final java.security.cert.Certificate peerCertificate;
    private final X509Certificate certificate;

    public AjpSSLSessionInfo(byte[] id, String cypherSuite, byte[] certificate) throws java.security.cert.CertificateException, CertificateException {
        this.id = id;
        this.cypherSuite = cypherSuite;

        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
        ByteArrayInputStream stream = new ByteArrayInputStream(certificate);
        peerCertificate = cf.generateCertificate(stream);
        this.certificate = X509Certificate.getInstance(certificate);
    }

    @Override
    public byte[] getId() {
        return id;
    }

    @Override
    public String getCipherSuite() {
        return cypherSuite;
    }

    @Override
    public java.security.cert.Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return new Certificate[]{peerCertificate};
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        return new X509Certificate[]{certificate};
    }
}
