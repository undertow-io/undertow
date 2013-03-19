package io.undertow.websockets.jsr;

import io.undertow.websockets.impl.UuidWebSocketSessionIdGenerator;
import io.undertow.websockets.impl.WebSocketSessionConnectionCallback;

/**
 * @author Stuart Douglas
 */
public class ServletWebSocketContainer extends ServerWebSocketContainer {

    private final JsrWebSocketFilter filter;

    public ServletWebSocketContainer(final ConfiguredServerEndpoint... configs) {

        filter = new JsrWebSocketFilter(new WebSocketSessionConnectionCallback(new UuidWebSocketSessionIdGenerator(),
                new EndpointSessionHandler(this), false), configs);
    }

    public JsrWebSocketFilter getFilter() {
        return filter;
    }
}
