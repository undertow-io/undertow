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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.net.ssl.SSLEngine;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.ssl.SslConnection;

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

/**
 * Open listener adaptor for ALPN connections
 * <p>
 * Not a proper open listener as such, but more a mechanism for selecting between them.
 *
 * @author Stuart Douglas
 */
public class AlpnOpenListener implements ChannelListener<StreamConnection>, OpenListener {

    /**
     * HTTP/2 required cipher. Not strictly part of ALPN but it can live here for now till we have a better solution.
     */
    public static final String REQUIRED_CIPHER = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
    /**
     * Names of ciphers in IBM JVM are prefixed with `SSL` instead of `TLS`, see e.g.:
     * https://www.ibm.com/support/knowledgecenter/SSFKSJ_9.0.0/com.ibm.mq.dev.doc/q113210_.htm.
     * Thus let's have IBM alternative for the REQUIRED_CIPHER variable too.
     */
    public static final String IBM_REQUIRED_CIPHER = "SSL_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
    private static final Set<String> REQUIRED_PROTOCOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("TLSv1.2","TLSv1.3")));

    private final ALPNManager alpnManager = ALPNManager.INSTANCE; //todo: configurable
    private final ByteBufferPool bufferPool;

    private final Map<String, ListenerEntry> listeners = new HashMap<>();
    private String[] protocols;
    private final String fallbackProtocol;
    private volatile HttpHandler rootHandler;
    private volatile OptionMap undertowOptions;
    private volatile boolean statisticsEnabled;

    private volatile boolean providerLogged;
    private volatile boolean alpnFailLogged;

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool, OptionMap undertowOptions, DelegateOpenListener httpListener) {
        this(bufferPool, undertowOptions, "http/1.1", httpListener);
    }

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool, OptionMap undertowOptions) {
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

    public AlpnOpenListener(ByteBufferPool bufferPool, OptionMap undertowOptions) {
        this(bufferPool, undertowOptions, null, null);
    }

    public AlpnOpenListener(ByteBufferPool bufferPool, OptionMap undertowOptions, String fallbackProtocol, DelegateOpenListener fallbackListener) {
        this.bufferPool = bufferPool;
        this.undertowOptions = undertowOptions;
        this.fallbackProtocol = fallbackProtocol;
        statisticsEnabled = undertowOptions.get(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false);
        if (fallbackProtocol != null && fallbackListener != null) {
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
        for (Map.Entry<String, ListenerEntry> delegate : listeners.entrySet()) {
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
        for (Map.Entry<String, ListenerEntry> delegate : listeners.entrySet()) {
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
        if (statisticsEnabled) {
            List<ConnectorStatistics> stats = new ArrayList<>();
            for (Map.Entry<String, ListenerEntry> l : listeners.entrySet()) {
                ConnectorStatistics c = l.getValue().listener.getConnectorStatistics();
                if (c != null) {
                    stats.add(c);
                }
            }
            return new AggregateConnectorStatistics(stats.toArray(new ConnectorStatistics[stats.size()]));
        }
        return null;
    }

    @Override
    public void closeConnections() {
        for(Map.Entry<String, ListenerEntry> i : listeners.entrySet()) {
            i.getValue().listener.closeConnections();
        }
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
        for (int i = 0; i < list.size(); ++i) {
            protocols[i] = list.get(i).protocol;
        }
        return this;
    }


    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        final SslConduit sslConduit = UndertowXnioSsl.getSslConduit((SslConnection) channel);
        final SSLEngine originalSSlEngine = sslConduit.getSSLEngine();

        //this will end up with the ALPN engine, or null if the engine did not support ALPN
        final CompletableFuture<SelectedAlpn> selectedALPNEngine = new CompletableFuture<>();
        alpnManager.registerEngineCallback(originalSSlEngine, new SSLConduitUpdater(sslConduit, new Function<SSLEngine, SSLEngine>() {
            @Override
            public SSLEngine apply(SSLEngine engine) {

                if (!engineSupportsHTTP2(engine)) {
                    if (!alpnFailLogged) {
                        synchronized (this) {
                            if (!alpnFailLogged) {
                                UndertowLogger.REQUEST_LOGGER.debugf("ALPN has been configured however %s is not present or TLS1.2 is not enabled, falling back to default protocol", REQUIRED_CIPHER);
                                alpnFailLogged = true;
                            }
                        }
                    }
                    if (fallbackProtocol != null) {
                        ListenerEntry listener = listeners.get(fallbackProtocol);
                        if (listener != null) {
                            selectedALPNEngine.complete(null);
                            return engine;
                        }
                    }
                }
                final ALPNProvider provider = alpnManager.getProvider(engine);
                if (provider == null) {
                    if (!providerLogged) {
                        synchronized (this) {
                            if (!providerLogged) {
                                UndertowLogger.REQUEST_LOGGER.debugf("ALPN has been configured however no provider could be found for engine %s for connector at %s", engine, channel.getLocalAddress());
                                providerLogged = true;
                            }
                        }
                    }
                    if (fallbackProtocol != null) {
                        ListenerEntry listener = listeners.get(fallbackProtocol);
                        if (listener != null) {
                            selectedALPNEngine.complete(null);
                            return engine;
                        }
                    }
                    UndertowLogger.REQUEST_LOGGER.debugf("No ALPN provider available and no fallback defined");
                    IoUtils.safeClose(channel);
                    selectedALPNEngine.complete(null);
                    return engine;
                }

                if (!providerLogged) {
                    synchronized (this) {
                        if (!providerLogged) {
                            UndertowLogger.REQUEST_LOGGER.debugf("Using ALPN provider %s for connector at %s", provider, channel.getLocalAddress());
                            providerLogged = true;
                        }
                    }
                }

                final SSLEngine newEngine = provider.setProtocols(engine, protocols);
                ALPNLimitingSSLEngine alpnLimitingSSLEngine = new ALPNLimitingSSLEngine(newEngine, new Runnable() {
                    @Override
                    public void run() {
                        provider.setProtocols(newEngine, new String[]{fallbackProtocol});
                    }
                });
                selectedALPNEngine.complete(new SelectedAlpn(newEngine, provider)); //we don't want the limiting engine, but the actual one we can use with a provider
                return alpnLimitingSSLEngine;
            }
        }));


        final AlpnConnectionListener potentialConnection = new AlpnConnectionListener(channel, selectedALPNEngine);
        channel.getSourceChannel().setReadListener(potentialConnection);
        potentialConnection.handleEvent(channel.getSourceChannel());

    }

    public static boolean engineSupportsHTTP2(SSLEngine engine) {
        //check to make sure the engine meets the minimum requirements for HTTP/2
        //if not then ALPN will not be attempted
        String[] protcols = engine.getEnabledProtocols();
        boolean found = false;
        for (String proto : protcols) {
            if (REQUIRED_PROTOCOLS.contains(proto)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return false;
        }

        String[] ciphers = engine.getEnabledCipherSuites();
        for (String i : ciphers) {
            if (i.equals(REQUIRED_CIPHER) || i.equals(IBM_REQUIRED_CIPHER)) {
                return true;
            }
        }
        return false;
    }

    private class AlpnConnectionListener implements ChannelListener<StreamSourceChannel> {
        private final StreamConnection channel;
        private final CompletableFuture<SelectedAlpn> selectedAlpn;

        private AlpnConnectionListener(StreamConnection channel, CompletableFuture<SelectedAlpn> selectedAlpn) {
            this.channel = channel;
            this.selectedAlpn = selectedAlpn;
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
                    SelectedAlpn selectedAlpn = this.selectedAlpn.getNow(null);
                    final String selected;
                    if (selectedAlpn != null) {
                        selected = selectedAlpn.provider.getSelectedProtocol(selectedAlpn.engine);
                    } else {
                        selected = null;
                    }
                    if (selected != null) {
                        DelegateOpenListener listener;
                        if (selected.isEmpty()) {
                            //alpn not in use
                            if (fallbackProtocol == null) {
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
                    } else if (res > 0) {
                        if (fallbackProtocol == null) {
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
            } catch (Throwable t) {
                UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
                IoUtils.safeClose(channel);
            } finally {
                if (free) {
                    buffer.close();
                }
            }
        }
    }

    static final class SelectedAlpn {
        final SSLEngine engine;
        final ALPNProvider provider;

        SelectedAlpn(SSLEngine engine, ALPNProvider provider) {
            this.engine = engine;
            this.provider = provider;
        }
    }

    static final class SSLConduitUpdater implements Function<SSLEngine, SSLEngine> {
        final SslConduit conduit;
        final Function<SSLEngine, SSLEngine> underlying;

        SSLConduitUpdater(SslConduit conduit, Function<SSLEngine, SSLEngine> underlying) {
            this.conduit = conduit;
            this.underlying = underlying;
        }

        @Override
        public SSLEngine apply(SSLEngine engine) {
            SSLEngine res = underlying.apply(engine);
            conduit.setSslEngine(res);
            return res;
        }
    }
}
