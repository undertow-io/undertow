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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.SameThreadExecutor;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.ssl.XnioSsl;

/**
 * Utilities to ping a remote node.
 *
 * @author Emanuel Muckenhuber
 */
class NodePingUtil {

    interface PingCallback {

        /**
         * Ping completed.
         */
        void completed();

        /**
         * Ping failed.
         */
        void failed();

    }

    private static final ClientRequest PING_REQUEST;

    static {
        final ClientRequest request = new ClientRequest();
        request.setMethod(Methods.OPTIONS);
        request.setPath("*");
        request.getRequestHeaders().add(Headers.USER_AGENT, "mod_cluster ping");
        PING_REQUEST = request;
    }

    /**
     * Try to open a socket connection to given address.
     *
     * @param address     the socket address
     * @param exchange    the http servers exchange
     * @param callback    the ping callback
     * @param options     the options
     */
    static void pingHost(InetSocketAddress address, HttpServerExchange exchange, PingCallback callback, OptionMap options) {

        final XnioIoThread thread = exchange.getIoThread();
        final XnioWorker worker = thread.getWorker();
        final HostPingTask r = new HostPingTask(address, worker, callback, options);
        // Schedule timeout task
        scheduleCancelTask(exchange.getIoThread(), r, 5, TimeUnit.SECONDS);
        exchange.dispatch(exchange.isInIoThread() ? SameThreadExecutor.INSTANCE : thread, r);
    }

    /**
     * Try to ping a server using the undertow client.
     *
     * @param connection    the connection URI
     * @param callback      the ping callback
     * @param exchange      the http servers exchange
     * @param client        the undertow client
     * @param xnioSsl       the ssl setup
     * @param options       the options
     */
    static void pingHttpClient(URI connection, PingCallback callback, HttpServerExchange exchange, UndertowClient client, XnioSsl xnioSsl, OptionMap options) {

        final XnioIoThread thread = exchange.getIoThread();
        final RequestExchangeListener exchangeListener = new RequestExchangeListener(callback, NodeHealthChecker.NO_CHECK, true);
        final Runnable r = new HttpClientPingTask(connection, exchangeListener, thread, client, xnioSsl, exchange.getConnection().getByteBufferPool(), options);
        exchange.dispatch(exchange.isInIoThread() ? SameThreadExecutor.INSTANCE : thread, r);
        // Schedule timeout task
        scheduleCancelTask(exchange.getIoThread(), exchangeListener, 5, TimeUnit.SECONDS);
    }

    /**
     * Try to ping a node using it's connection pool.
     *
     * @param node        the node
     * @param exchange    the http servers exchange
     * @param callback    the ping callback
     */
    static void pingNode(final Node node, final HttpServerExchange exchange, final PingCallback callback) {
        if (node == null) {
            callback.failed();
            return;
        }

        final int timeout = node.getNodeConfig().getPing();
        exchange.dispatch(exchange.isInIoThread() ? SameThreadExecutor.INSTANCE : exchange.getIoThread(), new Runnable() {
            @Override
            public void run() {
                node.getConnectionPool().connect(null, exchange, new ProxyCallback<ProxyConnection>() {
                    @Override
                    public void completed(final HttpServerExchange exchange, ProxyConnection result) {
                        final RequestExchangeListener exchangeListener = new RequestExchangeListener(callback, NodeHealthChecker.NO_CHECK, false);
                        exchange.dispatch(SameThreadExecutor.INSTANCE, new ConnectionPoolPingTask(result, exchangeListener));
                        // Schedule timeout task
                        scheduleCancelTask(exchange.getIoThread(), exchangeListener, timeout, TimeUnit.SECONDS);
                    }

                    @Override
                    public void failed(HttpServerExchange exchange) {
                        callback.failed();
                    }

                    @Override
                    public void queuedRequestFailed(HttpServerExchange exchange) {
                        callback.failed();
                    }

                    @Override
                    public void couldNotResolveBackend(HttpServerExchange exchange) {
                        callback.failed();
                    }

                }, timeout, TimeUnit.SECONDS, false);
            }
        });
    }

