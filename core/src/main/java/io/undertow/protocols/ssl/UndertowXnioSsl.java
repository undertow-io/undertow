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

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import io.undertow.UndertowOptions;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.SslClientAuthMode;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.AssembledConnectedSslStreamChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.ssl.JsseSslUtils;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.SslConnection;
import org.xnio.ssl.XnioSsl;

import static org.xnio.IoUtils.safeClose;

/**
 * @author Stuart Douglas
 */
public class UndertowXnioSsl extends XnioSsl {

    private static final ByteBufferPool DEFAULT_BUFFER_POOL = new DefaultByteBufferPool(true, 17 * 1024, -1, 12);

    private final ByteBufferPool bufferPool;
    private final Executor delegatedTaskExecutor;
    private volatile SSLContext sslContext;

    /**
     * Construct a new instance.
     *
     * @param xnio the XNIO instance to associate with
     * @param optionMap the options for this provider
     * @throws java.security.NoSuchProviderException if the given SSL provider is not found
     * @throws java.security.NoSuchAlgorithmException if the given SSL algorithm is not supported
     * @throws java.security.KeyManagementException if the SSL context could not be initialized
     */
    public UndertowXnioSsl(final Xnio xnio, final OptionMap optionMap) throws NoSuchProviderException, NoSuchAlgorithmException, KeyManagementException {
        this(xnio, optionMap, DEFAULT_BUFFER_POOL, JsseSslUtils.createSSLContext(optionMap));
    }

    /**
     * Construct a new instance.
     * @param xnio the XNIO instance to associate with
     * @param optionMap the options for this provider
     * @param sslContext the SSL context to use for this instance
     */
    public UndertowXnioSsl(final Xnio xnio, final OptionMap optionMap, final SSLContext sslContext) {
        this(xnio, optionMap, DEFAULT_BUFFER_POOL, sslContext);
    }

    /**
     * Construct a new instance.
     * @param xnio the XNIO instance to associate with
     * @param optionMap the options for this provider
     * @param sslContext the SSL context to use for this instance
     * @param delegatedTaskExecutor Executor instance used to run {@link SSLEngine#getDelegatedTask() delegated tasks}.
     */
    public UndertowXnioSsl(final Xnio xnio, final OptionMap optionMap, final SSLContext sslContext, final Executor delegatedTaskExecutor) {
        this(xnio, optionMap, DEFAULT_BUFFER_POOL, sslContext, delegatedTaskExecutor);
    }

    /**
     * Construct a new instance.
     *
     * @param xnio the XNIO instance to associate with
     * @param optionMap the options for this provider
     * @param bufferPool
     * @throws java.security.NoSuchProviderException if the given SSL provider is not found
     * @throws java.security.NoSuchAlgorithmException if the given SSL algorithm is not supported
     * @throws java.security.KeyManagementException if the SSL context could not be initialized
     */
    public UndertowXnioSsl(final Xnio xnio, final OptionMap optionMap, ByteBufferPool bufferPool) throws NoSuchProviderException, NoSuchAlgorithmException, KeyManagementException {
        this(xnio, optionMap, bufferPool, JsseSslUtils.createSSLContext(optionMap));
    }

    /**
     * Construct a new instance.
     *  @param xnio the XNIO instance to associate with
     * @param optionMap the options for this provider
     * @param bufferPool
     * @param sslContext the SSL context to use for this instance
     */
    public UndertowXnioSsl(final Xnio xnio, final OptionMap optionMap, ByteBufferPool bufferPool, final SSLContext sslContext) {
        this(xnio, optionMap, bufferPool, sslContext, null);
    }

    /**
     * Construct a new instance.
     *  @param xnio the XNIO instance to associate with
     * @param optionMap the options for this provider
     * @param bufferPool
     * @param sslContext the SSL context to use for this instance
     * @param delegatedTaskExecutor Executor instance used to run {@link SSLEngine#getDelegatedTask() delegated tasks}.
     */
    public UndertowXnioSsl(final Xnio xnio, final OptionMap optionMap, ByteBufferPool bufferPool, final SSLContext sslContext, final Executor delegatedTaskExecutor) {
        super(xnio, sslContext, optionMap);
        this.bufferPool = bufferPool;
        this.sslContext = sslContext;
        this.delegatedTaskExecutor = delegatedTaskExecutor;
    }

    /**
     * Get the JSSE SSL context for this provider instance.
     *
     * @return the SSL context
     */
    @SuppressWarnings("unused")
    public SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * Get the {@link Executor} used to run delegated tasks or {@code null} if no executor is configured.
     *
     * @return the delegated task executor or null
     */
    Executor getDelegatedTaskExecutor() {
        return delegatedTaskExecutor;
    }

