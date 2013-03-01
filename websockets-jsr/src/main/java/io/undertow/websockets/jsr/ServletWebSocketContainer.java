package io.undertow.websockets.jsr;

import javax.websocket.server.ServerEndpointConfiguration;

import io.undertow.websockets.impl.UuidWebSocketSessionIdGenerator;
import io.undertow.websockets.impl.WebSocketSessionConnectionCallback;

/**
 * @author Stuart Douglas
 */
public class ServletWebSocketContainer extends ServerWebSocketContainer {

    private final JsrWebSocketServlet servlet;

    public ServletWebSocketContainer(final EndpointFactory factory, final ServerEndpointConfiguration... configs) {
        super(factory, configs);
        servlet = new JsrWebSocketServlet(new WebSocketSessionConnectionCallback(new UuidWebSocketSessionIdGenerator(),
                new EndpointSessionHandler(this), false), configs);
    }

    public JsrWebSocketServlet getServlet() {
        return servlet;
    }
}
