package io.undertow.websockets.server;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.WebSocketChannel;

/**
 * Interface that is used on the client side to accept web socket connections
 *
 * @author Stuart Douglas
 */
public interface WebSocketConnectionCallback {

    void onConnect(final HttpServerExchange exchange, WebSocketChannel channel);

}
