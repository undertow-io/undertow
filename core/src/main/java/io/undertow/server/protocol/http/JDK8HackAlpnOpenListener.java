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
import io.undertow.protocols.ssl.ALPNHackSSLEngine;
import io.undertow.protocols.ssl.SslConduit;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.AggregateConnectorStatistics;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.HttpHandler;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.ssl.SslConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Open listener adaptor for ALPN connections that uses the SSLExplorer based approach and hack into the JDK8
 * SSLEngine via reflection.
 *
 * @author Stuart Douglas
 */
public class JDK8HackAlpnOpenListener implements ChannelListener<StreamConnection>, AlpnOpenListener.AlpnDelegateListener {

    private final ByteBufferPool bufferPool;

    private final Map<String, ListenerEntry> listeners = new HashMap<>();
    private final String fallbackProtocol;
    private volatile HttpHandler rootHandler;
    private volatile OptionMap undertowOptions;
    private volatile boolean statisticsEnabled;

    public static boolean ENABLED = ALPNHackSSLEngine.ENABLED;


    public JDK8HackAlpnOpenListener(ByteBufferPool bufferPool, OptionMap undertowOptions, String fallbackProtocol, DelegateOpenListener fallbackListener) {
        this.bufferPool = bufferPool;
        this.fallbackProtocol = fallbackProtocol;
        if(fallbackProtocol != null && fallbackListener != null) {
            addProtocol(fallbackProtocol, fallbackListener, 0);
        }
        statisticsEnabled = undertowOptions.get(UndertowOptions.ENABLE_STATISTICS, false);
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
        public int compareTo(ListenerEntry o) {
            return -Integer.compare(this.weight, o.weight);
        }
    }

    public void addProtocol(String name, DelegateOpenListener listener, int weight) {
        listeners.put(name, new ListenerEntry(listener, weight, name));
    }

    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        final SslConduit sslConduit = UndertowXnioSsl.getSslConduit((SslConnection) channel);
        ALPNHackSSLEngine engine = new ALPNHackSSLEngine(sslConduit.getSSLEngine());
        sslConduit.setSslEngine(engine);

        final AlpnConnectionListener potentialConnection = new AlpnConnectionListener(channel, engine);
        channel.getSourceChannel().setReadListener(potentialConnection);
        List<String> protocols = new ArrayList<>();
        List<ListenerEntry> entries = new ArrayList<>(listeners.values());
        Collections.sort(entries);
        for(int i = 0; i < entries.size(); ++i) {
            protocols.add(entries.get(i).protocol);
        }
        engine.setApplicationProtocols(protocols);
        potentialConnection.handleEvent(channel.getSourceChannel());

    }

    private class AlpnConnectionListener implements ChannelListener<StreamSourceChannel> {
        private final StreamConnection channel;
        private final ALPNHackSSLEngine alpnsslEngine;

        private AlpnConnectionListener(StreamConnection channel, ALPNHackSSLEngine alpnsslEngine) {
            this.channel = channel;
            this.alpnsslEngine = alpnsslEngine;
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
                    final String selected = alpnsslEngine.getSelectedApplicationProtocol();
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
