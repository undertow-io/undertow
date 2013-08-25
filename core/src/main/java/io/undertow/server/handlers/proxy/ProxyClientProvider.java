package io.undertow.server.handlers.proxy;

import io.undertow.server.HttpServerExchange;

import java.util.concurrent.TimeUnit;

/**
 * A provider for a {@link ProxyClient}. The resulting proxy client is scoped to the connection.
 *
 * This may register IO callbacks, and as a result the call stack may return. This should not be invoked inside the
 * scope of a handler chain, but rather for a dispatched task.
 *
 * @author Stuart Douglas
 */
public interface ProxyClientProvider {

    void createProxyClient(final HttpServerExchange exchange, final ProxyCallback<ProxyClient> callback, long timeout, TimeUnit timeUnit);
}
