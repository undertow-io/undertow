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

package io.undertow;

import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import io.undertow.server.protocol.ajp.AjpOpenListener;
import io.undertow.server.protocol.http.AlpnOpenListener;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.server.protocol.http2.Http2OpenListener;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.ssl.SslConnection;
import org.xnio.ssl.XnioSsl;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Convenience class used to build an Undertow server.
 * <p>
 *
 * @author Stuart Douglas
 */
public final class Undertow {

    private final int bufferSize;
    private final int ioThreads;
    private final int workerThreads;
    private final boolean directBuffers;
    private final List<ListenerConfig> listeners = new ArrayList<>();
    private volatile List<ListenerInfo> listenerInfo;
    private final HttpHandler rootHandler;
    private final OptionMap workerOptions;
    private final OptionMap socketOptions;
    private final OptionMap serverOptions;

    /**
     * Will be true when a {@link XnioWorker} instance was NOT provided to the {@link Builder}.
     * When true, a new worker will be created during {@link Undertow#start()},
     * and shutdown when {@link Undertow#stop()} is called.
     *
     * Will be false when a {@link XnioWorker} instance was provided to the {@link Builder}.
     * When false, the provided {@link #worker} will be used instead of creating a new one in {@link Undertow#start()}.
     * Also, when false, the {@link #worker} will NOT be shutdown when {@link Undertow#stop()} is called.
     */
    private final boolean internalWorker;

    private XnioWorker worker;
    private List<AcceptingChannel<? extends StreamConnection>> channels;
    private Xnio xnio;

    private Undertow(Builder builder) {
        this.bufferSize = builder.bufferSize;
        this.ioThreads = builder.ioThreads;
        this.workerThreads = builder.workerThreads;
        this.directBuffers = builder.directBuffers;
        this.listeners.addAll(builder.listeners);
        this.rootHandler = builder.handler;
        this.worker = builder.worker;
        this.internalWorker = builder.worker == null;
        this.workerOptions = builder.workerOptions.getMap();
        this.socketOptions = builder.socketOptions.getMap();
        this.serverOptions = builder.serverOptions.getMap();
    }

    /**
     * @return A builder that can be used to create an Undertow server instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public synchronized void start() {
        UndertowLogger.ROOT_LOGGER.debugf("starting undertow server %s", this);
        xnio = Xnio.getInstance(Undertow.class.getClassLoader());
        channels = new ArrayList<>();
        try {
            if (internalWorker) {
                worker = xnio.createWorker(OptionMap.builder()
                        .set(Options.WORKER_IO_THREADS, ioThreads)
                        .set(Options.CONNECTION_HIGH_WATER, 1000000)
                        .set(Options.CONNECTION_LOW_WATER, 1000000)
                        .set(Options.WORKER_TASK_CORE_THREADS, workerThreads)
                        .set(Options.WORKER_TASK_MAX_THREADS, workerThreads)
                        .set(Options.TCP_NODELAY, true)
                        .set(Options.CORK, true)
                        .addAll(workerOptions)
                        .getMap());
            }

            OptionMap socketOptions = OptionMap.builder()
                    .set(Options.WORKER_IO_THREADS, worker.getIoThreadCount())
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true)
                    .set(Options.BALANCING_TOKENS, 1)
                    .set(Options.BALANCING_CONNECTIONS, 2)
                    .set(Options.BACKLOG, 1000)
                    .addAll(this.socketOptions)
                    .getMap();

            OptionMap serverOptions = OptionMap.builder()
                    .set(UndertowOptions.NO_REQUEST_TIMEOUT, 60 * 1000)
                    .addAll(this.serverOptions)
                    .getMap();


            ByteBufferPool buffers = new DefaultByteBufferPool(directBuffers, bufferSize, -1, 4);

            listenerInfo = new ArrayList<>();
            for (ListenerConfig listener : listeners) {
                UndertowLogger.ROOT_LOGGER.debugf("Configuring listener with protocol %s for interface %s and port %s", listener.type, listener.host, listener.port);
                final HttpHandler rootHandler = listener.rootHandler != null ? listener.rootHandler : this.rootHandler;
                if (listener.type == ListenerType.AJP) {
                    AjpOpenListener openListener = new AjpOpenListener(buffers, serverOptions);
                    openListener.setRootHandler(rootHandler);
                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), acceptListener, socketOptions);
                    server.resumeAccepts();
                    channels.add(server);
                    listenerInfo.add(new ListenerInfo("ajp", server.getLocalAddress(), null, openListener));
                } else {
                    OptionMap undertowOptions = OptionMap.builder().set(UndertowOptions.BUFFER_PIPELINED_DATA, true).addAll(serverOptions).getMap();
                    if (listener.type == ListenerType.HTTP) {
                        HttpOpenListener openListener = new HttpOpenListener(buffers, undertowOptions);
                        openListener.setRootHandler(rootHandler);
                        ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                        AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), acceptListener, socketOptions);
                        server.resumeAccepts();
                        channels.add(server);
                        listenerInfo.add(new ListenerInfo("http", server.getLocalAddress(), null, openListener));
                    } else if (listener.type == ListenerType.HTTPS) {
                        OpenListener openListener;

                        HttpOpenListener httpOpenListener = new HttpOpenListener(buffers, undertowOptions);
                        httpOpenListener.setRootHandler(rootHandler);

                        boolean http2 = serverOptions.get(UndertowOptions.ENABLE_HTTP2, false);
                        if(http2) {
                            AlpnOpenListener alpn = new AlpnOpenListener(buffers, undertowOptions, httpOpenListener);
                            if(http2) {
                                Http2OpenListener http2Listener = new Http2OpenListener(buffers, undertowOptions);
                                http2Listener.setRootHandler(rootHandler);
                                alpn.addProtocol(Http2OpenListener.HTTP2, http2Listener, 10);
                                alpn.addProtocol(Http2OpenListener.HTTP2_14, http2Listener, 7);
                            }
                            openListener = alpn;
                        } else {
                            openListener = httpOpenListener;
                        }
                        ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                        XnioSsl xnioSsl;
                        if (listener.sslContext != null) {
                            xnioSsl = new UndertowXnioSsl(xnio, OptionMap.create(Options.USE_DIRECT_BUFFERS, true), listener.sslContext);
                        } else {
                            xnioSsl = xnio.getSslProvider(listener.keyManagers, listener.trustManagers, OptionMap.create(Options.USE_DIRECT_BUFFERS, true));
                        }
                        AcceptingChannel<SslConnection> sslServer = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), (ChannelListener) acceptListener, socketOptions);
                        sslServer.resumeAccepts();
                        channels.add(sslServer);
                        listenerInfo.add(new ListenerInfo("https", sslServer.getLocalAddress(), listener.sslContext, openListener));
                    }
                }

            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void stop() {
        UndertowLogger.ROOT_LOGGER.debugf("stopping undertow server %s", this);
        if (channels != null) {
            for (AcceptingChannel<? extends StreamConnection> channel : channels) {
                IoUtils.safeClose(channel);
            }
            channels = null;
        }

        /*
         * Only shutdown the worker if it was created during start()
         */
        if (internalWorker && worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        xnio = null;
        listenerInfo = null;
    }

