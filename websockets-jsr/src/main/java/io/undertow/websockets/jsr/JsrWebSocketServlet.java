package io.undertow.websockets.jsr;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.UndertowLogger;
import io.undertow.servlet.websockets.ServletWebSocketHttpExchange;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi07Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi08Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi13Handshake;

/**
 * @author Stuart Douglas
 */
public class JsrWebSocketServlet extends HttpServlet {


    private final Set<Handshake> handshakes;

    private final WebSocketConnectionCallback callback;

    public JsrWebSocketServlet(WebSocketConnectionCallback callback, ConfiguredServerEndpoint... configs) {
        this.callback = callback;
        this.handshakes = handshakes(configs);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        final ServletWebSocketHttpExchange facade = new ServletWebSocketHttpExchange(req, resp);
        Handshake handshaker = null;
        for (Handshake method : handshakes) {
            if (method.matches(facade)) {
                handshaker = method;
                break;
            }
        }

        if (handshaker == null) {
            UndertowLogger.REQUEST_LOGGER.debug("Could not find hand shaker for web socket request");
            resp.sendError(400);
            return;
        }
        handshaker.handshake(facade, callback);
    }

    protected Set<Handshake> handshakes(ConfiguredServerEndpoint... configs) {
        Set<Handshake> handshakes = new HashSet<Handshake>();
        for (ConfiguredServerEndpoint config : configs) {
            handshakes.add(new JsrHybi07Handshake(config));
            handshakes.add(new JsrHybi08Handshake(config));
            handshakes.add(new JsrHybi13Handshake(config));
        }
        return handshakes;
    }

}
