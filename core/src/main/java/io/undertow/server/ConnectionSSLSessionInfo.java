package io.undertow.server;

import io.undertow.UndertowMessages;
import io.undertow.server.protocol.http.HttpServerConnection;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;
import org.xnio.channels.SslChannel;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;

/**
 * SSL session information that is read directly from the SSL session of the
 * XNIO connection
 *
 * @author Stuart Douglas
 */
public class ConnectionSSLSessionInfo implements SSLSessionInfo {

    private final SslChannel channel;
    private final HttpServerConnection serverConnection;

    public ConnectionSSLSessionInfo(SslChannel channel, HttpServerConnection serverConnection) {
        this.channel = channel;
        this.serverConnection = serverConnection;
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
    public Certificate[] getPeerCertificates(boolean forceRenegotiation) throws SSLPeerUnverifiedException {
        try {
            return channel.getSslSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            if (forceRenegotiation) {
                AbstractServerConnection.ConduitState oldState = serverConnection.resetChannel();
                try {
                    SslClientAuthMode sslClientAuthMode = channel.getOption(Options.SSL_CLIENT_AUTH_MODE);
                    if (sslClientAuthMode == SslClientAuthMode.NOT_REQUESTED) {
                        SslHandshakeWaiter waiter = new SslHandshakeWaiter();
                        channel.getHandshakeSetter().set(waiter);
                        //we use requested, to place nicely with other auth modes
                        channel.setOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED);
                        channel.getSslSession().invalidate();
                        channel.startHandshake();
                        ByteBuffer b = ByteBuffer.wrap(new byte[1]);
                        while (!waiter.isDone()) {
                            int read = serverConnection.getSourceChannel().read(b);
                            if (read != 0) {
                                throw new SSLPeerUnverifiedException("");
                            }
                            if(!waiter.isDone()) {
                                serverConnection.getSourceChannel().awaitReadable();
                            }
                        }
                        return channel.getSslSession().getPeerCertificates();
                    }
                } catch (IOException e2) {
                    throw e;
                } finally {
                    serverConnection.restoreChannel(oldState);
                }
            }
            throw e;
        }
    }

    @Override
    public X509Certificate[] getPeerCertificateChain(boolean forceRenegotiation) throws SSLPeerUnverifiedException {
        try {
            return channel.getSslSession().getPeerCertificateChain();
        } catch (SSLPeerUnverifiedException e) {
            if (forceRenegotiation) {
                AbstractServerConnection.ConduitState oldState = serverConnection.resetChannel();
                try {
                    SslClientAuthMode sslClientAuthMode = channel.getOption(Options.SSL_CLIENT_AUTH_MODE);
                    if (sslClientAuthMode == SslClientAuthMode.NOT_REQUESTED) {
                        SslHandshakeWaiter waiter = new SslHandshakeWaiter();
                        channel.getHandshakeSetter().set(waiter);
                        //we use requested, to place nicely with other auth modes
                        channel.setOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED);
                        channel.getSslSession().invalidate();
                        channel.startHandshake();
                        ByteBuffer b = ByteBuffer.wrap(new byte[1]);
                        while (!waiter.isDone()) {
                            int read = serverConnection.getSourceChannel().read(b);
                            if (read != 0) {
                                throw UndertowMessages.MESSAGES.couldNotRenegotiate();
                            }
                            if(!waiter.isDone()) {
                                serverConnection.getSourceChannel().awaitReadable();
                            }
                        }
                        return channel.getSslSession().getPeerCertificateChain();
                    }
                } catch (IOException e2) {
                    throw e;
                } finally {
                    serverConnection.restoreChannel(oldState);
                }
            }
            throw e;
        }
    }


    private static class SslHandshakeWaiter implements ChannelListener<SslChannel> {

        private volatile boolean done = false;

        boolean isDone() {
            return done;
        }

        @Override
        public void handleEvent(SslChannel channel) {
            done = true;
        }
    }

}
