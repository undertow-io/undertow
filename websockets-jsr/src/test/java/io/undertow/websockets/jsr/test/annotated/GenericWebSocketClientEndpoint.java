package io.undertow.websockets.jsr.test.annotated;

public interface GenericWebSocketClientEndpoint<M> {

    void onMessage(M message);
}
