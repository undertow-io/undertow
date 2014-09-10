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

package io.undertow.server.handlers.proxy;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.CopyOnWriteMap;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.ssl.XnioSsl;

/**
 * A pool of connections to a target host.
 *
 * This pool can also be used to open connections in exclusive mode, in which case they will not be added to the connection pool.
 *
 * In this case the caller is responsible for closing any connections.
 *
 * @author Stuart Douglas
 */
public class ProxyConnectionPool implements Closeable {

    private final URI uri;

    private final InetSocketAddress bindAddress;

    private final XnioSsl ssl;

    private final UndertowClient client;

    private final ConnectionPoolManager connectionPoolManager;

    private final OptionMap options;

    /**
     * Set to true when the connection pool is closed.
     */
    private volatile boolean closed;

    private final int maxConnections;
    private final int maxCachedConnections;
    private final int sMaxConnections;
    private final int maxRequestQueueSize;
    private final long ttl;

    private final ConcurrentMap<XnioIoThread, HostThreadData> hostThreadData = new CopyOnWriteMap<>();

    public ProxyConnectionPool(ConnectionPoolManager connectionPoolManager, URI uri, UndertowClient client, OptionMap options) {
        this(connectionPoolManager, uri, null, client, options);
    }

    public ProxyConnectionPool(ConnectionPoolManager connectionPoolManager,InetSocketAddress bindAddress, URI uri, UndertowClient client, OptionMap options) {
        this(connectionPoolManager, bindAddress, uri, null, client, options);
    }

    public ProxyConnectionPool(ConnectionPoolManager connectionPoolManager, URI uri, XnioSsl ssl, UndertowClient client, OptionMap options) {
        this(connectionPoolManager, null, uri, ssl, client, options);
    }

    public ProxyConnectionPool(ConnectionPoolManager connectionPoolManager, InetSocketAddress bindAddress,URI uri, XnioSsl ssl, UndertowClient client, OptionMap options) {
        this.connectionPoolManager = connectionPoolManager;
        this.maxConnections = Math.max(connectionPoolManager.getMaxConnections(), 1);
        this.maxCachedConnections = Math.max(connectionPoolManager.getMaxCachedConnections(), 0);
        this.sMaxConnections = Math.max(connectionPoolManager.getSMaxConnections(), 0);
        this.maxRequestQueueSize = Math.max(connectionPoolManager.getMaxQueueSize(), 0);
        this.ttl = connectionPoolManager.getTtl();
        this.bindAddress = bindAddress;
        this.uri = uri;
        this.ssl = ssl;
        this.client = client;
        this.options = options;
    }

