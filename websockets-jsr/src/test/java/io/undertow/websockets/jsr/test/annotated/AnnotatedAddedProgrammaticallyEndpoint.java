package io.undertow.websockets.jsr.test.annotated;


import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

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
