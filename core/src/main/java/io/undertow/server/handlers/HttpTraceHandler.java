package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

/**
 * A handler that handles HTTP trace requests
 *
 * @author Stuart Douglas
 */
public class HttpTraceHandler implements HttpHandler {

    private final HttpHandler handler;

    public HttpTraceHandler(final HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if(exchange.getRequestMethod().equals(Methods.TRACE)) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "message/http");
            StringBuilder body = new StringBuilder("TRACE ");
            body.append(exchange.getRequestURI());
            if(!exchange.getQueryString().isEmpty()) {
                body.append('?');
                body.append(exchange.getQueryString());
            }
            body.append(exchange.getProtocol().toString());
            body.append("\r\n");
            for(HeaderValues header : exchange.getRequestHeaders()) {
                for(String value : header) {
                    body.append(header.getHeaderName());
                    body.append(": ");
                    body.append(value);
                    body.append("\r\n");
                }
            }
            body.append("\r\n");
            exchange.getResponseSender().send(body.toString());
        } else {
            handler.handleRequest(exchange);
        }
    }
}
