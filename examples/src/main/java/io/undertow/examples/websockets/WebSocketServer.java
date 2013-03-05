package io.undertow.examples.websockets;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.predicate.PathMatchPredicate;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.RedirectHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.websockets.api.AbstractAssembledFrameHandler;
import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.api.WebSocketSession;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * @author Stuart Douglas
 */
@UndertowExample("Web Sockets")
public class WebSocketServer {

    public static void main(final String[] args) {
        Undertow server = Undertow.builder()
                .addListener(8080, "localhost")
                .addWebSocketHandler("/myapp", new WebSocketSessionHandler() {
                    @Override
                    public void onSession(final WebSocketSession session, WebSocketHttpExchange exchange) {
                        session.setFrameHandler(new AbstractAssembledFrameHandler() {
                            @Override
                            public void onTextFrame(final WebSocketSession session, final WebSocketFrameHeader header, final CharSequence payload) {
                                session.sendText(payload, null);
                            }
                        });
                    }
                })
                .setDefaultHandler(
                        //we use a predicate handler here. If the path is index.html we serve the page
                        //otherwise we redirect to index.html
                        new PredicateHandler(
                                new PathMatchPredicate("/index.html"),
                                new ResourceHandler()
                                        .setResourceManager(new ClassPathResourceManager(WebSocketServer.class.getClassLoader(), WebSocketServer.class.getPackage())),
                                new RedirectHandler("http://localhost:8080/index.html")))
                .build();
        server.start();
    }

}
