package io.undertow.server.handlers;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpHandlers;
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

    private String charset = "UTF-8";

    public URLDecodingHandler() {
    }

    public URLDecodingHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {

        try {
            exchange.setRelativePath(URLDecoder.decode(exchange.getRelativePath(),charset));
            exchange.setCanonicalPath(URLDecoder.decode(exchange.getRequestPath(), charset));
            for (Map.Entry<String, Deque<String>> entry : exchange.getQueryParameters().entrySet()) {
                final Deque<String> value = entry.getValue();
                final Deque<String> newValue = new ArrayDeque<>(value.size());
                for (String v : value) {
                    newValue.push(URLDecoder.decode(v, charset));
                }
                entry.setValue(newValue);
            }
            next.handleRequest(exchange);
        } catch (UnsupportedEncodingException e) {
            UndertowLogger.REQUEST_LOGGER.debug("Unsupported encoding", e);
            exchange.setResponseCode(500);
            exchange.endExchange();
        }
    }

    public HttpHandler getNext() {
        return next;
    }

    public URLDecodingHandler setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    public String getCharset() {
        return charset;
    }

    public URLDecodingHandler setCharset(final String charset) {
        this.charset = charset;
        return this;
    }
}