    /**
     * Internally ping a node. This should probably use the connections from the nodes pool, if there are any available.
     *
     * @param node          the node
     * @param callback      the ping callback
     * @param ioThread      the xnio i/o thread
     * @param bufferPool    the xnio buffer pool
     * @param client        the undertow client
     * @param xnioSsl       the ssl setup
     * @param options       the options
     */
    static void internalPingNode(Node node, PingCallback callback, NodeHealthChecker healthChecker, XnioIoThread ioThread, ByteBufferPool bufferPool, UndertowClient client, XnioSsl xnioSsl, OptionMap options) {

        final URI uri = node.getNodeConfig().getConnectionURI();
        final long timeout = node.getNodeConfig().getPing();
        final RequestExchangeListener exchangeListener = new RequestExchangeListener(callback, healthChecker, true);
        final HttpClientPingTask r = new HttpClientPingTask(uri, exchangeListener, ioThread, client, xnioSsl, bufferPool, options);
        // Schedule timeout task
        scheduleCancelTask(ioThread, exchangeListener, timeout, TimeUnit.SECONDS);
        ioThread.execute(r);
    }

    static class ConnectionPoolPingTask implements Runnable {

        private final RequestExchangeListener exchangeListener;
        private final ProxyConnection proxyConnection;

        ConnectionPoolPingTask(ProxyConnection proxyConnection, RequestExchangeListener exchangeListener) {
            this.proxyConnection = proxyConnection;
            this.exchangeListener = exchangeListener;
        }

        @Override
        public void run() {

            // TODO AJP has a special ping thing
            proxyConnection.getConnection().sendRequest(PING_REQUEST, new ClientCallback<ClientExchange>() {
                @Override
                public void completed(final ClientExchange result) {
                    if (exchangeListener.isDone()) {
                        IoUtils.safeClose(proxyConnection.getConnection());
                        return;
                    }
                    exchangeListener.exchange = result;
                    result.setResponseListener(exchangeListener);
                    try {
                        result.getRequestChannel().shutdownWrites();
                        if (!result.getRequestChannel().flush()) {
                            result.getRequestChannel().getWriteSetter().set(ChannelListeners.flushingChannelListener(null, new ChannelExceptionHandler<StreamSinkChannel>() {
                                @Override
                                public void handleException(StreamSinkChannel channel, IOException exception) {
                                    IoUtils.safeClose(proxyConnection.getConnection());
                                    exchangeListener.taskFailed();
                                }
                            }));
                            result.getRequestChannel().resumeWrites();
                        }
                    } catch (IOException e) {
                        IoUtils.safeClose(proxyConnection.getConnection());
                        exchangeListener.taskFailed();
                    }
                }

                @Override
                public void failed(IOException e) {
                    exchangeListener.taskFailed();
                }
            });
        }

    }

    static class HostPingTask extends CancellableTask implements Runnable {

        private final InetSocketAddress address;
        private final XnioWorker worker;
        private final OptionMap options;

        HostPingTask(InetSocketAddress address, XnioWorker worker, PingCallback callback, OptionMap options) {
            super(callback);
            this.address = address;
            this.worker = worker;
            this.options = options;
        }

        @Override
        public void run() {
            try {
                final IoFuture<StreamConnection> future = worker.openStreamConnection(address, new ChannelListener<StreamConnection>() {
                    @Override
                    public void handleEvent(StreamConnection channel) {
                        IoUtils.safeClose(channel); // Close the channel right away
                    }
                }, options);

                future.addNotifier(new IoFuture.HandlingNotifier<StreamConnection, Void>() {

                    @Override
                    public void handleCancelled(Void attachment) {
                        cancel();
                    }

                    @Override
                    public void handleFailed(IOException exception, Void attachment) {
                        taskFailed();
                    }

                    @Override
                    public void handleDone(StreamConnection data, Void attachment) {
                        taskCompleted();
                    }
                }, null);

            } catch (Exception e) {
                taskFailed();
            }
        }

    }

    static class HttpClientPingTask implements Runnable {

        private final URI connection;
        private final XnioIoThread thread;
        private final UndertowClient client;
        private final XnioSsl xnioSsl;
        private final ByteBufferPool bufferPool;
        private final OptionMap options;
        private final RequestExchangeListener exchangeListener;

        HttpClientPingTask(URI connection, RequestExchangeListener exchangeListener, XnioIoThread thread, UndertowClient client, XnioSsl xnioSsl, ByteBufferPool bufferPool, OptionMap options) {
            this.connection = connection;
            this.thread = thread;
            this.client = client;
            this.xnioSsl = xnioSsl;
            this.bufferPool = bufferPool;
            this.options = options;
            this.exchangeListener = exchangeListener;
        }

