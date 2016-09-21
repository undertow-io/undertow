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
import io.undertow.protocols.alpn.ALPNManager;
import io.undertow.protocols.alpn.ALPNProvider;
import io.undertow.protocols.ssl.SslConduit;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.AggregateConnectorStatistics;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import io.undertow.server.XnioByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.ssl.SslConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLEngine;

/**
 * Open listener adaptor for ALPN connections
 *
 * Not a proper open listener as such, but more a mechanism for selecting between them.
 *
 *
 *
 * @author Stuart Douglas
 */
public class AlpnOpenListener implements ChannelListener<StreamConnection>, OpenListener {

    private final ALPNManager alpnManager = ALPNManager.INSTANCE; //todo: configurable
    private final ByteBufferPool bufferPool;

    private final Map<String, ListenerEntry> listeners = new HashMap<>();
    private String[] protocols;
    private final String fallbackProtocol;
    private volatile HttpHandler rootHandler;
    private volatile OptionMap undertowOptions;
    private volatile boolean statisticsEnabled;

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool, OptionMap undertowOptions, DelegateOpenListener httpListener) {
        this(bufferPool, undertowOptions, "http/1.1", httpListener);
    }

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool,  OptionMap undertowOptions) {
        this(bufferPool, undertowOptions, null, null);
    }

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool, OptionMap undertowOptions, String fallbackProtocol, DelegateOpenListener fallbackListener) {
        this(new XnioByteBufferPool(bufferPool), undertowOptions, fallbackProtocol, fallbackListener);
    }

    public AlpnOpenListener(ByteBufferPool bufferPool, OptionMap undertowOptions, DelegateOpenListener httpListener) {
        this(bufferPool, undertowOptions, "http/1.1", httpListener);
    }

    public AlpnOpenListener(ByteBufferPool bufferPool) {
        this(bufferPool, OptionMap.EMPTY, null, null);
    }

    public AlpnOpenListener(ByteBufferPool bufferPool,  OptionMap undertowOptions) {
        this(bufferPool, undertowOptions, null, null);
    }

    public AlpnOpenListener(ByteBufferPool bufferPool, OptionMap undertowOptions, String fallbackProtocol, DelegateOpenListener fallbackListener) {
        this.bufferPool = bufferPool;
        this.undertowOptions = undertowOptions;
        this.fallbackProtocol = fallbackProtocol;
        statisticsEnabled = undertowOptions.get(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false);
        if(fallbackProtocol != null && fallbackListener != null) {
            addProtocol(fallbackProtocol, fallbackListener, 0);
        }
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


    private static class ListenerEntry implements Comparable<ListenerEntry> {
        final DelegateOpenListener listener;
        final int weight;
        final String protocol;

        ListenerEntry(DelegateOpenListener listener, int weight, String protocol) {
            this.listener = listener;
            this.weight = weight;
            this.protocol = protocol;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ListenerEntry)) return false;

            ListenerEntry that = (ListenerEntry) o;

            if (weight != that.weight) return false;
            if (!listener.equals(that.listener)) return false;
            return protocol.equals(that.protocol);
        }

        @Override
        public int hashCode() {
            int result = listener.hashCode();
            result = 31 * result + weight;
            result = 31 * result + protocol.hashCode();
            return result;
        }

        @Override
        public int compareTo(ListenerEntry o) {
            return -Integer.compare(this.weight, o.weight);
        }
    }

    public AlpnOpenListener addProtocol(String name, DelegateOpenListener listener, int weight) {
        listeners.put(name, new ListenerEntry(listener, weight, name));
        List<ListenerEntry> list = new ArrayList<>(listeners.values());
        Collections.sort(list);
        protocols = new String[list.size()];
        for(int i = 0; i < list.size(); ++i) {
            protocols[i] = list.get(i).protocol;
        }
        return this;
    }


    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        final SslConduit sslConduit = UndertowXnioSsl.getSslConduit((SslConnection) channel);
        final SSLEngine sslEngine = sslConduit.getSSLEngine();

        ALPNProvider provider = alpnManager.getProvider(sslEngine);
        if(provider == null) {
            if(fallbackProtocol != null) {
                ListenerEntry listener = listeners.get(fallbackProtocol);
                if(listener != null) {
                    listener.listener.handleEvent(channel);
                    return;
                }
            }
            UndertowLogger.REQUEST_LOGGER.debugf("No ALPN provider available and no fallback defined");
            IoUtils.safeClose(channel);
            return;
        }

        SSLEngine newEngine = provider.setProtocols(sslEngine, protocols);
        if(newEngine != sslEngine) {
            sslConduit.setSslEngine(newEngine);
        }
        final AlpnConnectionListener potentialConnection = new AlpnConnectionListener(channel, newEngine, provider);
        channel.getSourceChannel().setReadListener(potentialConnection);
        potentialConnection.handleEvent(channel.getSourceChannel());

    }

    private class AlpnConnectionListener implements ChannelListener<StreamSourceChannel> {
        private final StreamConnection channel;
        private final SSLEngine engine;
        private final ALPNProvider provider;

        private AlpnConnectionListener(StreamConnection channel, SSLEngine engine, ALPNProvider provider) {
            this.channel = channel;
            this.engine = engine;
            this.provider = provider;
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
                    final String selected = provider.getSelectedProtocol(engine);
                    if(selected != null) {
                        DelegateOpenListener listener;
                        if(selected.isEmpty()) {
                            //alpn not in use
                            if(fallbackProtocol == null) {
                                UndertowLogger.REQUEST_IO_LOGGER.noALPNFallback(channel.getPeerAddress());
                                IoUtils.safeClose(channel);
                                return;
                            }
                            listener = listeners.get(fallbackProtocol).listener;
                        } else {
                            listener = listeners.get(selected).listener;
                        }
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
            }  finally {
                if (free) {
                    buffer.close();
                }
            }
        }
    }
}