    public Xnio getXnio() {
        return xnio;
    }

    public XnioWorker getWorker() {
        return worker;
    }

    public List<ListenerInfo> getListenerInfo() {
        if(listenerInfo == null) {
            throw UndertowMessages.MESSAGES.serverNotStarted();
        }
        return Collections.unmodifiableList(listenerInfo);
    }



    public enum ListenerType {
        HTTP,
        HTTPS,
        AJP
    }

    private static class ListenerConfig {
        final ListenerType type;
        final int port;
        final String host;
        final KeyManager[] keyManagers;
        final TrustManager[] trustManagers;
        final SSLContext sslContext;
        final HttpHandler rootHandler;

        private ListenerConfig(final ListenerType type, final int port, final String host, KeyManager[] keyManagers, TrustManager[] trustManagers, HttpHandler rootHandler) {
            this.type = type;
            this.port = port;
            this.host = host;
            this.keyManagers = keyManagers;
            this.trustManagers = trustManagers;
            this.rootHandler = rootHandler;
            this.sslContext = null;
        }

        private ListenerConfig(final ListenerType type, final int port, final String host, SSLContext sslContext, HttpHandler rootHandler) {
            this.type = type;
            this.port = port;
            this.host = host;
            this.rootHandler = rootHandler;
            this.keyManagers = null;
            this.trustManagers = null;
            this.sslContext = sslContext;
        }
    }

    public static final class Builder {

        private int bufferSize;
        private int ioThreads;
        private int workerThreads;
        private boolean directBuffers;
        private final List<ListenerConfig> listeners = new ArrayList<>();
        private HttpHandler handler;
        private XnioWorker worker;

        private final OptionMap.Builder workerOptions = OptionMap.builder();
        private final OptionMap.Builder socketOptions = OptionMap.builder();
        private final OptionMap.Builder serverOptions = OptionMap.builder();

        private Builder() {
            ioThreads = Math.max(Runtime.getRuntime().availableProcessors(), 2);
            workerThreads = ioThreads * 8;
            long maxMemory = Runtime.getRuntime().maxMemory();
            //smaller than 64mb of ram we use 512b buffers
            if (maxMemory < 64 * 1024 * 1024) {
                //use 512b buffers
                directBuffers = false;
                bufferSize = 512;
            } else if (maxMemory < 128 * 1024 * 1024) {
                //use 1k buffers
                directBuffers = true;
                bufferSize = 1024;
            } else {
                //use 16k buffers for best performance
                //as 16k is generally the max amount of data that can be sent in a single write() call
                directBuffers = true;
                bufferSize = 1024 * 16;
            }

        }

