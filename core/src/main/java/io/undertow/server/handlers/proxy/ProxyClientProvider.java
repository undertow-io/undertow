package io.undertow.server.handlers.proxy;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.util.concurrent.TimeUnit;

/**
 * A provider for a {@link ProxyClient}. The resulting proxy client is scoped to the connection.

 * When the client is acquired the provided handler will be invoked, with the connection available under the
 * {@link #CLIENT} attachment key on the exchange. If the client could not be acquired then the cause will be available
 * from the {@link #THROWABLE} attachment key.
 *
 * The handler will always be executed via a call to {@link io.undertow.server.HttpServerExchange#dispatch()}, so
 * unless the request has been dispatched again the exchange will be completed once the handler returns.
 *
 * @author Stuart Douglas
 */
public interface ProxyClientProvider {

    AttachmentKey<ProxyClient> CLIENT = AttachmentKey.create(ProxyClient.class);

    AttachmentKey<Throwable> THROWABLE = AttachmentKey.create(Throwable.class);

    void createProxyClient(final HttpServerExchange exchange, final HttpHandler nextHandler, long timeout, TimeUnit timeUnit);
}
