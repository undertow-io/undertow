package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.AccessLogReceiver;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;

public class CompositeHandler implements HttpHandler {
    private final HttpHandler handler;

    public CompositeHandler(HttpHandler handler) {
        Handlers.handlerNotNull(handler);
        this.handler = handler;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        handler.handleRequest(exchange);
    }

    public CompositeHandler trace() {
        return new CompositeHandler(Handlers.trace(handler));
    }

    public CompositeHandler header(String headerName, String headerValue) {
        return new CompositeHandler(Handlers.header(handler, headerName, headerValue));
    }

    public CompositeHandler requestLimiting(final int maxRequest, final int queueSize) {
        return new CompositeHandler(Handlers.requestLimitingHandler(maxRequest, queueSize, handler));
    }

    public CompositeHandler requestLimiting(final RequestLimit requestLimit) {
        return new CompositeHandler(Handlers.requestLimitingHandler(requestLimit, handler));
    }

    public CompositeHandler allowedMethods(HttpString... allowedMethods) {
        return new CompositeHandler(new AllowedMethodsHandler(handler, allowedMethods));
    }

    public CompositeHandler disallowedMethods(HttpString... allowedMethods) {
        return new CompositeHandler(new DisallowedMethodsHandler(handler, allowedMethods));
    }

    public CompositeHandler blocking() {
        return new CompositeHandler(new BlockingHandler(handler));
    }

    public <T> CompositeHandler attachment(AttachmentKey<T> key) {
        return new CompositeHandler(new AttachmentHandler<>(key, handler));
    }

    public <T> CompositeHandler attachment(AttachmentKey<T> key, T instance) {
        return new CompositeHandler(new AttachmentHandler<>(key, handler, instance));
    }

    public CompositeHandler metrics() {
        return new CompositeHandler(new MetricsHandler(handler));
    }

    public CompositeHandler requestDumping() {
        return new CompositeHandler(new RequestDumpingHandler(handler));
    }

    /*
     * I didn't want to modify the existing status code handler.
     * Maybe there should be a second that delegates to an internal handler?
     * Possibly an optional handler and all the singletons in the class pass in null/
     */
    public CompositeHandler responseCode(final int responseCode) {
        return new CompositeHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.setResponseCode(responseCode);
                handler.handleRequest(exchange);
            }
        });
    }

    public CompositeHandler requestLogging(final AccessLogReceiver accessLogReceiver, final String formatString, ClassLoader classLoader) {
        return new CompositeHandler(new AccessLogHandler(handler, accessLogReceiver, formatString, classLoader));
    }
}
