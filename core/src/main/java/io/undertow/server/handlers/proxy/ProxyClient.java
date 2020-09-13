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
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient.Host;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.TimeUnit;

/**
 * A client that provides connections for the proxy handler. The provided connection is valid for the duration of the
 * current exchange.
 *
 * Note that implementation are required to manage the lifecycle of these connections themselves, generally by registering callbacks
 * on the exchange.
 *
 *
 *
 *
 * @author Stuart Douglas
 */
public interface ProxyClient {

    /**
     * Finds a proxy target for this request, returning null if none can be found.
     *
     * If this method returns null it means that there is no backend available to handle
     * this request, and it should proceed as normal.
     *
     * @param exchange The exchange
     * @return The proxy target
     */
    ProxyTarget findTarget(final HttpServerExchange exchange);

    /**
     * Gets a proxy connection for the given request.
     *
     * @param exchange The exchange
     * @param callback The callback
     * @param timeout The timeout
     * @param timeUnit Time unit for the timeout
     */
    void getConnection(final ProxyTarget target, final HttpServerExchange exchange, final ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit);

    /**
     * An opaque interface that may contain information about the proxy target
     */
    interface ProxyTarget {

    }

    interface MaxRetriesProxyTarget extends ProxyTarget {
        int getMaxRetries();
    }

    interface HostProxyTarget extends ProxyTarget {
        void setHost(Host host);
    }

    default List<ProxyTarget> getAllTargets(){
        return new ArrayList();
    }
}
