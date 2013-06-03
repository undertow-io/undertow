package io.undertow.examples.websockets;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.api.AbstractAssembledFrameHandler;
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
@UndertowExample("Web Sockets")
public class WebSocketServer {

    public static void main(final String[] args) {
        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .setHandler(path()
                        .addPath("/myapp", websocket(new WebSocketSessionHandler() {
                            @Override
                            public void onSession(final WebSocketSession session, WebSocketHttpExchange exchange) {
                                session.setFrameHandler(new AbstractAssembledFrameHandler() {
                                    @Override
                                    public void onTextFrame(final WebSocketSession session, final WebSocketFrameHeader header, final CharSequence payload) {
                                        session.sendText(payload, null);
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
