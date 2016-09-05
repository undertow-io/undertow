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

package io.undertow.client.http2;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.client.ALPNClientSelector;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientProvider;
import io.undertow.client.ClientStatistics;
import io.undertow.conduits.ByteActivityCallback;
import io.undertow.conduits.BytesReceivedStreamSourceConduit;
import io.undertow.conduits.BytesSentStreamSinkConduit;
import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.http2.Http2Channel;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.ssl.SslConnection;
import org.xnio.ssl.XnioSsl;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Plaintext HTTP2 client provider that works using HTTP upgrade
 *
 * @author Stuart Douglas
 */
public class Http2ClientProvider implements ClientProvider {

    private static final String HTTP2 = "h2";
    private static final String HTTP_1_1 = "http/1.1";

    private static final ChannelListener<SslConnection> FAILED = new ChannelListener<SslConnection>() {
        @Override
        public void handleEvent(SslConnection connection) {
            UndertowLogger.ROOT_LOGGER.alpnConnectionFailed(connection);
            IoUtils.safeClose(connection);
        }
    };

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioWorker worker, final XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap options) {
        connect(listener, null, uri, worker, ssl, bufferPool, options);
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final URI uri, final XnioIoThread ioThread, final XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap options) {
        connect(listener, null, uri, ioThread, ssl, bufferPool, options);
    }

    @Override
    public Set<String> handlesSchemes() {
        return new HashSet<>(Arrays.asList(new String[]{"h2"}));
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, final URI uri, final XnioWorker worker, final XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap options) {
        if (ssl == null) {
            listener.failed(UndertowMessages.MESSAGES.sslWasNull());
            return;
        }
        OptionMap tlsOptions = OptionMap.builder().addAll(options).set(Options.SSL_STARTTLS, true).getMap();
        if(bindAddress == null) {
            ssl.openSslConnection(worker, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()), createOpenListener(listener, uri, ssl, bufferPool, tlsOptions), tlsOptions).addNotifier(createNotifier(listener), null);
        } else {
            ssl.openSslConnection(worker, bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()), createOpenListener(listener, uri, ssl, bufferPool, tlsOptions), tlsOptions).addNotifier(createNotifier(listener), null);
        }

    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, final URI uri, final XnioIoThread ioThread, final XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap options) {
        if (ssl == null) {
            listener.failed(UndertowMessages.MESSAGES.sslWasNull());
            return;
        }
        if(bindAddress == null) {
            OptionMap tlsOptions = OptionMap.builder().addAll(options).set(Options.SSL_STARTTLS, true).getMap();
            ssl.openSslConnection(ioThread, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()), createOpenListener(listener, uri, ssl, bufferPool, tlsOptions), options).addNotifier(createNotifier(listener), null);
        } else {
            ssl.openSslConnection(ioThread, bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 443 : uri.getPort()), createOpenListener(listener, uri, ssl, bufferPool, options), options).addNotifier(createNotifier(listener), null);
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

    private ChannelListener<StreamConnection> createOpenListener(final ClientCallback<ClientConnection> listener, final URI uri, final XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap options) {
        return new ChannelListener<StreamConnection>() {
            @Override
            public void handleEvent(StreamConnection connection) {
                handleConnected(connection, listener, uri, bufferPool, options);
            }
        };
    }

    public static ALPNClientSelector.ALPNProtocol alpnProtocol(final ClientCallback<ClientConnection> listener, URI uri, ByteBufferPool bufferPool, OptionMap options) {
        return new ALPNClientSelector.ALPNProtocol(new ChannelListener<SslConnection>() {
            @Override
            public void handleEvent(SslConnection connection) {
                listener.completed(createHttp2Channel(connection, bufferPool, options, uri.getHost()));
            }
        }, HTTP2);
    };

    private void handleConnected(StreamConnection connection, final ClientCallback<ClientConnection> listener, URI uri,ByteBufferPool bufferPool, OptionMap options) {
        ALPNClientSelector.runAlpn((SslConnection) connection, FAILED, listener, alpnProtocol(listener, uri, bufferPool, options));
    }

    private static Http2ClientConnection createHttp2Channel(StreamConnection connection, ByteBufferPool bufferPool, OptionMap options, String defaultHost) {

        final ClientStatisticsImpl clientStatistics;
        //first we set up statistics, if required
        if (options.get(UndertowOptions.ENABLE_STATISTICS, false)) {
            clientStatistics = new ClientStatisticsImpl();
            connection.getSinkChannel().setConduit(new BytesSentStreamSinkConduit(connection.getSinkChannel().getConduit(), new ByteActivityCallback() {
                @Override
                public void activity(long bytes) {
                    clientStatistics.written += bytes;
                }
            }));
            connection.getSourceChannel().setConduit(new BytesReceivedStreamSourceConduit(connection.getSourceChannel().getConduit(), new ByteActivityCallback() {
                @Override
                public void activity(long bytes) {
                    clientStatistics.read += bytes;
                }
            }));
        } else {
            clientStatistics = null;
        }
        Http2Channel http2Channel = new Http2Channel(connection, null, bufferPool, null, true, false, options);
        return new Http2ClientConnection(http2Channel, false, defaultHost, clientStatistics, true);
    }

    private static class ClientStatisticsImpl implements ClientStatistics {
        private long requestCount, read, written;
        @Override
        public long getRequests() {
            return requestCount;
        }

        @Override
        public long getRead() {
            return read;
        }

        @Override
        public long getWritten() {
            return written;
        }

        @Override
        public void reset() {
            read = 0;
            written = 0;
            requestCount = 0;
        }
    }
}
