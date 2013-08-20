package io.undertow.server;

import org.xnio.channels.SslChannel;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.security.cert.Certificate;

/**
 * SSL session information that is read directly from the SSL session of the
 * XNIO connection
 *
 *
 * @author Stuart Douglas
 */
public class ConnectionSSLSessionInfo implements SSLSessionInfo {

    private final SslChannel channel;

    public ConnectionSSLSessionInfo(SslChannel channel) {
        this.channel = channel;
    }

    @Override
    public byte[] getSessionId() {
        return channel.getSslSession().getId();
    }

    @Override
    public String getCipherSuite() {
        return channel.getSslSession().getCipherSuite();
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return channel.getSslSession().getPeerCertificates();
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        return channel.getSslSession().getPeerCertificateChain();
    }


}
