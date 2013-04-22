package io.undertow.servlet.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.io.BlockingSenderImpl;
import io.undertow.io.Sender;
import io.undertow.server.BlockingHttpExchange;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

/**
 * @author Stuart Douglas
 */
public class ServletBlockingHttpExchange implements BlockingHttpExchange {

    private final HttpServerExchange exchange;
    private Sender sender;

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

    @Override
    public Sender getSender() {
        if (sender == null) {
            try {
                sender = new BlockingSenderImpl(exchange, getOutputStream());
            } catch (IllegalStateException e) {
                ServletResponse response = exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
                try {
                    sender = new BlockingWriterSenderImpl(exchange, response.getWriter(), response.getCharacterEncoding());
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }
            }
        }
        return sender;
    }
}
