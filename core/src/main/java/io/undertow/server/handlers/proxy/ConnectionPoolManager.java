package io.undertow.server.handlers.proxy;

import io.undertow.server.HttpServerExchange;

/**
 * Manager that controls the behaviour of a {@link ProxyConnectionPool}
 *
 * @author Stuart Douglas
 */
public interface ConnectionPoolManager {

    /**
     * Returns true if the connection pool can create a new connection
     *
     * @param connections The number of connections associated with the current IO thread.
     * @param proxyConnectionPool The connection pool
     * @return true if a connection can be created
     */
    boolean canCreateConnection(int connections, ProxyConnectionPool proxyConnectionPool);

    /**
     * This is invoked when the target thread pool transitions to problem status. It will be called once for each queued request
     * that has not yet been allocated a connection. The manager can redistribute these requests to other hosts, or can end the
     * exchange with an error status.
     *
     * @param proxyTarget The proxy target
     * @param exchange The exchange
     * @param callback The callback
     * @param timeoutMills The remaining timeout in milliseconds, or -1 if no timeout has been specified
     */
    void queuedConnectionFailed(ProxyClient.ProxyTarget proxyTarget, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeoutMills);

    /**
     *
     * @return The amount of time that we should wait before re-testing a problem server
     */
    int getProblemServerRetry();
}
