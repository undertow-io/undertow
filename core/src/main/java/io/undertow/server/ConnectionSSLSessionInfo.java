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

import io.undertow.UndertowOptions;
import io.undertow.server.protocol.http.HttpServerConnection;
import org.xnio.ChannelListener;
import org.xnio.Options;
import org.xnio.Pooled;
import org.xnio.SslClientAuthMode;
import org.xnio.channels.Channels;
import org.xnio.channels.SslChannel;
import org.xnio.channels.StreamSourceChannel;

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
    public void renegotiate(HttpServerExchange exchange, SslClientAuthMode sslClientAuthMode) throws IOException {
        if (exchange.isRequestComplete()) {
            renegotiateNoRequest(exchange, sslClientAuthMode);
        } else {
            renegotiateBufferRequest(exchange, sslClientAuthMode);
        }
    }

    public void renegotiateBufferRequest(HttpServerExchange exchange, SslClientAuthMode newAuthMode) throws IOException {
        int maxSize = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, 16384);
        if (maxSize <= 0) {
            throw new SSLPeerUnverifiedException("");
        }

        //first we need to read the request
        boolean requestResetRequired = false;
        StreamSourceChannel requestChannel = Connectors.getExistingRequestChannel(exchange);
        if (requestChannel == null) {
            requestChannel = exchange.getRequestChannel();
            requestResetRequired = true;
        }

        Pooled<ByteBuffer> pooled = exchange.getConnection().getBufferPool().allocate();
        boolean free = true; //if the pooled buffer should be freed
        int usedBuffers = 0;
        Pooled<ByteBuffer>[] poolArray = null;
        final int bufferSize = pooled.getResource().remaining();
        int allowedBuffers = ((maxSize + bufferSize - 1) / bufferSize);
        poolArray = new Pooled[allowedBuffers];
        poolArray[usedBuffers++] = pooled;
        try {
            int res;
            do {
                final ByteBuffer buf = pooled.getResource();
                res = Channels.readBlocking(requestChannel, buf);
                if (!buf.hasRemaining()) {
                    if (usedBuffers == allowedBuffers) {
                        throw new SSLPeerUnverifiedException("");
                    } else {
                        buf.flip();
                        pooled = exchange.getConnection().getBufferPool().allocate();
                        poolArray[usedBuffers++] = pooled;
                    }
                }
            } while (res != -1);
            free = false;
            pooled.getResource().flip();
            Connectors.ungetRequestBytes(exchange, poolArray);
            renegotiateNoRequest(exchange, newAuthMode);
        } finally {
            if (free) {
                for(Pooled<ByteBuffer> buf : poolArray) {
                    if(buf != null) {
                        buf.free();
                    }
                }
            }
            if(requestResetRequired) {
                exchange.requestChannel = null;
            }
        }
    }

    public void renegotiateNoRequest(HttpServerExchange exchange, SslClientAuthMode newAuthMode) throws IOException {
        AbstractServerConnection.ConduitState oldState = serverConnection.resetChannel();
        try {
            SslClientAuthMode sslClientAuthMode = channel.getOption(Options.SSL_CLIENT_AUTH_MODE);
            if (sslClientAuthMode == SslClientAuthMode.NOT_REQUESTED) {
                SslHandshakeWaiter waiter = new SslHandshakeWaiter();
                channel.getHandshakeSetter().set(waiter);
                //we use requested, to place nicely with other auth modes
                channel.setOption(Options.SSL_CLIENT_AUTH_MODE, newAuthMode);
                channel.getSslSession().invalidate();
                channel.startHandshake();
                ByteBuffer buff = ByteBuffer.wrap(new byte[1]);
                while (!waiter.isDone() && serverConnection.isOpen()) {
                    int read = serverConnection.getSourceChannel().read(buff);
                    if (read != 0) {
                        throw new SSLPeerUnverifiedException("");
                    }
                    if (!waiter.isDone()) {
                        serverConnection.getSourceChannel().awaitReadable();
                    }
                }
            }
        } finally {
            if (oldState != null) {
                serverConnection.restoreChannel(oldState);
            }
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