    /**
     * Get the SSL engine for a given connection.
     *
     * @return the SSL engine
     */
    public static SSLEngine getSslEngine(SslConnection connection) {
        if (connection instanceof UndertowSslConnection) {
            return ((UndertowSslConnection) connection).getSSLEngine();
        } else {
            return JsseXnioSsl.getSslEngine(connection);
        }
    }

    public static SslConduit getSslConduit(SslConnection connection) {
        return ((UndertowSslConnection) connection).getSslConduit();
    }

    @SuppressWarnings("deprecation")
    public IoFuture<ConnectedSslStreamChannel> connectSsl(final XnioWorker worker, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super ConnectedSslStreamChannel> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<ConnectedSslStreamChannel> futureResult = new FutureResult<>(IoUtils.directExecutor());
        final IoFuture<SslConnection> futureSslConnection = openSslConnection(worker, bindAddress, destination, new ChannelListener<SslConnection>() {
            public void handleEvent(final SslConnection sslConnection) {
                final ConnectedSslStreamChannel assembledChannel = new AssembledConnectedSslStreamChannel(sslConnection, sslConnection.getSourceChannel(), sslConnection.getSinkChannel());
                if (!futureResult.setResult(assembledChannel)) {
                    safeClose(assembledChannel);
                } else {
                    ChannelListeners.invokeChannelListener(assembledChannel, openListener);
                }
            }
        }, bindListener, optionMap).addNotifier(new IoFuture.HandlingNotifier<SslConnection, FutureResult<ConnectedSslStreamChannel>>() {
            public void handleCancelled(final FutureResult<ConnectedSslStreamChannel> result) {
                result.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<ConnectedSslStreamChannel> result) {
                result.setException(exception);
            }
        }, futureResult);
        futureResult.getIoFuture().addNotifier(new IoFuture.HandlingNotifier<ConnectedStreamChannel, IoFuture<SslConnection>>() {
            public void handleCancelled(final IoFuture<SslConnection> result) {
                result.cancel();
            }
        }, futureSslConnection);
        futureResult.addCancelHandler(futureSslConnection);
        return futureResult.getIoFuture();
    }

    public IoFuture<SslConnection> openSslConnection(final XnioWorker worker, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super SslConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<SslConnection> futureResult = new FutureResult<>(worker);
        final IoFuture<StreamConnection> connection = worker.openStreamConnection(bindAddress, destination, new StreamConnectionChannelListener(optionMap, destination, futureResult, openListener), bindListener, optionMap);
        return setupSslConnection(futureResult, connection);
    }
    @Override
    public IoFuture<SslConnection> openSslConnection(final XnioIoThread ioThread, final InetSocketAddress bindAddress, final InetSocketAddress destination, final ChannelListener<? super SslConnection> openListener, final ChannelListener<? super BoundChannel> bindListener, final OptionMap optionMap) {
        final FutureResult<SslConnection> futureResult = new FutureResult<>(ioThread);
        final IoFuture<StreamConnection> connection = ioThread.openStreamConnection(bindAddress, destination, new StreamConnectionChannelListener(optionMap, destination, futureResult, openListener), bindListener, optionMap);
        return setupSslConnection(futureResult, connection);
    }

    public SslConnection wrapExistingConnection(StreamConnection connection, OptionMap optionMap) {
        return new UndertowSslConnection(connection, createSSLEngine(sslContext, optionMap, (InetSocketAddress) connection.getPeerAddress(), true), bufferPool, delegatedTaskExecutor);
    }

    public SslConnection wrapExistingConnection(StreamConnection connection, OptionMap optionMap, boolean clientMode) {
        return new UndertowSslConnection(connection, createSSLEngine(sslContext, optionMap, (InetSocketAddress) connection.getPeerAddress(), clientMode), bufferPool, delegatedTaskExecutor);
    }

    public SslConnection wrapExistingConnection(StreamConnection connection, OptionMap optionMap, URI destinationURI) {
        SSLEngine sslEngine = createSSLEngine(sslContext, optionMap, getPeerAddress(destinationURI), true);
        SSLParameters sslParameters = sslEngine.getSSLParameters();
        if (sslParameters.getServerNames() == null || sslParameters.getServerNames().isEmpty()) {
            sslParameters.setServerNames(Collections.singletonList(new SNIHostName(destinationURI.getHost())));
            sslEngine.setSSLParameters(sslParameters);
        }
        return new UndertowSslConnection(connection, sslEngine, bufferPool, delegatedTaskExecutor);
    }

