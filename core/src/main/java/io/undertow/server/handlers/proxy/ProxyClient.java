package io.undertow.server.handlers.proxy;

import io.undertow.server.HttpServerExchange;

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
    public interface ProxyTarget {

    }
}
