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

package io.undertow.protocols.ssl;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Options;
import io.undertow.connector.ByteBufferPool;
import org.xnio.SslClientAuthMode;
import org.xnio.StreamConnection;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
class UndertowSslConnection extends SslConnection {

    private static final Set<Option<?>> SUPPORTED_OPTIONS = Option.setBuilder().add(Options.SECURE, Options.SSL_CLIENT_AUTH_MODE).create();

    private final StreamConnection delegate;
    private final SslConduit sslConduit;
    private final ChannelListener.SimpleSetter<SslConnection> handshakeSetter = new ChannelListener.SimpleSetter<>();
    private final SSLEngine engine;

    /**
     * Construct a new instance.
     *
     * @param delegate the underlying connection
     */
    UndertowSslConnection(StreamConnection delegate, SSLEngine engine, ByteBufferPool bufferPool) {
        super(delegate.getIoThread());
        this.delegate = delegate;
        this.engine = engine;
        sslConduit = new SslConduit(this, delegate, engine, bufferPool, new HandshakeCallback());
        setSourceConduit(sslConduit);
        setSinkConduit(sslConduit);
    }

    @Override
    public void startHandshake() throws IOException {
        sslConduit.startHandshake();
    }

    @Override
    public SSLSession getSslSession() {
        return sslConduit.getSslSession();
    }

    @Override
    public ChannelListener.Setter<? extends SslConnection> getHandshakeSetter() {
        return handshakeSetter;
    }

    @Override
    protected void notifyWriteClosed() {
        sslConduit.notifyWriteClosed();
    }

    @Override
    protected void notifyReadClosed() {
        sslConduit.notifyReadClosed();
    }

    @Override
    public SocketAddress getPeerAddress() {
        return delegate.getPeerAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }

    public SSLEngine getSSLEngine() {
        return sslConduit.getSSLEngine();
    }

    SslConduit getSslConduit() {
        return sslConduit;
    }

    /** {@inheritDoc} */
    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            try {
                return option.cast(engine.getNeedClientAuth() ? SslClientAuthMode.REQUIRED : engine.getWantClientAuth() ? SslClientAuthMode.REQUESTED : SslClientAuthMode.NOT_REQUESTED);
            } finally {
                engine.setNeedClientAuth(value == SslClientAuthMode.REQUIRED);
                engine.setWantClientAuth(value == SslClientAuthMode.REQUESTED);
            }
        } else if (option == Options.SECURE) {
            throw new IllegalArgumentException();
        } else {
            return delegate.setOption(option, value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        if (option == Options.SSL_CLIENT_AUTH_MODE) {
            return option.cast(engine.getNeedClientAuth() ? SslClientAuthMode.REQUIRED : engine.getWantClientAuth() ? SslClientAuthMode.REQUESTED : SslClientAuthMode.NOT_REQUESTED);
        } else {
            return option == Options.SECURE ? (T)Boolean.TRUE : delegate.getOption(option);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsOption(final Option<?> option) {
        return SUPPORTED_OPTIONS.contains(option) || delegate.supportsOption(option);
    }

    @Override
    protected boolean readClosed() {
        return super.readClosed();
    }

    @Override
    protected boolean writeClosed() {
        return super.writeClosed();
    }

    protected void closeAction() {
        sslConduit.close();
    }

    private final class HandshakeCallback implements Runnable {

        @Override
        public void run() {
            final ChannelListener<? super SslConnection> listener = handshakeSetter.get();
            if (listener == null) {
                return;
            }
            ChannelListeners.<SslConnection>invokeChannelListener(UndertowSslConnection.this, listener);
        }
    }
}