    private InetSocketAddress getPeerAddress(URI destinationURI) {
        String hostname = destinationURI.getHost();
        int port = destinationURI.getPort();
        if (port == -1) {
            port = destinationURI.getScheme().equals("wss") ? 443 : 80;
        }
        return new InetSocketAddress(hostname, port);
    }

    /**
     * Create a new  SSL engine, configured from an option map.
     *
     * @param sslContext the SSL context
     * @param optionMap the SSL options
     * @param peerAddress the peer address of the connection
     * @param client whether this SSL connection is run in client mode
     * @return the configured SSL engine
     */
    private static SSLEngine createSSLEngine(SSLContext sslContext, OptionMap optionMap, InetSocketAddress peerAddress, boolean client) {
        final SSLEngine engine = sslContext.createSSLEngine(
                optionMap.get(Options.SSL_PEER_HOST_NAME, peerAddress.getHostString()),
                optionMap.get(Options.SSL_PEER_PORT, peerAddress.getPort())
        );
        engine.setUseClientMode(client);
        engine.setEnableSessionCreation(optionMap.get(Options.SSL_ENABLE_SESSION_CREATION, true));
        final Sequence<String> cipherSuites = optionMap.get(Options.SSL_ENABLED_CIPHER_SUITES);
        if (cipherSuites != null) {
            final Set<String> supported = new HashSet<>(Arrays.asList(engine.getSupportedCipherSuites()));
            final List<String> finalList = new ArrayList<>();
            for (String name : cipherSuites) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledCipherSuites(finalList.toArray(new String[finalList.size()]));
        }
        final Sequence<String> protocols = optionMap.get(Options.SSL_ENABLED_PROTOCOLS);
        if (protocols != null) {
            final Set<String> supported = new HashSet<>(Arrays.asList(engine.getSupportedProtocols()));
            final List<String> finalList = new ArrayList<>();
            for (String name : protocols) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledProtocols(finalList.toArray(new String[finalList.size()]));
        }
        if (!client) {
            final SslClientAuthMode clientAuthMode = optionMap.get(Options.SSL_CLIENT_AUTH_MODE);
            if (clientAuthMode != null) {
                switch (clientAuthMode) {
                    case NOT_REQUESTED:
                        engine.setNeedClientAuth(false);
                        engine.setWantClientAuth(false);
                        break;
                    case REQUESTED:
                        engine.setWantClientAuth(true);
                        break;
                    case REQUIRED:
                        engine.setNeedClientAuth(true);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        }
        boolean useCipherSuitesOrder = optionMap.get(UndertowOptions.SSL_USER_CIPHER_SUITES_ORDER, false);
        if (useCipherSuitesOrder) {
            SSLParameters sslParameters = engine.getSSLParameters();
            sslParameters.setUseCipherSuitesOrder(true);
            engine.setSSLParameters(sslParameters);
        }
        final String endpointIdentificationAlgorithm = optionMap.get(UndertowOptions.ENDPOINT_IDENTIFICATION_ALGORITHM, null);
        if (endpointIdentificationAlgorithm != null) {
            SSLParameters sslParameters = engine.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(endpointIdentificationAlgorithm);
            engine.setSSLParameters(sslParameters);
        }
        return engine;
    }

    private IoFuture<SslConnection> setupSslConnection(FutureResult<SslConnection> futureResult, IoFuture<StreamConnection> connection) {
        connection.addNotifier(new IoFuture.HandlingNotifier<StreamConnection, FutureResult<SslConnection>>() {
            public void handleCancelled(final FutureResult<SslConnection> attachment) {
                attachment.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<SslConnection> attachment) {
                attachment.setException(exception);
            }
        }, futureResult);
        futureResult.addCancelHandler(connection);
        return futureResult.getIoFuture();
    }

    @SuppressWarnings("deprecation")
    public AcceptingChannel<ConnectedSslStreamChannel> createSslTcpServer(final XnioWorker worker, final InetSocketAddress bindAddress, final ChannelListener<? super AcceptingChannel<ConnectedSslStreamChannel>> acceptListener, final OptionMap optionMap) throws IOException {
        final AcceptingChannel<SslConnection> server = createSslConnectionServer(worker, bindAddress, null, optionMap);
        final AcceptingChannel<ConnectedSslStreamChannel> acceptingChannel = new AcceptingChannel<ConnectedSslStreamChannel>() {
            public ConnectedSslStreamChannel accept() throws IOException {
                final SslConnection connection = server.accept();
                return connection == null ? null : new AssembledConnectedSslStreamChannel(connection, connection.getSourceChannel(), connection.getSinkChannel());
            }

            public ChannelListener.Setter<? extends AcceptingChannel<ConnectedSslStreamChannel>> getAcceptSetter() {
                return ChannelListeners.getDelegatingSetter(server.getAcceptSetter(), this);
            }

            public ChannelListener.Setter<? extends AcceptingChannel<ConnectedSslStreamChannel>> getCloseSetter() {
                return ChannelListeners.getDelegatingSetter(server.getCloseSetter(), this);
            }

            public SocketAddress getLocalAddress() {
                return server.getLocalAddress();
            }

            public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
                return server.getLocalAddress(type);
            }

            public void suspendAccepts() {
                server.suspendAccepts();
            }

            public void resumeAccepts() {
                server.resumeAccepts();
            }

            public boolean isAcceptResumed() {
                return server.isAcceptResumed();
            }

            public void wakeupAccepts() {
                server.wakeupAccepts();
            }

            public void awaitAcceptable() throws IOException {
                server.awaitAcceptable();
            }

            public void awaitAcceptable(final long time, final TimeUnit timeUnit) throws IOException {
                server.awaitAcceptable(time, timeUnit);
            }

            public XnioWorker getWorker() {
                return server.getWorker();
            }

            @Deprecated
            public XnioExecutor getAcceptThread() {
                return server.getAcceptThread();
            }

            public XnioIoThread getIoThread() {
                return server.getIoThread();
            }

            public void close() throws IOException {
                server.close();
            }

            public boolean isOpen() {
                return server.isOpen();
            }

            public boolean supportsOption(final Option<?> option) {
                return server.supportsOption(option);
            }

            public <T> T getOption(final Option<T> option) throws IOException {
                return server.getOption(option);
            }

            public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
                return server.setOption(option, value);
            }
        };
        acceptingChannel.getAcceptSetter().set(acceptListener);
        return acceptingChannel;
    }

