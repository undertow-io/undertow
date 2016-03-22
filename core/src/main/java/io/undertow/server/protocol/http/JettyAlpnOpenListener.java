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

package io.undertow.server.protocol.http;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.AggregateConnectorStatistics;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.HttpHandler;
import org.eclipse.jetty.alpn.ALPN;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Open listener adaptor for ALPN connections
 *
 * Not a proper open listener as such, but more a mechanism for selecting between them
 *
 * @author Stuart Douglas
 */
class JettyAlpnOpenListener implements ChannelListener<StreamConnection>, AlpnOpenListener.AlpnDelegateListener {

    private static final String PROTOCOL_KEY = JettyAlpnOpenListener.class.getName() + ".protocol";

    private final ByteBufferPool bufferPool;

    private final Map<String, ListenerEntry> listeners = new HashMap<>();
    private final String fallbackProtocol;
    private volatile HttpHandler rootHandler;
    private volatile OptionMap undertowOptions;
    private volatile boolean statisticsEnabled;

    JettyAlpnOpenListener(ByteBufferPool bufferPool, OptionMap undertowOptions, String fallbackProtocol, DelegateOpenListener fallbackListener) {
        this.bufferPool = bufferPool;
        this.fallbackProtocol = fallbackProtocol;
        if(fallbackProtocol != null && fallbackListener != null) {
            addProtocol(fallbackProtocol, fallbackListener, 0);
        }
        statisticsEnabled = undertowOptions.get(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false);
        this.undertowOptions = undertowOptions;
    }


    @Override
    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    @Override
    public void setRootHandler(HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
        for(Map.Entry<String, ListenerEntry> delegate : listeners.entrySet()) {
            delegate.getValue().listener.setRootHandler(rootHandler);
        }
    }

    @Override
    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    @Override
    public void setUndertowOptions(OptionMap undertowOptions) {
        if (undertowOptions == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("undertowOptions");
        }
        this.undertowOptions = undertowOptions;
        for(Map.Entry<String, ListenerEntry> delegate : listeners.entrySet()) {
            delegate.getValue().listener.setRootHandler(rootHandler);
        }
        statisticsEnabled = undertowOptions.get(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false);
    }

    @Override
    public ByteBufferPool getBufferPool() {
        return bufferPool;
    }

    @Override
    public ConnectorStatistics getConnectorStatistics() {
        if(statisticsEnabled) {
            List<ConnectorStatistics> stats = new ArrayList<>();
            for(Map.Entry<String, ListenerEntry> l : listeners.entrySet()) {
                ConnectorStatistics c = l.getValue().listener.getConnectorStatistics();
                if(c != null) {
                    stats.add(c);
                }
            }
            return new AggregateConnectorStatistics(stats.toArray(new ConnectorStatistics[stats.size()]));
        }
        return null;
    }

    private static class ListenerEntry {
        DelegateOpenListener listener;
        int weight;

        ListenerEntry(DelegateOpenListener listener, int weight) {
            this.listener = listener;
            this.weight = weight;
        }
    }

    public void addProtocol(String name, DelegateOpenListener listener, int weight) {
        listeners.put(name, new ListenerEntry(listener, weight));
    }

    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        final AlpnConnectionListener potentialConnection = new AlpnConnectionListener(channel);
        channel.getSourceChannel().setReadListener(potentialConnection);
        final SSLEngine sslEngine = UndertowXnioSsl.getSslEngine((SslConnection) channel);
        ALPN.put(sslEngine, new ALPN.ServerProvider() {
            @Override
            public void unsupported() {
                final String existing = (String) sslEngine.getHandshakeSession().getValue(PROTOCOL_KEY);
                if (existing == null || !listeners.containsKey(existing)) {
                    if(fallbackProtocol == null) {
                        UndertowLogger.REQUEST_IO_LOGGER.noALPNFallback(channel.getPeerAddress());
                        IoUtils.safeClose(channel);
                    }
                    potentialConnection.selected = fallbackProtocol;
                } else {
                    potentialConnection.selected = existing;
                }
            }

            @Override
            public String select(List<String> strings) {

                ALPN.remove(sslEngine);

                String match = null;
                int lastWeight = -1;
                for (String s : strings) {
                    ListenerEntry listener = listeners.get(s);
                    if (listener != null && listener.weight > lastWeight) {
                        match = s;
                        lastWeight = listener.weight;
                    }
                }

                if (match != null) {
                    sslEngine.getHandshakeSession().putValue(PROTOCOL_KEY, match);
                    return potentialConnection.selected = match;
                }

                if(fallbackProtocol == null) {
                    UndertowLogger.REQUEST_IO_LOGGER.noALPNFallback(channel.getPeerAddress());
                    IoUtils.safeClose(channel);
                    return null;
                }
                sslEngine.getHandshakeSession().putValue(PROTOCOL_KEY, fallbackProtocol);
                potentialConnection.selected = fallbackProtocol;
                return fallbackProtocol;
            }
        });
        potentialConnection.handleEvent(channel.getSourceChannel());

    }

    private class AlpnConnectionListener implements ChannelListener<StreamSourceChannel> {
        private String selected;
        private final StreamConnection channel;

        private AlpnConnectionListener(StreamConnection channel) {
            this.channel = channel;
        }

        @Override
        public void handleEvent(StreamSourceChannel source) {
            PooledByteBuffer buffer = bufferPool.allocate();
            boolean free = true;
            try {
                while (true) {
                    int res = channel.getSourceChannel().read(buffer.getBuffer());
                    if (res == -1) {
                        IoUtils.safeClose(channel);
                        return;
                    }
                    buffer.getBuffer().flip();
                    if(selected != null) {
                        DelegateOpenListener listener = listeners.get(selected).listener;
                        source.getReadSetter().set(null);
                        listener.handleEvent(channel, buffer);
                        free = false;
                        return;
                    } else if(res > 0) {
                        if(fallbackProtocol == null) {
                            UndertowLogger.REQUEST_IO_LOGGER.noALPNFallback(channel.getPeerAddress());
                            IoUtils.safeClose(channel);
                            return;
                        }
                        DelegateOpenListener listener = listeners.get(fallbackProtocol).listener;
                        source.getReadSetter().set(null);
                        listener.handleEvent(channel, buffer);
                        free = false;
                        return;
                    } else if (res == 0) {
                        channel.getSourceChannel().resumeReads();
                        return;
                    }
                }

            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(channel);
            } finally {
                if (free) {
                    buffer.close();
                }
            }
        }
    }

}
