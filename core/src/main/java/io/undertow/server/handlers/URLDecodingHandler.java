package io.undertow.server.handlers;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Handler that performs URL decoding.
 *
 * TODO: this is not very efficient at the moment, this will need to be optimised
 *
 * @author Stuart Douglas
 */
public class URLDecodingHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;

    public URLDecodingHandler() {
    }

    public URLDecodingHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {

        try {
            exchange.setRelativePath(URLDecoder.decode(exchange.getRelativePath(), "UTF-8"));
            exchange.setCanonicalPath(URLDecoder.decode(exchange.getRequestPath(), "UTF-8"));
            for (Map.Entry<String, Deque<String>> entry : exchange.getQueryParameters().entrySet()) {
                final Deque<String> value = entry.getValue();
                final Deque<String> newValue = new ArrayDeque<>(value.size());
                for (String v : value) {
                    newValue.push(URLDecoder.decode(v, "UTF-8"));
                }
                entry.setValue(newValue);
            }
            HttpHandlers.executeHandler(next, exchange);
        } catch (UnsupportedEncodingException e) {
            UndertowLogger.REQUEST_LOGGER.debug("Unsupported encoding", e);
            exchange.setResponseCode(500);
            exchange.endExchange();
        }
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }
}
