package io.undertow.websockets.jsr;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.servlet.websockets.ServletWebSocketHttpExchange;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi07Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi08Handshake;
import io.undertow.websockets.jsr.handshake.JsrHybi13Handshake;

/**
 * @author Stuart Douglas
 */
public class JsrWebSocketFilter implements Filter {

    private final Set<Handshake> handshakes;

    private final WebSocketConnectionCallback callback;

    public JsrWebSocketFilter(WebSocketConnectionCallback callback, ConfiguredServerEndpoint... configs) {
        this.callback = callback;
        this.handshakes = handshakes(configs);
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        final ServletWebSocketHttpExchange facade = new ServletWebSocketHttpExchange((HttpServletRequest) request, (HttpServletResponse) response);
        Handshake handshaker = null;
        for (Handshake method : handshakes) {
            if (method.matches(facade)) {
                handshaker = method;
                break;
            }
        }

        if (handshaker == null) {
            chain.doFilter(request, response);
        } else {
            handshaker.handshake(facade, callback);
        }
    }

    @Override
    public void destroy() {

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
