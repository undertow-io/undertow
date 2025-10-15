/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import io.undertow.io.BlockingReceiverImpl;
import io.undertow.io.BlockingSenderImpl;
import io.undertow.io.Receiver;
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
        ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (!exchange.isComplete()) {
            try {
                HttpServletRequestImpl request = servletRequestContext.getOriginalRequest();
                request.closeAndDrainRequest();
            } finally {
                HttpServletResponseImpl response = servletRequestContext.getOriginalResponse();
                response.closeStreamAndWriter();
            }
        } else {
            try {
            HttpServletRequestImpl request = servletRequestContext.getOriginalRequest();
            request.freeResources();
            } finally {
                HttpServletResponseImpl response = servletRequestContext.getOriginalResponse();
                response.freeResources();
            }
        }
    }

    @Override
    public Receiver getReceiver() {
        return new BlockingReceiverImpl(exchange, getInputStream());
    }
}