        @Override
        public void run() {

            UndertowLogger.ROOT_LOGGER.httpClientPingTask(connection);
            // TODO AJP has a special ping thing
            client.connect(new ClientCallback<ClientConnection>() {
                @Override
                public void completed(final ClientConnection clientConnection) {
                    if (exchangeListener.isDone()) {
                        IoUtils.safeClose(clientConnection);
                        return;
                    }
                    clientConnection.sendRequest(PING_REQUEST, new ClientCallback<ClientExchange>() {

                        @Override
                        public void completed(ClientExchange result) {
                            exchangeListener.exchange = result;
                            if (exchangeListener.isDone()) {
                                return;
                            }
                            result.setResponseListener(exchangeListener);
                            try {
                                result.getRequestChannel().shutdownWrites();
                                if (!result.getRequestChannel().flush()) {
                                    result.getRequestChannel().getWriteSetter().set(ChannelListeners.flushingChannelListener(null, new ChannelExceptionHandler<StreamSinkChannel>() {
                                        @Override
                                        public void handleException(StreamSinkChannel channel, IOException exception) {
                                            IoUtils.safeClose(clientConnection);
                                            exchangeListener.taskFailed();
                                        }
                                    }));
                                    result.getRequestChannel().resumeWrites();
                                }
                            } catch (IOException e) {
                                IoUtils.safeClose(clientConnection);
                                exchangeListener.taskFailed();
                            }
                        }

                        @Override
                        public void failed(IOException e) {
                            exchangeListener.taskFailed();
                            IoUtils.safeClose(clientConnection);
                        }
                    });
                }

                @Override
                public void failed(IOException e) {
                    exchangeListener.taskFailed();
                }
            }, connection, thread, xnioSsl, bufferPool, options);

        }
    }

    static class RequestExchangeListener extends CancellableTask implements ClientCallback<ClientExchange> {

        private ClientExchange exchange;
        private final boolean closeConnection;
        private final NodeHealthChecker healthChecker;

        RequestExchangeListener(PingCallback callback, NodeHealthChecker healthChecker, boolean closeConnection) {
            super(callback);
            assert healthChecker != null;
            this.closeConnection = closeConnection;
            this.healthChecker = healthChecker;
        }

        @Override
        public void completed(final ClientExchange result) {
            if (isDone()) {
                IoUtils.safeClose(result.getConnection());
                return;
            }
            final ChannelListener<StreamSourceChannel> listener = ChannelListeners.drainListener(Long.MAX_VALUE, new ChannelListener<StreamSourceChannel>() {
                @Override
                public void handleEvent(StreamSourceChannel channel) {
                    try {
                        if (healthChecker.checkResponse(result.getResponse())) {
                            taskCompleted();
                        } else {
                            taskFailed();
                        }
                    } finally {
                        if (closeConnection) {
                            if (exchange != null) {
                                IoUtils.safeClose(exchange.getConnection());
                            }
                        }
                    }
                }
            }, new ChannelExceptionHandler<StreamSourceChannel>() {
                @Override
                public void handleException(StreamSourceChannel channel, IOException exception) {
                    taskFailed();
                    if (exception != null) {
                        IoUtils.safeClose(exchange.getConnection());
                    }
                }
            });
            StreamSourceChannel responseChannel = result.getResponseChannel();
            responseChannel.getReadSetter().set(listener);
            responseChannel.resumeReads();
            listener.handleEvent(responseChannel);
        }

        @Override
        public void failed(IOException e) {
            taskFailed();
            if (exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
        }
    }

    enum State {
        WAITING, DONE, CANCELLED;
    }

    static class CancellableTask {

        private final PingCallback delegate;
        private volatile State state = State.WAITING;
        private volatile XnioExecutor.Key cancelKey;

        CancellableTask(PingCallback callback) {
            this.delegate = callback;
        }

        boolean isDone() {
            return state != State.WAITING;
        }

        void setCancelKey(XnioExecutor.Key cancelKey) {
            if (state == State.WAITING) {
                this.cancelKey = cancelKey;
            } else {
                cancelKey.remove();
            }
        }

        void taskCompleted() {
            if (state == State.WAITING) {
                state = State.DONE;
                if (cancelKey != null) {
                    cancelKey.remove();
                }
                delegate.completed();
            }
        }

        void taskFailed() {
            if (state == State.WAITING) {
                state = State.DONE;
                if (cancelKey != null) {
                    cancelKey.remove();
                }
                delegate.failed();
            }
        }

        void cancel() {
            if (state == State.WAITING) {
                state = State.CANCELLED;
                if (cancelKey != null) {
                    cancelKey.remove();
                }
                delegate.failed();
            }
        }

    }

    static void scheduleCancelTask(final XnioIoThread ioThread, final CancellableTask cancellable, final long timeout, final TimeUnit timeUnit ) {
        final XnioExecutor.Key key = ioThread.executeAfter(new Runnable() {
            @Override
            public void run() {
                cancellable.cancel();
            }
        }, timeout, timeUnit);
        cancellable.setCancelKey(key);
    }

}
