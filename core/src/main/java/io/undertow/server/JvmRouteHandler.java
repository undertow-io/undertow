package io.undertow.server;

import io.undertow.server.handlers.Cookie;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

/**
 * @author Stuart Douglas
 */
public class JvmRouteHandler implements HttpHandler {

    private final HttpHandler next;
    private final String sessionCookieName;
    private final String jvmRoute;
    private final JvmRouteWrapper wrapper = new JvmRouteWrapper();


    public JvmRouteHandler(HttpHandler next, String sessionCookieName, String jvmRoute) {
        this.next = next;
        this.sessionCookieName = sessionCookieName;
        this.jvmRoute = jvmRoute;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        Cookie sessionId = exchange.getRequestCookies().get(sessionCookieName);
        if (sessionId != null) {
            int part = sessionId.getValue().indexOf('.');
            if (part != -1) {
                sessionId.setValue(sessionId.getValue().substring(0, part));
            }
        }
        exchange.addResponseWrapper(wrapper);
        next.handleRequest(exchange);
    }

    private class JvmRouteWrapper implements ConduitWrapper<StreamSinkConduit> {

        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {

            Cookie sessionId = exchange.getResponseCookies().get(sessionCookieName);
            if (sessionId != null) {
                StringBuilder sb = new StringBuilder(sessionId.getValue());
                sb.append('.');
                sb.append(jvmRoute);
                sessionId.setValue(sb.toString());
            }
            return factory.create();
        }
    }
}
