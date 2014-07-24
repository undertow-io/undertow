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

import io.undertow.server.HttpServerExchange;

/**
 * Manager that controls the behaviour of a {@link ProxyConnectionPool}
 *
 * @author Stuart Douglas
 */
public interface ConnectionPoolManager {

    /**
     * Check if the pool is available.
     *
     * @return true if the pool can be used
     */
    boolean isAvailable();

    /**
     * Notify a connection error.
     */
    void connectionError();

    /**
     * Clear the connection error.
     */
    void clearErrorState();

    /**
     * Returns true if the connection pool can create a new connection
     *
     * @param connections The number of connections associated with the current IO thread.
     * @param proxyConnectionPool The connection pool
     * @return true if a connection can be created
     */
    boolean canCreateConnection(int connections, ProxyConnectionPool proxyConnectionPool);

    /**
     * Returns true if the pool should cache a new connection
     *
     * @return true if the connection can be cached
     */
    boolean cacheConnection(int connections, ProxyConnectionPool proxyConnectionPool);

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
