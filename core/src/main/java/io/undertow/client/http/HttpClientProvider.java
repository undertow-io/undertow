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

package io.undertow.client.http;

import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientProvider;
import io.undertow.client.http2.Http2ClientProvider;
import io.undertow.client.spdy.SpdyClientProvider;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.ssl.SslConnection;
import org.xnio.ssl.XnioSsl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class HttpClientProvider implements ClientProvider {

    @Override
    public Set<String> handlesSchemes() {
        return new HashSet<>(Arrays.asList(new String[]{"http", "https"}));
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        connect(listener, null, uri, worker, ssl, bufferPool, options);
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, final XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        connect(listener, null, uri, ioThread, ssl, bufferPool, options);
    }

    @Override
    public void connect(ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, URI uri, XnioWorker worker, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options) {
        if (uri.getScheme().equals("https")) {
            if (ssl == null) {
                listener.failed(UndertowMessages.MESSAGES.sslWasNull());
                return;
            }
            if (bindAddress == null) {
                ssl.openSslConnection(worker, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()), createOpenListener(listener, bufferPool, options), options).addNotifier(createNotifier(listener), null);
            } else {
                ssl.openSslConnection(worker, bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()), createOpenListener(listener, bufferPool, options), options).addNotifier(createNotifier(listener), null);
            }
        } else {
            if (bindAddress == null) {
                worker.openStreamConnection(new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), createOpenListener(listener, bufferPool, options), options).addNotifier(createNotifier(listener), null);
            } else {
                worker.openStreamConnection(bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), createOpenListener(listener, bufferPool, options), null, options).addNotifier(createNotifier(listener), null);
            }
        }
    }

    @Override
    public void connect(ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, URI uri, XnioIoThread ioThread, XnioSsl ssl, Pool<ByteBuffer> bufferPool, OptionMap options) {
        if (uri.getScheme().equals("https")) {
            if (ssl == null) {
                listener.failed(UndertowMessages.MESSAGES.sslWasNull());
                return;
            }
            if (bindAddress == null) {
                ssl.openSslConnection(ioThread, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()), createOpenListener(listener, bufferPool, options), options).addNotifier(createNotifier(listener), null);
            } else {
                ssl.openSslConnection(ioThread, bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()), createOpenListener(listener, bufferPool, options), options).addNotifier(createNotifier(listener), null);
            }
        } else {
            if (bindAddress == null) {
                ioThread.openStreamConnection(new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), createOpenListener(listener, bufferPool, options), options).addNotifier(createNotifier(listener), null);
            } else {
                ioThread.openStreamConnection(bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 80 : uri.getPort()), createOpenListener(listener, bufferPool, options), null, options).addNotifier(createNotifier(listener), null);
            }
        }
    }

    private IoFuture.Notifier<StreamConnection, Object> createNotifier(final ClientCallback<ClientConnection> listener) {
        return new IoFuture.Notifier<StreamConnection, Object>() {
            @Override
            public void notify(IoFuture<? extends StreamConnection> ioFuture, Object o) {
                if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                    listener.failed(ioFuture.getException());
                }
            }
        };
    }

    private ChannelListener<StreamConnection> createOpenListener(final ClientCallback<ClientConnection> listener, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        return new ChannelListener<StreamConnection>() {
            @Override
            public void handleEvent(StreamConnection connection) {
                handleConnected(connection, listener, bufferPool, options);
            }
        };
    }


    private void handleConnected(final StreamConnection connection, final ClientCallback<ClientConnection> listener, final Pool<ByteBuffer> bufferPool, final OptionMap options) {
        if (options.get(UndertowOptions.ENABLE_SPDY, false) && connection instanceof SslConnection && SpdyClientProvider.isEnabled()) {
            try {
                SpdyClientProvider.handlePotentialSpdyConnection(connection, listener, bufferPool, options, new ChannelListener<SslConnection>() {
                    @Override
                    public void handleEvent(SslConnection channel) {
                        listener.completed(new HttpClientConnection(connection, options, bufferPool));
                    }
                });
            } catch (Exception e) {
                listener.failed(new IOException(e));
            }
        } else if (options.get(UndertowOptions.ENABLE_HTTP2, false) && connection instanceof SslConnection && Http2ClientProvider.isEnabled()) {
            try {
                Http2ClientProvider.handlePotentialHttp2Connection(connection, listener, bufferPool, options, new ChannelListener<SslConnection>() {
                    @Override
                    public void handleEvent(SslConnection channel) {
                        listener.completed(new HttpClientConnection(connection, options, bufferPool));
                    }
                });
            } catch (Exception e) {
                listener.failed(new IOException(e));
            }
        } else {
            listener.completed(new HttpClientConnection(connection, options, bufferPool));
        }
    }
}
