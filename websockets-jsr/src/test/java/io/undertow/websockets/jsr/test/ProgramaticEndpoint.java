package io.undertow.websockets.jsr.test;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

/**
 * @author Stuart Douglas
 */
public class ProgramaticEndpoint extends Endpoint {
    @Override
    public void onOpen(final Session session, EndpointConfig config) {
        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                session.getAsyncRemote().sendText("Hello " + message);
            }
        });

    }
}
