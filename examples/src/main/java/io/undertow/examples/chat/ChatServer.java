package io.undertow.examples.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.predicate.Predicates;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.websockets.api.AbstractAssembledFrameHandler;
import io.undertow.websockets.api.CloseReason;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.spi.WebSocketHttpExchange;

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
                .addWebSocketHandler("/myapp", new WebSocketSessionHandler() {
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
                })
                .setDefaultHandler(
                        //we use a predicate handler here. If the path is index.html we serve the page
                        //otherwise we redirect to index.html
                        new PredicateHandler(
                                Predicates.path("/index.html"),
                                new ResourceHandler()
                                        .setResourceManager(new ClassPathResourceManager(ChatServer.class.getClassLoader(), ChatServer.class.getPackage())),
                                new RedirectHandler("http://localhost:8080/index.html")))
                .build();
        server.start();
    }

}
