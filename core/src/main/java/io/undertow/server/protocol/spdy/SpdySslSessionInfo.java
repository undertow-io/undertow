package io.undertow.server.protocol.spdy;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RenegotiationRequiredException;
import io.undertow.server.SSLSessionInfo;
import io.undertow.spdy.SpdyChannel;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.security.cert.Certificate;

/**
 * @author Stuart Douglas
 */
class SpdySslSessionInfo implements SSLSessionInfo {

    private final SpdyChannel channel;

    public SpdySslSessionInfo(SpdyChannel channel) {
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
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException, RenegotiationRequiredException {
        try {
            return channel.getSslSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            try {
                SslClientAuthMode sslClientAuthMode = channel.getOption(Options.SSL_CLIENT_AUTH_MODE);
                if (sslClientAuthMode == SslClientAuthMode.NOT_REQUESTED) {
                    throw new RenegotiationRequiredException();
                }
            } catch (IOException e1) {
                //ignore, will not actually happen
            }
            throw e;
        }
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException, RenegotiationRequiredException {
        try {
            return channel.getSslSession().getPeerCertificateChain();
        } catch (SSLPeerUnverifiedException e) {
            try {
                SslClientAuthMode sslClientAuthMode = channel.getOption(Options.SSL_CLIENT_AUTH_MODE);
                if (sslClientAuthMode == SslClientAuthMode.NOT_REQUESTED) {
                    throw new RenegotiationRequiredException();
                }
            } catch (IOException e1) {
                //ignore, will not actually happen
            }
            throw e;
        }
    }
    @Override
    public void renegotiate(HttpServerExchange exchange, SslClientAuthMode sslClientAuthMode) throws IOException {
        throw UndertowMessages.MESSAGES.renegotiationNotSupported();
    }
}
