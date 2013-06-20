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
import io.undertow.servlet.handlers.ServletRequestContext;
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
        ServletRequest request = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletRequest();
        try {
            return request.getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        ServletResponse response = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletResponse();
        try {
            return response.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Sender getSender() {
        try {
            return new BlockingSenderImpl(exchange, getOutputStream());
        } catch (IllegalStateException e) {
            ServletResponse response = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletResponse();
            try {
                return new BlockingWriterSenderImpl(exchange, response.getWriter(), response.getCharacterEncoding());
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!exchange.isComplete()) {
            ServletRequestContext attachments = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            HttpServletRequestImpl request = attachments.getOriginalRequest();
            request.closeAndDrainRequest();
            HttpServletResponseImpl response = attachments.getOriginalResponse();
            response.closeStreamAndWriter();
        }
    }
}
