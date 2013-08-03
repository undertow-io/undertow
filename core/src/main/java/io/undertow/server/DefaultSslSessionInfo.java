package io.undertow.server;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.security.cert.Certificate;

/**
 * SSL session information that is read directly from the SSL session
 *
 *
 * @author Stuart Douglas
 */
public class DefaultSslSessionInfo implements SSLSessionInfo {

    private final SSLSession session;

    public DefaultSslSessionInfo(SSLSession session) {
        this.session = session;
    }

    @Override
    public byte[] getId() {
        return session.getId();
    }

    @Override
    public String getCipherSuite() {
        return session.getCipherSuite();
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return session.getPeerCertificates();
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        return session.getPeerCertificateChain();
    }
}
