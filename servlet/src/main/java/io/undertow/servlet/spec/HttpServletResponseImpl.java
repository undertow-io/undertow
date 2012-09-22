/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.spec;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.handlers.CookieHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
public class HttpServletResponseImpl implements HttpServletResponse {

    public static final AttachmentKey<ServletResponse> ATTACHMENT_KEY = AttachmentKey.create(ServletResponse.class);

    private final BlockingHttpServerExchange exchange;

    private ServletOutputStreamImpl servletOutputStream;
    private PrintWriter writer;
    private Integer bufferSize;

    public HttpServletResponseImpl(final BlockingHttpServerExchange exchange) {
        this.exchange = exchange;
    }

    public BlockingHttpServerExchange getExchange() {
        return exchange;
    }

    @Override
    public void addCookie(final Cookie cookie) {
        final AttachmentList<io.undertow.server.handlers.Cookie> cookies = exchange.getExchange().getAttachment(io.undertow.server.handlers.Cookie.RESPONSE_COOKIES);
        cookies.add(new io.undertow.server.handlers.Cookie(cookie.getName(), cookie.getValue()));
    }

    @Override
    public boolean containsHeader(final String name) {
        return exchange.getExchange().getResponseHeaders().contains(name);
    }

    @Override
    public String encodeURL(final String url) {
        return null;
    }

    @Override
    public String encodeRedirectURL(final String url) {
        return null;
    }

    @Override
    public String encodeUrl(final String url) {
        return null;
    }

    @Override
    public String encodeRedirectUrl(final String url) {
        return null;
    }

    @Override
    public void sendError(final int sc, final String msg) throws IOException {

    }

    @Override
    public void sendError(final int sc) throws IOException {

    }

    @Override
    public void sendRedirect(final String location) throws IOException {

    }

    @Override
    public void setDateHeader(final String name, final long date) {
        exchange.getExchange().getResponseHeaders().put(name, DateUtils.toDateString(new Date(date)));
    }

    @Override
    public void addDateHeader(final String name, final long date) {
        exchange.getExchange().getResponseHeaders().add(name, DateUtils.toDateString(new Date(date)));
    }

    @Override
    public void setHeader(final String name, final String value) {
        exchange.getExchange().getResponseHeaders().put(name, value);
    }

    @Override
    public void addHeader(final String name, final String value) {
        exchange.getExchange().getResponseHeaders().add(name, value);
    }

    @Override
    public void setIntHeader(final String name, final int value) {
        exchange.getExchange().getResponseHeaders().put(name, "" + value);
    }

    @Override
    public void addIntHeader(final String name, final int value) {
        exchange.getExchange().getResponseHeaders().add(name, "" + value);
    }

    @Override
    public void setStatus(final int sc) {
        exchange.getExchange().setResponseCode(sc);
    }

    @Override
    public void setStatus(final int sc, final String sm) {
        setStatus(sc);
    }

    @Override
    public int getStatus() {
        return exchange.getExchange().getResponseCode();
    }

    @Override
    public String getHeader(final String name) {
        return exchange.getExchange().getResponseHeaders().getFirst(name);
    }

    @Override
    public Collection<String> getHeaders(final String name) {
        return new ArrayList<String>(exchange.getExchange().getResponseHeaders().get(name));
    }

    @Override
    public Collection<String> getHeaderNames() {
        return exchange.getExchange().getResponseHeaders().getHeaderNames();
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw UndertowServletMessages.MESSAGES.getWriterAlreadyCalled();
        }
        if (servletOutputStream == null) {
            if (bufferSize == null) {
                servletOutputStream = new ServletOutputStreamImpl(exchange.getExchange().getResponseChannelFactory(), this);
            } else {
                servletOutputStream = new ServletOutputStreamImpl(exchange.getExchange().getResponseChannelFactory(), this, bufferSize);
            }
        }
        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            if (servletOutputStream != null) {
                throw UndertowServletMessages.MESSAGES.getOutputStreamAlreadyCalled();
            }
            if (bufferSize == null) {
                servletOutputStream = new ServletOutputStreamImpl(exchange.getExchange().getResponseChannelFactory(), this);
            } else {
                servletOutputStream = new ServletOutputStreamImpl(exchange.getExchange().getResponseChannelFactory(), this, bufferSize);
            }
            writer = new PrintWriter(new OutputStreamWriter(servletOutputStream));
        }
        return writer;
    }

    @Override
    public void setCharacterEncoding(final String charset) {

    }

    @Override
    public void setContentLength(final int len) {
        exchange.getExchange().getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + len);
    }

    @Override
    public void setContentType(final String type) {
        exchange.getExchange().getResponseHeaders().put(Headers.CONTENT_TYPE, type);
    }

    @Override
    public void setBufferSize(final int size) {
        if (servletOutputStream != null) {
            servletOutputStream.setBufferSize(size);
        }
        this.bufferSize = size;
    }

    @Override
    public int getBufferSize() {
        //todo: fix this
        return bufferSize;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        } else if (servletOutputStream != null) {
            servletOutputStream.flush();
        }
    }

    @Override
    public void resetBuffer() {
        if (servletOutputStream != null) {
            servletOutputStream.resetBuffer();
        }
    }

    @Override
    public boolean isCommitted() {
        return exchange.getExchange().isResponseStarted();
    }

    @Override
    public void reset() {
        if (servletOutputStream != null) {
            servletOutputStream.resetBuffer();
        }
        exchange.getExchange().getResponseHeaders().clear();
        exchange.getExchange().setResponseCode(200);
    }

    @Override
    public void setLocale(final Locale loc) {

    }

    @Override
    public Locale getLocale() {
        return null;
    }
}