    public URI getUri() {
        return uri;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public void close() {
        this.closed = true;
        for (HostThreadData data : hostThreadData.values()) {
            final ConnectionHolder holder = data.availableConnections.poll();
            if (holder != null) {
                IoUtils.safeClose(holder.clientConnection);
            }
        }
    }

    /**
     * Called when the IO thread has completed a successful request
     *
     * @param connectionHolder The client connection holder
     */
    private void returnConnection(final ConnectionHolder connectionHolder) {
        HostThreadData hostData = getData();
        if (closed) {
            //the host has been closed
            IoUtils.safeClose(connectionHolder.clientConnection);
            ConnectionHolder con = hostData.availableConnections.poll();
            while (con != null) {
                IoUtils.safeClose(con.clientConnection);
                con = hostData.availableConnections.poll();
            }
            redistributeQueued(hostData);
            return;
        }

        //only do something if the connection is open. If it is closed then
        //the close setter will handle creating a new connection and decrementing
        //the connection count
        final ClientConnection connection = connectionHolder.clientConnection;
        if (connection.isOpen() && !connection.isUpgraded()) {
            CallbackHolder callback = hostData.awaitingConnections.poll();
            while (callback != null && callback.isCancelled()) {
                callback = hostData.awaitingConnections.poll();
            }
            if (callback != null) {
                if (callback.getTimeoutKey() != null) {
                    callback.getTimeoutKey().remove();
                }
                // Anything waiting for a connection is not expecting exclusivity.
                connectionReady(connectionHolder, callback.getCallback(), callback.getExchange(), false);
            } else {
                final int cachedConnectionCount = hostData.availableConnections.size();
                if (cachedConnectionCount >= maxCachedConnections) {
                    // Close the longest idle connection instead of the current one
                    final ConnectionHolder holder = hostData.availableConnections.poll();
                    if (holder != null) {
                        IoUtils.safeClose(holder.clientConnection);
                    }
                }
                hostData.availableConnections.add(connectionHolder);
                // If the soft max and ttl are configured
                if (sMaxConnections >= 0 && ttl > 0) {
                    final long currentTime = System.currentTimeMillis();
                    connectionHolder.timeout = currentTime + ttl;
                    timeoutConnections(currentTime, hostData);
                }
            }
        } else if (connection.isOpen() && connection.isUpgraded()) {
            //we treat upgraded connections as closed
            //as we do not want the connection pool filled with upgraded connections
            //if the connection is actually closed the close setter will handle it
            connection.getCloseSetter().set(null);
            handleClosedConnection(hostData, connectionHolder);
        }
    }

    private void handleClosedConnection(HostThreadData hostData, final ConnectionHolder connection) {

        int connections = --hostData.connections;
        hostData.availableConnections.remove(connection);
        if (connections < maxConnections) {
            CallbackHolder task = hostData.awaitingConnections.poll();
            while (task != null && task.isCancelled()) {
                task = hostData.awaitingConnections.poll();
            }
            if (task != null) {
                openConnection(task.exchange, task.callback, hostData, false);
            }
        }
    }

    private void openConnection(final HttpServerExchange exchange, final ProxyCallback<ProxyConnection> callback, final HostThreadData data, final boolean exclusive) {
        if (!exclusive) {
            data.connections++;
        }
        client.connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(final ClientConnection result) {
                final ConnectionHolder connectionHolder = new ConnectionHolder(result);
                if (!exclusive) {
                    result.getCloseSetter().set(new ChannelListener<ClientConnection>() {
                        @Override
                        public void handleEvent(ClientConnection channel) {
                            handleClosedConnection(data, connectionHolder);
                        }
                    });
                }
                connectionReady(connectionHolder, callback, exchange, exclusive);
            }

            @Override
            public void failed(IOException e) {
                if (!exclusive) {
                    data.connections--;
                }
                UndertowLogger.REQUEST_LOGGER.debug("Failed to connect", e);
                if (!connectionPoolManager.handleError()) {
                    redistributeQueued(getData());
                    scheduleFailedHostRetry(exchange);
                }
                callback.failed(exchange);
            }
        }, bindAddress, getUri(), exchange.getIoThread(), ssl, exchange.getConnection().getBufferPool(), options);
    }

    private void redistributeQueued(HostThreadData hostData) {
        CallbackHolder callback = hostData.awaitingConnections.poll();
        while (callback != null) {
            if (callback.getTimeoutKey() != null) {
                callback.getTimeoutKey().remove();
            }
            if (!callback.isCancelled()) {
                long time = System.currentTimeMillis();
                if (callback.getExpireTime() > 0 && callback.getExpireTime() < time) {
                    callback.getCallback().failed(callback.getExchange());
                } else {
                    callback.getCallback().queuedRequestFailed(callback.getExchange());
                }
            }
            callback = hostData.awaitingConnections.poll();
        }
    }

    private void connectionReady(final ConnectionHolder result, final ProxyCallback<ProxyConnection> callback, final HttpServerExchange exchange, final boolean exclusive) {
        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                if (!exclusive) {
                    returnConnection(result);
                }
                nextListener.proceed();
            }
        });

        callback.completed(exchange, new ProxyConnection(result.clientConnection, uri.getPath() == null ? "/" : uri.getPath()));
    }

    public AvailabilityType available() {
        if (closed) {
            return AvailabilityType.CLOSED;
        }
        if (!connectionPoolManager.isAvailable()) {
            return AvailabilityType.PROBLEM;
        }
        HostThreadData data = getData();
        if (data.connections < maxConnections) {
            return AvailabilityType.AVAILABLE;
        }
        if (!data.availableConnections.isEmpty()) {
            return AvailabilityType.AVAILABLE;
        }
        if (data.awaitingConnections.size() >= maxRequestQueueSize) {
            return AvailabilityType.FULL_QUEUE;
        }
        return AvailabilityType.FULL;
    }

    /**
     * If a host fails we periodically retry
     *
     * @param exchange The server exchange
     */
    private void scheduleFailedHostRetry(final HttpServerExchange exchange) {
        final int retry = connectionPoolManager.getProblemServerRetry();
        // only schedule a retry task if the node is not available
        if (retry > 0 && !connectionPoolManager.isAvailable()) {
            exchange.getIoThread().executeAfter(new Runnable() {
                @Override
                public void run() {
                    if (closed) {
                        return;
                    }

                    UndertowLogger.PROXY_REQUEST_LOGGER.debugf("Attempting to reconnect to failed host %s", getUri());
                    client.connect(new ClientCallback<ClientConnection>() {
                        @Override
                        public void completed(ClientConnection result) {
                            UndertowLogger.PROXY_REQUEST_LOGGER.debugf("Connected to previously failed host %s, returning to service", getUri());
                            if (connectionPoolManager.clearError()) {
                                // In case the node is available now, return the connection
                                final ConnectionHolder connectionHolder = new ConnectionHolder(result);
                                final HostThreadData data = getData();
                                result.getCloseSetter().set(new ChannelListener<ClientConnection>() {
                                    @Override
                                    public void handleEvent(ClientConnection channel) {
                                        handleClosedConnection(data, connectionHolder);
                                    }
                                });
                                data.connections++;
                                returnConnection(connectionHolder);
                            } else {
                                // Otherwise reschedule the retry task
                                scheduleFailedHostRetry(exchange);
                            }
                        }

                        @Override
                        public void failed(IOException e) {
                            UndertowLogger.PROXY_REQUEST_LOGGER.debugf("Failed to reconnect to failed host %s", getUri());
                            connectionPoolManager.handleError();
                            scheduleFailedHostRetry(exchange);
                        }
                    }, bindAddress, getUri(), exchange.getIoThread(), ssl, exchange.getConnection().getBufferPool(), options);
                }
            }, retry, TimeUnit.SECONDS);
        }
    }

    /**
     * Timeout idle connections which are above the soft max cached connections limit.
     *
     * @param currentTime    the current time
     * @param data           the local host thread data
     */
    private void timeoutConnections(final long currentTime, final HostThreadData data) {
        int idleConnections = data.availableConnections.size();
        for (;;) {
            ConnectionHolder holder;
            if (idleConnections > 0 && idleConnections >= sMaxConnections && (holder = data.availableConnections.peek()) != null) {
                if (!holder.clientConnection.isOpen()) {
                    // Already closed connections decrease the available connections
                    idleConnections--;
                } else if (currentTime >= holder.timeout) {
                    // If the timeout is reached already, just close
                    holder = data.availableConnections.poll();
                    IoUtils.safeClose(holder.clientConnection);
                    idleConnections--;
                } else {
                    // If the next run is after the connection timeout don't reschedule the task
                    if (data.timeoutKey == null || data.nextTimeout > holder.timeout) {
                        if (data.timeoutKey != null) {
                            data.timeoutKey.remove();
                            data.timeoutKey = null;
                        }
                        // Schedule a timeout task
                        final long remaining = holder.timeout - currentTime + 1;
                        data.nextTimeout = holder.timeout;
                        data.timeoutKey = holder.clientConnection.getIoThread().executeAfter(data.timeoutTask, remaining, TimeUnit.MILLISECONDS);
                    }
                    return;
                }
            } else {
                // If we are below the soft limit, just cancel the task
                if (data.timeoutKey != null) {
                    data.timeoutKey.remove();
                    data.timeoutKey = null;
                }
                return;
            }
        }
    }

    /**
     * Gets the host data for this thread
     *
     * @return The data for this thread
     */
    private HostThreadData getData() {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof XnioIoThread)) {
            throw UndertowMessages.MESSAGES.canOnlyBeCalledByIoThread();
        }
        XnioIoThread ioThread = (XnioIoThread) thread;
        HostThreadData data = hostThreadData.get(ioThread);
        if (data != null) {
            return data;
        }
        data = new HostThreadData();
        HostThreadData existing = hostThreadData.putIfAbsent(ioThread, data);
        if (existing != null) {
            return existing;
        }
        return data;
    }

    /**
     * @param exclusive - Is connection for the exclusive use of one client?
     */
    public void connect(ProxyClient.ProxyTarget proxyTarget, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, final long timeout, final TimeUnit timeUnit, boolean exclusive) {
        HostThreadData data = getData();
        ConnectionHolder connectionHolder = data.availableConnections.poll();
        while (connectionHolder != null && !connectionHolder.clientConnection.isOpen()) {
            connectionHolder = data.availableConnections.poll();
        }
        if (connectionHolder != null) {
            if (exclusive) {
                data.connections--;
            }
            connectionReady(connectionHolder, callback, exchange, exclusive);
        } else if (exclusive || data.connections < maxConnections) {
            openConnection(exchange, callback, data, exclusive);
        } else {
            // Reject the request directly if we reached the max request queue size
            if (data.awaitingConnections.size() >= maxRequestQueueSize) {
                callback.queuedRequestFailed(exchange);
                return;
            }
            CallbackHolder holder;
            if (timeout > 0) {
                long time = System.currentTimeMillis();
                holder = new CallbackHolder(proxyTarget, callback, exchange, time + timeUnit.toMillis(timeout));
                holder.setTimeoutKey(exchange.getIoThread().executeAfter(holder, timeout, timeUnit));
            } else {
                holder = new CallbackHolder(proxyTarget, callback, exchange, -1);
            }
            data.awaitingConnections.add(holder);
        }
    }

    private final class HostThreadData {

        int connections = 0;
        XnioIoThread.Key timeoutKey;
        long nextTimeout;

        final Deque<ConnectionHolder> availableConnections = new ArrayDeque<>();
        final Deque<CallbackHolder> awaitingConnections = new ArrayDeque<>();
        final Runnable timeoutTask = new Runnable() {
            @Override
            public void run() {
                final long currentTime = System.currentTimeMillis();
                timeoutConnections(currentTime, HostThreadData.this);
            }
        };

    }

    private static final class ConnectionHolder {

        private long timeout;
        private final ClientConnection clientConnection;

        private ConnectionHolder(ClientConnection clientConnection) {
            this.clientConnection = clientConnection;
        }

    }


    private static final class CallbackHolder implements Runnable {
        final ProxyClient.ProxyTarget proxyTarget;
        final ProxyCallback<ProxyConnection> callback;
        final HttpServerExchange exchange;
        final long expireTime;
        XnioExecutor.Key timeoutKey;
        boolean cancelled = false;

        private CallbackHolder(ProxyClient.ProxyTarget proxyTarget, ProxyCallback<ProxyConnection> callback, HttpServerExchange exchange, long expireTime) {
            this.proxyTarget = proxyTarget;
            this.callback = callback;
            this.exchange = exchange;
            this.expireTime = expireTime;
        }

        private ProxyCallback<ProxyConnection> getCallback() {
            return callback;
        }

        private HttpServerExchange getExchange() {
            return exchange;
        }

        private long getExpireTime() {
            return expireTime;
        }

        private XnioExecutor.Key getTimeoutKey() {
            return timeoutKey;
        }

        private boolean isCancelled() {
            return cancelled;
        }

        private void setTimeoutKey(XnioExecutor.Key timeoutKey) {
            this.timeoutKey = timeoutKey;
        }

        @Override
        public void run() {
            cancelled = true;
            callback.failed(exchange);
        }

        public ProxyClient.ProxyTarget getProxyTarget() {
            return proxyTarget;
        }
    }

    public enum AvailabilityType {
        /**
         * The host is read to accept requests
         */
        AVAILABLE,
        /**
         * The host is stopped. No request should be forwarded that are not tied
         * to this node via sticky sessions
         */
        DRAIN,
        /**
         * All connections are in use, connections will be queued
         */
        FULL,
        /**
         * All connections are in use and the queue is full. Requests will be rejected.
         */
        FULL_QUEUE,
        /**
         * The host is probably down, only try as a last resort
         */
        PROBLEM,
        /**
         * The host is closed. connections will always fail
         */
        CLOSED;
    }

}
