package io.undertow.websockets;

import io.undertow.server.HttpHandler;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.core.handler.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.impl.WebSocketSessionConnectionCallback;

/**
 * @author Stuart Douglas
 */
public class Websockets {

    public static HttpHandler handler(final WebSocketSessionHandler sessionHandler) {
        return new WebSocketProtocolHandshakeHandler(new WebSocketSessionConnectionCallback(sessionHandler));
    }

    private  Websockets() {}

}
