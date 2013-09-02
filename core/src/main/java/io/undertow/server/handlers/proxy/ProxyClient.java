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
 * @author Stuart Douglas
 */
public interface ProxyClient {

    void getConnection(final HttpServerExchange exchange, final ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit);

}
