package io.undertow.server.handlers;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.HttpHeaderNames;

/**
 *
 * Handler that disables response caching by browsers and proxies.
 *
 *
 * @author Stuart Douglas
 */
public class DisableCacheHandler implements HttpHandler {

    private final HttpHandler next;

    public DisableCacheHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.responseHeaders().add(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        exchange.responseHeaders().add(HttpHeaderNames.PRAGMA, "no-cache");
        exchange.responseHeaders().add(HttpHeaderNames.EXPIRES, "0");
        next.handleRequest(exchange);
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "disable-cache";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper();
        }

    }

    private static class Wrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new DisableCacheHandler(handler);
        }
    }
}
