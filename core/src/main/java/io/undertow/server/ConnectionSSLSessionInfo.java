package io.undertow.server;

import io.undertow.UndertowOptions;
import io.undertow.server.protocol.http.HttpServerConnection;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.Pooled;
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
    public void renegotiate(HttpServerExchange exchange, SslClientAuthMode newAuthMode) throws IOException {
        AbstractServerConnection.ConduitState oldState = serverConnection.resetChannel();
        Pooled<ByteBuffer> pooled = null;
        boolean free = true; //if the pooled buffer should be freed
        int allowedBuffers = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_BUFFERS_FOR_BUFFERED_REQUEST, 1);
        int usedBuffers = 0;
        Pooled<ByteBuffer>[] poolArray = null;
        if (allowedBuffers > 0) {
            poolArray = new Pooled[allowedBuffers];
            pooled = exchange.getConnection().getBufferPool().allocate();
            poolArray[usedBuffers++] = pooled;
        }
        boolean dataRead = false;
        try {
            SslClientAuthMode sslClientAuthMode = channel.getOption(Options.SSL_CLIENT_AUTH_MODE);
            if (sslClientAuthMode == SslClientAuthMode.NOT_REQUESTED) {
                SslHandshakeWaiter waiter = new SslHandshakeWaiter();
                channel.getHandshakeSetter().set(waiter);
                //we use requested, to place nicely with other auth modes
                channel.setOption(Options.SSL_CLIENT_AUTH_MODE, newAuthMode);
                channel.getSslSession().invalidate();
                channel.startHandshake();
                ByteBuffer buff;
                if (pooled == null) {
                    buff = ByteBuffer.wrap(new byte[1]);
                } else {
                    buff = pooled.getResource();
                }
                while (!waiter.isDone()) {
                    int read = serverConnection.getSourceChannel().read(buff);
                    if (read != 0) {
                        dataRead = true;
                        if (pooled == null) {
                            throw new SSLPeerUnverifiedException("");
                        }
                        if (!buff.hasRemaining()) {
                            if (usedBuffers == allowedBuffers) {
                                pooled = null;
                                buff = ByteBuffer.wrap(new byte[1]);
                            } else {
                                pooled = exchange.getConnection().getBufferPool().allocate();
                                poolArray[usedBuffers++] = pooled;
                                buff = pooled.getResource();
                            }
                        }
                    }
                    if (!waiter.isDone()) {
                        serverConnection.getSourceChannel().awaitReadable();
                    }
                }
                if(dataRead) {
                    free = false;
                    Connectors.ungetRequestBytes(exchange, poolArray);
                }
            }
        } finally {
            if (free && pooled != null) {
                pooled.free();
            }
            serverConnection.restoreChannel(oldState);
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
