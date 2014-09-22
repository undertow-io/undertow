package io.undertow.examples.jsrwebsockets;

import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

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
