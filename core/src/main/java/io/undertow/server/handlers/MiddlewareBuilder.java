package io.undertow.server.handlers;

import java.util.function.Function;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpHandler;

public class MiddlewareBuilder {
    private final Function<HttpHandler, HttpHandler> function;

    private MiddlewareBuilder(Function<HttpHandler, HttpHandler> function) {
        middlewareFunctionNotNull(function);
        this.function = function;
    }

    public static MiddlewareBuilder begin(Function<HttpHandler, HttpHandler> function) {
        return new MiddlewareBuilder(function);
    }

    /*
     * Since the before function is always passed in we want to apply it first
     * to make the chained calls apply in order instead of reverse.
     */
    public MiddlewareBuilder next(Function<HttpHandler, HttpHandler> before) {
        return new MiddlewareBuilder(function.compose(before));
    }

    public HttpHandler complete(HttpHandler handler) {
        return function.apply(handler);
    }

    private static void middlewareFunctionNotNull(final Function<HttpHandler, HttpHandler> function) {
        if (function == null) {
            throw UndertowMessages.MESSAGES.middlewareFunctionCannotBeNull();
        }
    }
}
