package io.undertow.server.handlers.proxy;

import io.undertow.UndertowMessages;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
class Host {

    private final LoadBalancingProxyClient loadBalancingProxyClient;

    private final URI uri;

    private final String jvmRoute;


    private final UndertowClient client;

    /**
     * flag that is set when a problem is detected with this host. It will be taken out of consideration
     * until the flag is cleared.
     * <p/>
     * The exception to this is if all flags are marked as problems, in which case it will be tried anyway
     */
    private volatile boolean problem;

    /**
     * Set to true when the host is removed from this load balancer
     */
    private volatile boolean closed;

    private final ConcurrentMap<XnioIoThread, HostThreadData> hostThreadData = new ConcurrentHashMap<XnioIoThread, HostThreadData>();

    public Host(LoadBalancingProxyClient loadBalancingProxyClient, URI uri, String jvmRoute, UndertowClient client) {
        this.loadBalancingProxyClient = loadBalancingProxyClient;
        this.uri = uri;
        this.jvmRoute = jvmRoute;
        this.client = client;
    }

    URI getUri() {
        return uri;
    }

    void close() {
        this.closed = true;
    }

    /**
     * Called when the IO thread has completed a successful request
     *
     * @param connection The client connection
     */
    void returnConnection(final ClientConnection connection) {
        HostThreadData hostData = getData();
        if (closed) {
            //the host has been closed
            IoUtils.safeClose(connection);
            ClientConnection con = hostData.availbleConnections.poll();
            while (con != null) {
                IoUtils.safeClose(con);
                con = hostData.availbleConnections.poll();
            }
            redistributeQueued(hostData);
            return;
        }

        //only do something if the connection is open. If it is closed then
        //the close setter will handle creating a new connection and decrementing
        //the connection count
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
                connectionReady(connection, callback.getCallback(), callback.getExchange(), false);
            } else {
                hostData.availbleConnections.add(connection);
            }
        } else if(connection.isOpen() && connection.isUpgraded()) {
            //we treat upgraded connections as closed
            //as we do not want the connection pool filled with upgraded connections
            //if the connection is actually closed the close setter will handle it
            connection.getCloseSetter().set(null);
            handleClosedConnection(hostData, connection);
        }
    }

    private void handleClosedConnection(HostThreadData hostData, final ClientConnection connection) {

        int connections = --hostData.connections;
        hostData.availbleConnections.remove(connection);
        if (connections < loadBalancingProxyClient.getConnectionsPerThread()) {
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
        if (exclusive == false) {
            data.connections++;
        }
        client.connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(final ClientConnection result) {
                problem = false;
                if (exclusive == false) {
                    result.getCloseSetter().set(new ChannelListener<ClientConnection>() {
                        @Override
                        public void handleEvent(ClientConnection channel) {
                            handleClosedConnection(data, channel);
                        }
                    });
                }
                connectionReady(result, callback, exchange, exclusive);
            }

            @Override
            public void failed(IOException e) {
                if (exclusive == false) {
                    data.connections--;
                }
                problem = true;
                redistributeQueued(getData());
                scheduleFailedHostRetry(exchange);
                callback.failed(exchange);
            }
        }, getUri(), exchange.getIoThread(), exchange.getConnection().getBufferPool(), OptionMap.EMPTY);
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
                    loadBalancingProxyClient.getConnection(callback.getProxyTarget(), callback.getExchange(), callback.getCallback(), callback.getExpireTime() > 0 ? time - callback.getExpireTime() : -1, TimeUnit.MILLISECONDS);
                    callback.getCallback().failed(callback.getExchange());
                }
            }
            callback = hostData.awaitingConnections.poll();
        }
    }

    private void connectionReady(final ClientConnection result, final ProxyCallback<ProxyConnection> callback, final HttpServerExchange exchange, final boolean exclusive) {
        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                if (exclusive == false) {
                    returnConnection(result);
                }
                nextListener.proceed();
            }
        });

        callback.completed(exchange, new ProxyConnection(result, uri.getPath() == null ? "/" : uri.getPath()));
    }

    AvailabilityType availible() {
        if (closed) {
            return AvailabilityType.CLOSED;
        }
        if (problem) {
            return AvailabilityType.PROBLEM;
        }
        HostThreadData data = getData();
        if (data.connections < loadBalancingProxyClient.getConnectionsPerThread()) {
            return AvailabilityType.AVAILABLE;
        }
        if (!data.availbleConnections.isEmpty()) {
            return AvailabilityType.AVAILABLE;
        }
        return AvailabilityType.FULL;
    }

    String getJvmRoute() {
        return jvmRoute;
    }

    /**
     * If a host fails we periodically retry
     *
     * @param exchange The server exchange
     */
    private void scheduleFailedHostRetry(final HttpServerExchange exchange) {
        exchange.getIoThread().executeAfter(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    return;
                }
                client.connect(new ClientCallback<ClientConnection>() {
                    @Override
                    public void completed(ClientConnection result) {
                        problem = false;
                        returnConnection(result);
                    }

                    @Override
                    public void failed(IOException e) {
                        scheduleFailedHostRetry(exchange);
                    }
                }, getUri(), exchange.getIoThread(), exchange.getConnection().getBufferPool(), OptionMap.EMPTY);
            }
        }, loadBalancingProxyClient.getProblemServerRetry(), TimeUnit.SECONDS);
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
     *
     * @param exclusive - Is connection for the exclusive use of one client?
     */
    public void connect(ProxyClient.ProxyTarget proxyTarget, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, final long timeout, final TimeUnit timeUnit, boolean exclusive) {
        HostThreadData data = getData();
        ClientConnection conn = data.availbleConnections.poll();
        while (conn != null && !conn.isOpen()) {
            conn = data.availbleConnections.poll();
        }
        if (conn != null) {
            if (exclusive) {
                data.connections--;
            }
            connectionReady(conn, callback, exchange, exclusive);
        } else if (exclusive || data.connections < loadBalancingProxyClient.getConnectionsPerThread()) {
            openConnection(exchange, callback, data, exclusive);
        } else {
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

    private static final class HostThreadData {

        int connections = 0;
        final Deque<ClientConnection> availbleConnections = new ArrayDeque<ClientConnection>();
        final Deque<CallbackHolder> awaitingConnections = new ArrayDeque<CallbackHolder>();

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

    enum AvailabilityType {
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
         * The host is probably down, only try as a last resort
         */
        PROBLEM,
        /**
         * The host is closed. connections will always fail
         */
        CLOSED;
    }
}
