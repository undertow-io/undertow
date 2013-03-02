package io.undertow.websockets.jsr;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.impl.UuidWebSocketSessionIdGenerator;
import io.undertow.websockets.impl.WebSocketSessionConnectionCallback;

/**
 * @author Stuart Douglas
 */
public class AsyncWebSocketContainer extends ServerWebSocketContainer implements HttpHandler {
    private final JsrWebSocketProtocolHandshakeHandler handler;

    public AsyncWebSocketContainer(final ConfiguredServerEndpoint... configs) {

        handler = new JsrWebSocketProtocolHandshakeHandler(new WebSocketSessionConnectionCallback(new UuidWebSocketSessionIdGenerator(),
                new EndpointSessionHandler(this), false), configs);
    }


    @Override
    public void handleRequest(HttpServerExchange exchange) {
        handler.handleRequest(exchange);
    }
}
