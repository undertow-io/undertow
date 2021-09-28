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

import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import io.undertow.server.protocol.ajp.AjpOpenListener;
import io.undertow.server.protocol.http.AlpnOpenListener;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.server.protocol.http2.Http2OpenListener;
import io.undertow.server.protocol.http2.Http2UpgradeHandler;
import io.undertow.server.protocol.proxy.ProxyProtocolOpenListener;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.ssl.JsseSslUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
     * <p>
     * Will be false when a {@link XnioWorker} instance was provided to the {@link Builder}.
     * When false, the provided {@link #worker} will be used instead of creating a new one in {@link Undertow#start()}.
     * Also, when false, the {@link #worker} will NOT be shutdown when {@link Undertow#stop()} is called.
     */
    private final boolean internalWorker;

    private ByteBufferPool byteBufferPool;
    private XnioWorker worker;
    private Executor sslEngineDelegatedTaskExecutor;
    private List<AcceptingChannel<? extends StreamConnection>> channels;
    private Xnio xnio;

    private Undertow(Builder builder) {
        this.byteBufferPool = builder.byteBufferPool;
        this.bufferSize = byteBufferPool != null ? byteBufferPool.getBufferSize() : builder.bufferSize;
        this.directBuffers = byteBufferPool != null ? byteBufferPool.isDirect() : builder.directBuffers;
        this.ioThreads = builder.ioThreads;
        this.workerThreads = builder.workerThreads;
        this.listeners.addAll(builder.listeners);
        this.rootHandler = builder.handler;
        this.worker = builder.worker;
        this.sslEngineDelegatedTaskExecutor = builder.sslEngineDelegatedTaskExecutor;
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
        UndertowLogger.ROOT_LOGGER.infof("starting server: %s", Version.getFullVersionString());
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


            ByteBufferPool buffers = this.byteBufferPool;
            if (buffers == null) {
                buffers = new DefaultByteBufferPool(directBuffers, bufferSize, -1, 4);
            }

            listenerInfo = new ArrayList<>();
            for (ListenerConfig listener : listeners) {
                UndertowLogger.ROOT_LOGGER.debugf("Configuring listener with protocol %s for interface %s and port %s", listener.type, listener.host, listener.port);
                final HttpHandler rootHandler = listener.rootHandler != null ? listener.rootHandler : this.rootHandler;
                OptionMap socketOptionsWithOverrides = OptionMap.builder().addAll(socketOptions).addAll(listener.overrideSocketOptions).getMap();
                if (listener.type == ListenerType.AJP) {
                    AjpOpenListener openListener = new AjpOpenListener(buffers, serverOptions);
                    openListener.setRootHandler(rootHandler);

                    final ChannelListener<StreamConnection> finalListener;
                    if (listener.useProxyProtocol) {
                        finalListener = new ProxyProtocolOpenListener(openListener, null, buffers, OptionMap.EMPTY);
                    } else {
                        finalListener = openListener;
                    }
                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(finalListener);
                    AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), acceptListener, socketOptionsWithOverrides);
                    server.resumeAccepts();
                    channels.add(server);
                    listenerInfo.add(new ListenerInfo("ajp", server.getLocalAddress(), openListener, null, server));
                } else {
                    OptionMap undertowOptions = OptionMap.builder().set(UndertowOptions.BUFFER_PIPELINED_DATA, true).addAll(serverOptions).getMap();
                    boolean http2 = serverOptions.get(UndertowOptions.ENABLE_HTTP2, false);
                    if (listener.type == ListenerType.HTTP) {
                        HttpOpenListener openListener = new HttpOpenListener(buffers, undertowOptions);
                        HttpHandler handler = rootHandler;
                        if (http2) {
                            handler = new Http2UpgradeHandler(handler);
                        }
                        openListener.setRootHandler(handler);
                        final ChannelListener<StreamConnection> finalListener;
                        if (listener.useProxyProtocol) {
                            finalListener = new ProxyProtocolOpenListener(openListener, null, buffers, OptionMap.EMPTY);
                        } else {
                            finalListener = openListener;
                        }

                        ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(finalListener);
                        AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), acceptListener, socketOptionsWithOverrides);
                        server.resumeAccepts();
                        channels.add(server);
                        listenerInfo.add(new ListenerInfo("http", server.getLocalAddress(), openListener, null, server));
                    } else if (listener.type == ListenerType.HTTPS) {
                        OpenListener openListener;

                        HttpOpenListener httpOpenListener = new HttpOpenListener(buffers, undertowOptions);
                        httpOpenListener.setRootHandler(rootHandler);

                        if (http2) {
                            AlpnOpenListener alpn = new AlpnOpenListener(buffers, undertowOptions, httpOpenListener);
                            Http2OpenListener http2Listener = new Http2OpenListener(buffers, undertowOptions);
                            http2Listener.setRootHandler(rootHandler);
                            alpn.addProtocol(Http2OpenListener.HTTP2, http2Listener, 10);
                            alpn.addProtocol(Http2OpenListener.HTTP2_14, http2Listener, 7);
                            openListener = alpn;
                        } else {
                            openListener = httpOpenListener;
                        }

                        UndertowXnioSsl xnioSsl;
                        if (listener.sslContext != null) {
                            xnioSsl = new UndertowXnioSsl(xnio, OptionMap.create(Options.USE_DIRECT_BUFFERS, true), listener.sslContext, sslEngineDelegatedTaskExecutor);
                        } else {
                            OptionMap.Builder builder = OptionMap.builder()
                                    .addAll(socketOptionsWithOverrides);
                            if (!socketOptionsWithOverrides.contains(Options.SSL_PROTOCOL)) {
                                builder.set(Options.SSL_PROTOCOL, "TLSv1.2");
                            }
                            xnioSsl = new UndertowXnioSsl(
                                    xnio,
                                    OptionMap.create(Options.USE_DIRECT_BUFFERS, true),
                                    JsseSslUtils.createSSLContext(listener.keyManagers, listener.trustManagers, new SecureRandom(), builder.getMap()),
                                    sslEngineDelegatedTaskExecutor);
                        }

                        AcceptingChannel<? extends StreamConnection> sslServer;
                        if (listener.useProxyProtocol) {
                            ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(new ProxyProtocolOpenListener(openListener, xnioSsl, buffers, socketOptionsWithOverrides));
                            sslServer = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), (ChannelListener) acceptListener, socketOptionsWithOverrides);
                        } else {
                            ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                            sslServer = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), (ChannelListener) acceptListener, socketOptionsWithOverrides);
                        }

                        sslServer.resumeAccepts();
                        channels.add(sslServer);
                        listenerInfo.add(new ListenerInfo("https", sslServer.getLocalAddress(), openListener, xnioSsl, sslServer));
                    }
                }

            }

        } catch (Exception e) {
            if(internalWorker && worker != null) {
                worker.shutdownNow();
            }
            throw new RuntimeException(e);
        }
    }

    public synchronized void stop() {
        UndertowLogger.ROOT_LOGGER.infof("stopping server: %s", Version.getFullVersionString());
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
            Integer shutdownTimeoutMillis = serverOptions.get(UndertowOptions.SHUTDOWN_TIMEOUT);
            worker.shutdown();
            try {
                if (shutdownTimeoutMillis == null) {
                    worker.awaitTermination();
                } else {
                    if (!worker.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                        worker.shutdownNow();
                    }
                }
            } catch (InterruptedException e) {
                worker.shutdownNow();
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
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
        if (listenerInfo == null) {
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
        final OptionMap overrideSocketOptions;
        final boolean useProxyProtocol;

        private ListenerConfig(final ListenerType type, final int port, final String host, KeyManager[] keyManagers, TrustManager[] trustManagers, HttpHandler rootHandler) {
            this.type = type;
            this.port = port;
            this.host = host;
            this.keyManagers = keyManagers;
            this.trustManagers = trustManagers;
            this.rootHandler = rootHandler;
            this.sslContext = null;
            this.overrideSocketOptions = OptionMap.EMPTY;
            this.useProxyProtocol = false;
        }

        private ListenerConfig(final ListenerType type, final int port, final String host, SSLContext sslContext, HttpHandler rootHandler) {
            this.type = type;
            this.port = port;
            this.host = host;
            this.rootHandler = rootHandler;
            this.keyManagers = null;
            this.trustManagers = null;
            this.sslContext = sslContext;
            this.overrideSocketOptions = OptionMap.EMPTY;
            this.useProxyProtocol = false;
        }

        private ListenerConfig(final ListenerBuilder listenerBuilder) {
            this.type = listenerBuilder.type;
            this.port = listenerBuilder.port;
            this.host = listenerBuilder.host;
            this.rootHandler = listenerBuilder.rootHandler;
            this.keyManagers = listenerBuilder.keyManagers;
            this.trustManagers = listenerBuilder.trustManagers;
            this.sslContext = listenerBuilder.sslContext;
            this.overrideSocketOptions = listenerBuilder.overrideSocketOptions;
            this.useProxyProtocol = listenerBuilder.useProxyProtocol;
        }
    }

    public static final class ListenerBuilder {

        ListenerType type;
        int port;
        String host;
        KeyManager[] keyManagers;
        TrustManager[] trustManagers;
        SSLContext sslContext;
        HttpHandler rootHandler;
        OptionMap overrideSocketOptions = OptionMap.EMPTY;
        boolean useProxyProtocol;

        public ListenerBuilder setType(ListenerType type) {
            this.type = type;
            return this;
        }

        public ListenerBuilder setPort(int port) {
            this.port = port;
            return this;
        }

        public ListenerBuilder setHost(String host) {
            this.host = host;
            return this;
        }

        public ListenerBuilder setKeyManagers(KeyManager[] keyManagers) {
            this.keyManagers = keyManagers;
            return this;
        }

        public ListenerBuilder setTrustManagers(TrustManager[] trustManagers) {
            this.trustManagers = trustManagers;
            return this;
        }

        public ListenerBuilder setSslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public ListenerBuilder setRootHandler(HttpHandler rootHandler) {
            this.rootHandler = rootHandler;
            return this;
        }

        public ListenerBuilder setOverrideSocketOptions(OptionMap overrideSocketOptions) {
            this.overrideSocketOptions = overrideSocketOptions;
            return this;
        }

        public ListenerBuilder setUseProxyProtocol(boolean useProxyProtocol) {
            this.useProxyProtocol = useProxyProtocol;
            return this;
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
        private Executor sslEngineDelegatedTaskExecutor;
        private ByteBufferPool byteBufferPool;

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
                bufferSize = 1024 * 16 - 20; //the 20 is to allow some space for protocol headers, see UNDERTOW-1209
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

        public Builder addListener(ListenerBuilder listenerBuilder) {
            listeners.add(new ListenerConfig(listenerBuilder));
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
         * <br>
         * <p>
         * When non-null, the provided {@link XnioWorker} will be reused instead of creating a new {@link XnioWorker}
         * when {@link Undertow#start()} is called.
         * Additionally, the provided {@link XnioWorker} will NOT be shutdown when {@link Undertow#stop()} is called.
         * Essentially, the lifecycle of the provided worker must be maintained outside of the {@link Undertow} instance.
         */
        public Builder setWorker(XnioWorker worker) {
            this.worker = worker;
            return this;
        }

        public Builder setSslEngineDelegatedTaskExecutor(Executor sslEngineDelegatedTaskExecutor) {
            this.sslEngineDelegatedTaskExecutor = sslEngineDelegatedTaskExecutor;
            return this;
        }

        public Builder setByteBufferPool(ByteBufferPool byteBufferPool) {
            this.byteBufferPool = byteBufferPool;
            return this;
        }
    }

    public static class ListenerInfo {

        private final String protcol;
        private final SocketAddress address;
        private final OpenListener openListener;
        private final UndertowXnioSsl ssl;
        private final AcceptingChannel<? extends StreamConnection> channel;
        private volatile boolean suspended = false;

        public ListenerInfo(String protcol, SocketAddress address, OpenListener openListener, UndertowXnioSsl ssl, AcceptingChannel<? extends StreamConnection> channel) {
            this.protcol = protcol;
            this.address = address;
            this.openListener = openListener;
            this.ssl = ssl;
            this.channel = channel;
        }

        public String getProtcol() {
            return protcol;
        }

        public SocketAddress getAddress() {
            return address;
        }

        public SSLContext getSslContext() {
            if(ssl == null) {
                return null;
            }
            return ssl.getSslContext();
        }

        public void setSslContext(SSLContext sslContext) {
            if(ssl != null) {
                //just ignore it if this is not a SSL listener
                ssl.updateSSLContext(sslContext);
            }
        }

        public synchronized void suspend() {
            suspended = true;
            channel.suspendAccepts();
            CountDownLatch latch = new CountDownLatch(1);
            //the channel may be in the middle of an accept, we need to close from the IO thread
            channel.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        openListener.closeConnections();
                    } finally {
                        latch.countDown();
                    }
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public synchronized void resume() {
            suspended = false;
            channel.resumeAccepts();
        }

        public boolean isSuspended() {
            return suspended;
        }

        public ConnectorStatistics getConnectorStatistics() {
            return openListener.getConnectorStatistics();
        }

        public <T> void setSocketOption(Option<T>option, T value) throws IOException {
            channel.setOption(option, value);
        }

        public void setServerOptions(OptionMap options) {
            openListener.setUndertowOptions(options);
        }

        @Override
        public String toString() {
            return "ListenerInfo{" +
                    "protcol='" + protcol + '\'' +
                    ", address=" + address +
                    ", sslContext=" + getSslContext() +
                    '}';
        }
    }

}
