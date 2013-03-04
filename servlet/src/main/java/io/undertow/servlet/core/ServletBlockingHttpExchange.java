package io.undertow.servlet.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.server.BlockingHttpExchange;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

/**
 * @author Stuart Douglas
 */
public class ServletBlockingHttpExchange implements BlockingHttpExchange {

    private final HttpServerExchange exchange;

    public ServletBlockingHttpExchange(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public InputStream getInputStream() {
        ServletRequest request = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        try {
            return request.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        ServletResponse response = exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        try {
            return response.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
