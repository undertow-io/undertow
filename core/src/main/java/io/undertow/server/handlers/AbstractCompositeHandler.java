package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.AccessLogReceiver;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;

/**
 * A handler which can fluently construct complex handlers.
 * Outside libraries can extend the abstract class in order to add additional custom handlers.
 * @author bill
 *
 * @param <T>
 */
public abstract class AbstractCompositeHandler<T extends AbstractCompositeHandler<T>> implements HttpHandler {
    private final HttpHandler handler;

    public AbstractCompositeHandler(HttpHandler handler) {
        Handlers.handlerNotNull(handler);
        this.handler = handler;
    }

    protected abstract T create(HttpHandler handler);

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        handler.handleRequest(exchange);
    }

    public T trace() {
        return create(Handlers.trace(handler));
    }

    public T header(String headerName, String headerValue) {
        return create(Handlers.header(handler, headerName, headerValue));
    }

    public T requestLimiting(final int maxRequest, final int queueSize) {
        return create(Handlers.requestLimitingHandler(maxRequest, queueSize, handler));
    }

    public T requestLimiting(final RequestLimit requestLimit) {
        return create(Handlers.requestLimitingHandler(requestLimit, handler));
    }

    public T allowedMethods(HttpString... allowedMethods) {
        return create(new AllowedMethodsHandler(handler, allowedMethods));
    }

    public T disallowedMethods(HttpString... allowedMethods) {
        return create(new DisallowedMethodsHandler(handler, allowedMethods));
    }

    public T blocking() {
        return create(new BlockingHandler(handler));
    }

    public <A> T attachment(AttachmentKey<A> key) {
        return create(new AttachmentHandler<>(key, handler));
    }

    public <A> T attachment(AttachmentKey<A> key, A instance) {
        return create(new AttachmentHandler<>(key, handler, instance));
    }

    public T metrics() {
        return create(new MetricsHandler(handler));
    }

    public T requestDumping() {
        return create(new RequestDumpingHandler(handler));
    }

    /*
     * I didn't want to modify the existing status code handler.
     * Maybe there should be a second that delegates to an internal handler?
     * Possibly an optional handler and all the singletons in the class pass in null/
     */
    public T responseCode(final int responseCode) {
        return create(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.setResponseCode(responseCode);
                handler.handleRequest(exchange);
            }
        });
    }

    public T requestLogging(final AccessLogReceiver accessLogReceiver, final String formatString, ClassLoader classLoader) {
        return create(new AccessLogHandler(handler, accessLogReceiver, formatString, classLoader));
    }

    /**
     * Default implementation
     * @author bill
     *
     */
    public static class CompositeHandler extends AbstractCompositeHandler<CompositeHandler> {
        public CompositeHandler(HttpHandler handler) {
            super(handler);
        }

        @Override
        protected CompositeHandler create(HttpHandler handler) {
            return new CompositeHandler(handler);
        }
    }
}
