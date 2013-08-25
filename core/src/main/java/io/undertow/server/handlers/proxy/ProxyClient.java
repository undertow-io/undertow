package io.undertow.server.handlers.proxy;

import io.undertow.client.ClientConnection;
import io.undertow.server.HttpServerExchange;

import java.util.concurrent.TimeUnit;

/**
 * A client that provides connections for the proxy handler. The provided connection is valid for the duration of the
 * current exchange.
 *
 *
 * @author Stuart Douglas
 */
public interface ProxyClient {

    void getConnection(final HttpServerExchange exchange, final ProxyCallback<ClientConnection> callback, long timeout, TimeUnit timeUnit);

    boolean isOpen();

}
