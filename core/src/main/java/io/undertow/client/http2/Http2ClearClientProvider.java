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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.client.ClientStatistics;
import io.undertow.conduits.ByteActivityCallback;
import io.undertow.conduits.BytesReceivedStreamSourceConduit;
import io.undertow.conduits.BytesSentStreamSinkConduit;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.BoundChannel;
import org.xnio.http.HttpUpgrade;
import org.xnio.ssl.XnioSsl;

import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientProvider;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.protocols.http2.Http2Setting;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;

/**
 * HTTP2 client provider that uses HTTP upgrade rather than ALPN. This provider will only use h2c, and sends an initial
 * dummy request to do the initial upgrade.
 *
 *
 *
 * @author Stuart Douglas
 */
public class Http2ClearClientProvider implements ClientProvider {

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
        return new HashSet<>(Arrays.asList(new String[]{"h2c"}));
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, InetSocketAddress bindAddress, final URI uri, final XnioWorker worker, final XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap options) {
        final URI upgradeUri;
        try {
            upgradeUri = new URI("http", uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            listener.failed(new IOException(e));
            return;
        }
        Map<String, String> headers = createHeaders(options, bufferPool, uri);
        HttpUpgrade.performUpgrade(worker, bindAddress, upgradeUri, headers, new Http2ClearOpenListener(bufferPool, options, listener, uri.getHost()), null, options, null).addNotifier(new FailedNotifier(listener), null);
    }

    @Override
    public void connect(final ClientCallback<ClientConnection> listener, final InetSocketAddress bindAddress, final URI uri, final XnioIoThread ioThread, final XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap options) {
        final URI upgradeUri;
        try {
            upgradeUri = new URI("http", uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            listener.failed(new IOException(e));
            return;
        }

        if (bindAddress != null) {
            ioThread.openStreamConnection(bindAddress, new InetSocketAddress(uri.getHost(), uri.getPort()), new ChannelListener<StreamConnection>() {
                @Override
                public void handleEvent(StreamConnection channel) {
                    Map<String, String> headers = createHeaders(options, bufferPool, uri);
                    HttpUpgrade.performUpgrade(channel, upgradeUri, headers, new Http2ClearOpenListener(bufferPool, options, listener, uri.getHost()), null).addNotifier(new FailedNotifier(listener), null);
                }
            }, new ChannelListener<BoundChannel>() {
                @Override
                public void handleEvent(BoundChannel channel) {

                }
            }, options).addNotifier(new FailedNotifier(listener), null);
        } else {
            ioThread.openStreamConnection(new InetSocketAddress(uri.getHost(), uri.getPort()), new ChannelListener<StreamConnection>() {
                @Override
                public void handleEvent(StreamConnection channel) {
                    Map<String, String> headers = createHeaders(options, bufferPool, uri);
                    HttpUpgrade.performUpgrade(channel, upgradeUri, headers, new Http2ClearOpenListener(bufferPool, options, listener, uri.getHost()), null).addNotifier(new FailedNotifier(listener), null);
                }
            }, new ChannelListener<BoundChannel>() {
                @Override
                public void handleEvent(BoundChannel channel) {

                }
            }, options).addNotifier(new FailedNotifier(listener), null);
        }

    }

    private Map<String, String> createHeaders(OptionMap options, ByteBufferPool bufferPool, URI uri) {
        Map<String, String> headers = new HashMap<>();
        headers.put("HTTP2-Settings", createSettingsFrame(options, bufferPool));
        headers.put(Headers.UPGRADE_STRING, Http2Channel.CLEARTEXT_UPGRADE_STRING);
        headers.put(Headers.CONNECTION_STRING, "Upgrade, HTTP2-Settings");
        headers.put(Headers.HOST_STRING, uri.getHost());
        headers.put("X-HTTP2-connect-only", "connect"); //undertow specific header that tells the remote server that this request should be ignored
        return headers;
    }


    public static String createSettingsFrame(OptionMap options, ByteBufferPool bufferPool) {
        PooledByteBuffer b = bufferPool.allocate();
        try {
            ByteBuffer currentBuffer = b.getBuffer();

            if (options.contains(UndertowOptions.HTTP2_SETTINGS_HEADER_TABLE_SIZE)) {
                pushOption(currentBuffer, Http2Setting.SETTINGS_HEADER_TABLE_SIZE, options.get(UndertowOptions.HTTP2_SETTINGS_HEADER_TABLE_SIZE));
            }
            if (options.contains(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH)) {
                pushOption(currentBuffer, Http2Setting.SETTINGS_ENABLE_PUSH, options.get(UndertowOptions.HTTP2_SETTINGS_ENABLE_PUSH) ? 1 : 0);
            }

            if (options.contains(UndertowOptions.HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS)) {
                pushOption(currentBuffer, Http2Setting.SETTINGS_MAX_CONCURRENT_STREAMS, options.get(UndertowOptions.HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS));
            }

            if (options.contains(UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE)) {
                pushOption(currentBuffer, Http2Setting.SETTINGS_INITIAL_WINDOW_SIZE, options.get(UndertowOptions.HTTP2_SETTINGS_INITIAL_WINDOW_SIZE));
            }

            if (options.contains(UndertowOptions.HTTP2_SETTINGS_MAX_FRAME_SIZE)) {
                pushOption(currentBuffer, Http2Setting.SETTINGS_MAX_FRAME_SIZE, options.get(UndertowOptions.HTTP2_SETTINGS_MAX_FRAME_SIZE));
            }

            if (options.contains(UndertowOptions.HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE)) {
                pushOption(currentBuffer, Http2Setting.SETTINGS_MAX_HEADER_LIST_SIZE, options.get(UndertowOptions.HTTP2_SETTINGS_MAX_HEADER_LIST_SIZE));
            }
            currentBuffer.flip();
            return FlexBase64.encodeStringURL(currentBuffer, false);
        } finally {
            b.close();
        }
    }

    private static void pushOption(ByteBuffer currentBuffer, int id, int value) {
        currentBuffer.put((byte) ((id >> 8) & 0xFF));
        currentBuffer.put((byte) (id & 0xFF));
        currentBuffer.put((byte) ((value >> 24) & 0xFF));
        currentBuffer.put((byte) ((value >> 16) & 0xFF));
        currentBuffer.put((byte) ((value >> 8) & 0xFF));
        currentBuffer.put((byte) (value & 0xFF));
    }

    private static class Http2ClearOpenListener implements ChannelListener<StreamConnection> {

        private final ByteBufferPool bufferPool;
        private final OptionMap options;
        private final ClientCallback<ClientConnection> listener;
        private final String defaultHost;

        Http2ClearOpenListener(ByteBufferPool bufferPool, OptionMap options, ClientCallback<ClientConnection> listener, String defaultHost) {
            this.bufferPool = bufferPool;
            this.options = options;
            this.listener = listener;
            this.defaultHost = defaultHost;
        }

        @Override
        public void handleEvent(StreamConnection channel) {

            final ClientStatisticsImpl clientStatistics;
            //first we set up statistics, if required
            if (options.get(UndertowOptions.ENABLE_STATISTICS, false)) {
                clientStatistics = new ClientStatisticsImpl();
                channel.getSinkChannel().setConduit(new BytesSentStreamSinkConduit(channel.getSinkChannel().getConduit(), new ByteActivityCallback() {
                    @Override
                    public void activity(long bytes) {
                        clientStatistics.written += bytes;
                    }
                }));
                channel.getSourceChannel().setConduit(new BytesReceivedStreamSourceConduit(channel.getSourceChannel().getConduit(), new ByteActivityCallback() {
                    @Override
                    public void activity(long bytes) {
                        clientStatistics.read += bytes;
                    }
                }));
            } else {
                clientStatistics = null;
            }

            Http2Channel http2Channel = new Http2Channel(channel, null, bufferPool, null, true, true, options);
            Http2ClientConnection http2ClientConnection = new Http2ClientConnection(http2Channel, true, defaultHost, clientStatistics, false);

            listener.completed(http2ClientConnection);
        }
    }

    private static class FailedNotifier implements IoFuture.Notifier<StreamConnection, Object> {
        private final ClientCallback<ClientConnection> listener;

        FailedNotifier(ClientCallback<ClientConnection> listener) {
            this.listener = listener;
        }

        @Override
        public void notify(IoFuture<? extends StreamConnection> ioFuture, Object attachment) {
            if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                listener.failed(ioFuture.getException());
            }
        }
    }
    private static class ClientStatisticsImpl implements ClientStatistics {
        private long requestCount, read, written;

        public long getRequestCount() {
            return requestCount;
        }

        public void setRequestCount(long requestCount) {
            this.requestCount = requestCount;
        }

        public void setRead(long read) {
            this.read = read;
        }

        public void setWritten(long written) {
            this.written = written;
        }

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
