package io.undertow.examples.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.examples.websockets.WebSocketServer;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.api.AbstractAssembledFrameHandler;
import io.undertow.websockets.api.CloseReason;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.redirect;
import static io.undertow.Handlers.resource;
import static io.undertow.Handlers.websocket;

/**
 * @author Stuart Douglas
 */
@UndertowExample("Chat")
public class ChatServer {

    private static final List<WebSocketSession> sessions = new ArrayList<WebSocketSession>();

    public static void main(final String[] args) {

        System.out.println("To see chat in action is to open two different browsers and point them at http://localhost:8080");

        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .setHandler(path()
                        .addPath("/myapp", websocket(new WebSocketSessionHandler() {
                            @Override
                            public void onSession(final WebSocketSession session, WebSocketHttpExchange exchange) {
                                synchronized (sessions) {
                                    sessions.add(session);
                                }
                                session.setFrameHandler(new AbstractAssembledFrameHandler() {
                                    @Override
                                    public void onTextFrame(final WebSocketSession session, final WebSocketFrameHeader header, final CharSequence payload) {
                                        synchronized (sessions) {
                                            Iterator<WebSocketSession> it = sessions.iterator();
                                            while (it.hasNext()) {
                                                final WebSocketSession sess = it.next();
                                                try {
                                                    sess.sendText(payload);
                                                } catch (IOException e) {
                                                    it.remove();
                                                }
                                            }
                                        }
                                    }

                                    @Override
                                    public void onCloseFrame(final WebSocketSession session, final CloseReason reason) {
                                        synchronized (sessions) {
                                            sessions.remove(session);
                                        }
                                    }
                                });
                            }
                        }))
                        .addPath("index.html", resource(new ClassPathResourceManager(WebSocketServer.class.getClassLoader(), WebSocketServer.class.getPackage())))
                        .addPath("/", redirect("http://localhost:8080/index.html")))
                .build();
        server.start();
    }

}
