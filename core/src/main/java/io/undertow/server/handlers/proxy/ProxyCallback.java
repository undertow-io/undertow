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
 * Yet another callback class, this one used by the proxy handler
 *
 * @author Stuart Douglas
 */
public interface ProxyCallback<T> {

    void completed(final HttpServerExchange exchange, T result);

    /**
     * Callback if establishing the connection to a backend server fails.
     *
     * @param exchange    the http server exchange
     */
    void failed(final HttpServerExchange exchange);

    /**
     * Callback if no backend server could be found.
     *
     * @param exchange    the http server exchange
     */
    void couldNotResolveBackend(final HttpServerExchange exchange);

    /**
     * This is invoked when the target connection pool transitions to problem status. It will be called once for each queued request
     * that has not yet been allocated a connection. The manager can redistribute these requests to other hosts, or can end the
     * exchange with an error status.
     *
     * @param exchange The exchange
     */
    void queuedRequestFailed(HttpServerExchange exchange);

}
