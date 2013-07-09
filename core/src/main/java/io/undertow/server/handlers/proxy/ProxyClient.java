package io.undertow.server.handlers.proxy;

import io.undertow.client.HttpClientConnection;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.util.concurrent.TimeUnit;

/**
 * A client that provides connections for the proxy handler. The provided connection is valid for the duration of the
 * current exchange.
 *
 * When the connection is acquired the provided handler will be invoked, with the connection available under the
 * {@link #CONNECTION} attachment key on the exchange. If the connection could not be acquired then the cause will be available
 * from the {@link #THROWABLE} attachment key.
 *
 * The handler will always be executed via a call to {@link io.undertow.server.HttpServerExchange#dispatch()}, so
 * unless the request has been dispatched again the exchange will be completed once the handler returns.
 *
 * @author Stuart Douglas
 */
public interface ProxyClient {

    AttachmentKey<HttpClientConnection> CONNECTION = AttachmentKey.create(HttpClientConnection.class);

    AttachmentKey<Throwable> THROWABLE = AttachmentKey.create(Throwable.class);

    void getConnection(final HttpServerExchange exchange, final HttpHandler nextHandler, long timeout, TimeUnit timeUnit);

}