    /**
     * Updates the SSLContext that is in use. All new connections will use this new context, however established connections
     * will not be affected.
     *
     * @param context The new context
     */
    public void updateSSLContext(SSLContext context) {
        this.sslContext = context;
    }

    public AcceptingChannel<SslConnection> createSslConnectionServer(final XnioWorker worker, final InetSocketAddress bindAddress, final ChannelListener<? super AcceptingChannel<SslConnection>> acceptListener, final OptionMap optionMap) throws IOException {
        final UndertowAcceptingSslChannel server = new UndertowAcceptingSslChannel(this, worker.createStreamConnectionServer(bindAddress,  null,  optionMap), optionMap, bufferPool, false);
        if (acceptListener != null) server.getAcceptSetter().set(acceptListener);
        return server;
    }

    private class StreamConnectionChannelListener implements ChannelListener<StreamConnection> {
        private final OptionMap optionMap;
        private final InetSocketAddress destination;
        private final FutureResult<SslConnection> futureResult;
        private final ChannelListener<? super SslConnection> openListener;

        StreamConnectionChannelListener(OptionMap optionMap, InetSocketAddress destination, FutureResult<SslConnection> futureResult, ChannelListener<? super SslConnection> openListener) {
            this.optionMap = optionMap;
            this.destination = destination;
            this.futureResult = futureResult;
            this.openListener = openListener;
        }

        public void handleEvent(final StreamConnection connection) {
            try {

                SSLEngine sslEngine = JsseSslUtils.createSSLEngine(sslContext, optionMap, destination);
                SSLParameters params = sslEngine.getSSLParameters();
                InetAddress address = destination.getAddress();
                String hostnameValue = destination.getHostString();
                if (address instanceof Inet6Address && hostnameValue.contains(":")) {
                    // WFLY-13748 get hostname value instead of IPV6adress if it's ipv6
                    // SNIHostname throw exception if adress contains :
                    hostnameValue = address.getHostName();
                }
                params.setServerNames(Collections.singletonList(new SNIHostName(hostnameValue)));
                final String endpointIdentificationAlgorithm = optionMap.get(UndertowOptions.ENDPOINT_IDENTIFICATION_ALGORITHM, null);
                if (endpointIdentificationAlgorithm != null) {
                    params.setEndpointIdentificationAlgorithm(endpointIdentificationAlgorithm);
                }

                sslEngine.setSSLParameters(params);

                final SslConnection wrappedConnection = new UndertowSslConnection(connection, sslEngine, bufferPool, delegatedTaskExecutor);
                if (!futureResult.setResult(wrappedConnection)) {
                    IoUtils.safeClose(connection);
                } else {
                    ChannelListeners.invokeChannelListener(wrappedConnection, openListener);
                }
            } catch (Throwable e) {
                futureResult.setException(new IOException(e));
            }
        }
    }
}
