package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handler that dispatches to a given handler and allows mapping exceptions
 * to be handled by additional handlers.  The order the exception handlers are
 * added is important because of inheritance.  Add all child classes before their
 * parents in order to use different handlers.
 */
public class ExceptionHandler implements HttpHandler {
    public static final AttachmentKey<Throwable> THROWABLE = AttachmentKey.create(Throwable.class);

    private final HttpHandler handler;
    private final List<ExceptionHandlerHolder<?>> exceptionHandlers = new CopyOnWriteArrayList<>();

    public ExceptionHandler(HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        try {
            handler.handleRequest(exchange);
        } catch (Throwable throwable) {
            for (ExceptionHandlerHolder<?> holder : exceptionHandlers) {
                if (holder.getClazz().isInstance(throwable)) {
                    exchange.putAttachment(THROWABLE, throwable);
                    holder.getHandler().handleRequest(exchange);
                    return;
                }
            }
            throw throwable;
        }
    }

    public <T extends Throwable> ExceptionHandler addExceptionHandler(Class<T> clazz, HttpHandler handler) {
        exceptionHandlers.add(new ExceptionHandlerHolder<>(clazz, handler));
        return this;
    }

    private static class ExceptionHandlerHolder<T extends Throwable> {
        private final Class<T> clazz;
        private final HttpHandler handler;
        ExceptionHandlerHolder(Class<T> clazz, HttpHandler handler) {
            super();
            this.clazz = clazz;
            this.handler = handler;
        }
        public Class<T> getClazz() {
            return clazz;
        }
        public HttpHandler getHandler() {
            return handler;
        }
    }
}
