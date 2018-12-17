package io.undertow.websockets.jsr.test.annotated;


import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(AnnotatedAddedProgrammaticallyEndpoint.PATH)
public class AnnotatedAddedProgrammaticallyEndpoint {

    static final String PATH = "/programmatic";

    @OnMessage
    public String handleMessage(String message, Session session) {
        StringBuilder reversed = new StringBuilder(message);
        reversed.reverse();
        return reversed.toString();
    }
}
