package io.undertow.examples.jsrwebsockets;

import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

/**
 * @author Stuart Douglas
 */
@ServerEndpoint("/myapp")
public class JsrChatWebSocketEndpoint {

    @OnMessage
    public void message(String message, Session session) {
        for (Session s : session.getOpenSessions()) {
            s.getAsyncRemote().sendText(message);
        }
    }

}