        public Undertow build() {
            return new Undertow(this);
        }

        @Deprecated
        public Builder addListener(int port, String host) {
            listeners.add(new ListenerConfig(ListenerType.HTTP, port, host, null, null, null));
            return this;
        }

        @Deprecated
        public Builder addListener(int port, String host, ListenerType listenerType) {
            listeners.add(new ListenerConfig(listenerType, port, host, null, null, null));
            return this;
        }

        public Builder addHttpListener(int port, String host) {
            listeners.add(new ListenerConfig(ListenerType.HTTP, port, host, null, null, null));
            return this;
        }

        public Builder addHttpsListener(int port, String host, KeyManager[] keyManagers, TrustManager[] trustManagers) {
            listeners.add(new ListenerConfig(ListenerType.HTTPS, port, host, keyManagers, trustManagers, null));
            return this;
        }

        public Builder addHttpsListener(int port, String host, SSLContext sslContext) {
            listeners.add(new ListenerConfig(ListenerType.HTTPS, port, host, sslContext, null));
            return this;
        }

        public Builder addAjpListener(int port, String host) {
            listeners.add(new ListenerConfig(ListenerType.AJP, port, host, null, null, null));
            return this;
        }

        public Builder addHttpListener(int port, String host, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(ListenerType.HTTP, port, host, null, null, rootHandler));
            return this;
        }

        public Builder addHttpsListener(int port, String host, KeyManager[] keyManagers, TrustManager[] trustManagers, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(ListenerType.HTTPS, port, host, keyManagers, trustManagers, rootHandler));
            return this;
        }

        public Builder addHttpsListener(int port, String host, SSLContext sslContext, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(ListenerType.HTTPS, port, host, sslContext, rootHandler));
            return this;
        }

        public Builder addAjpListener(int port, String host, HttpHandler rootHandler) {
            listeners.add(new ListenerConfig(ListenerType.AJP, port, host, null, null, rootHandler));
            return this;
        }
        public Builder setBufferSize(final int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        @Deprecated
        public Builder setBuffersPerRegion(final int buffersPerRegion) {
            return this;
        }

        public Builder setIoThreads(final int ioThreads) {
            this.ioThreads = ioThreads;
            return this;
        }

        public Builder setWorkerThreads(final int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }

        public Builder setDirectBuffers(final boolean directBuffers) {
            this.directBuffers = directBuffers;
            return this;
        }

        public Builder setHandler(final HttpHandler handler) {
            this.handler = handler;
            return this;
        }

        public <T> Builder setServerOption(final Option<T> option, final T value) {
            serverOptions.set(option, value);
            return this;
        }

        public <T> Builder setSocketOption(final Option<T> option, final T value) {
            socketOptions.set(option, value);
            return this;
        }

        public <T> Builder setWorkerOption(final Option<T> option, final T value) {
            workerOptions.set(option, value);
            return this;
        }

        /**
         * When null (the default), a new {@link XnioWorker} will be created according
         * to the various worker-related configuration (ioThreads, workerThreads, workerOptions)
         * when {@link Undertow#start()} is called.
         * Additionally, this newly created worker will be shutdown when {@link Undertow#stop()} is called.
         * <br/>
         *
         * When non-null, the provided {@link XnioWorker} will be reused instead of creating a new {@link XnioWorker}
         * when {@link Undertow#start()} is called.
         * Additionally, the provided {@link XnioWorker} will NOT be shutdown when {@link Undertow#stop()} is called.
         * Essentially, the lifecycle of the provided worker must be maintained outside of the {@link Undertow} instance.
         */
        public <T> Builder setWorker(XnioWorker worker) {
            this.worker = worker;
            return this;
        }
    }

    public static class ListenerInfo {

        private final String protcol;
        private final SocketAddress address;
        private final SSLContext sslContext;
        private final OpenListener openListener;

        public ListenerInfo(String protcol, SocketAddress address, SSLContext sslContext, OpenListener openListener) {
            this.protcol = protcol;
            this.address = address;
            this.sslContext = sslContext;
            this.openListener = openListener;
        }

        public String getProtcol() {
            return protcol;
        }

        public SocketAddress getAddress() {
            return address;
        }

        public SSLContext getSslContext() {
            return sslContext;
        }

        public ConnectorStatistics getConnectorStatistics() {
            return openListener.getConnectorStatistics();
        }

        @Override
        public String toString() {
            return "ListenerInfo{" +
                    "protcol='" + protcol + '\'' +
                    ", address=" + address +
                    ", sslContext=" + sslContext +
                    '}';
        }
    }

}
