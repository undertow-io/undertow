package io.undertow.servlet.websockets;

import io.undertow.UndertowLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.core.protocol.version00.Hybi00Handshake;
import io.undertow.websockets.core.protocol.version07.Hybi07Handshake;
import io.undertow.websockets.core.protocol.version08.Hybi08Handshake;
import io.undertow.websockets.core.protocol.version13.Hybi13Handshake;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class WebSocketServlet extends HttpServlet {

    public static final String SESSION_HANDLER = "io.undertow.handler";

    private final List<Handshake> handshakes;

    private WebSocketConnectionCallback callback;

    public WebSocketServlet() {
        this.handshakes = handshakes();
    }

    public WebSocketServlet(WebSocketConnectionCallback callback) {
        this.callback = callback;
        this.handshakes = handshakes();
    }


    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        try {
            final String sessionHandler = config.getInitParameter(SESSION_HANDLER);
            if (sessionHandler != null) {
                final Class<?> clazz = Class.forName(sessionHandler, true, Thread.currentThread().getContextClassLoader());
                final Object handler = clazz.newInstance();
                this.callback = (WebSocketConnectionCallback) handler;
            }
            //TODO: set properties based on init params

        } catch (ClassNotFoundException e) {
            throw new ServletException(e);
        } catch (InstantiationException e) {
            throw new ServletException(e);
        } catch (IllegalAccessException e) {
            throw new ServletException(e);
        }
        if (callback == null) {
            throw UndertowServletMessages.MESSAGES.noWebSocketHandler();
        }
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

    protected List<Handshake> handshakes() {
        List<Handshake> handshakes = new ArrayList<Handshake>();
        handshakes.add(new Hybi13Handshake());
        handshakes.add(new Hybi08Handshake());
        handshakes.add(new Hybi07Handshake());
        handshakes.add(new Hybi00Handshake());
        return handshakes;
    }

}
