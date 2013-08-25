package io.undertow.server.handlers.proxy;

import io.undertow.server.HttpServerExchange;

/**
 * Yet another callback class, this one used by the proxy handler
 *
 * @author Stuart Douglas
 */
public interface ProxyCallback<T> {

    void completed(final HttpServerExchange exchange, T result);

    void failed(HttpServerExchange exchange);

}
