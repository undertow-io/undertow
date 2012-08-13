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
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;

/**
 * @author Stuart Douglas
 */
public class HttpServletResponseImpl implements HttpServletResponse {

    public static final String ATTACHMENT_KEY = HttpServletResponseImpl.class.getName();

    private final BlockingHttpServerExchange exchange;

    private ServletOutputStream servletOutputStream;
    private PrintWriter writer;

    public HttpServletResponseImpl(final BlockingHttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void addCookie(final Cookie cookie) {

    }

    @Override
    public boolean containsHeader(final String name) {
        return false;
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

    }

    @Override
    public void addDateHeader(final String name, final long date) {

    }

    @Override
    public void setHeader(final String name, final String value) {

    }

    @Override
    public void addHeader(final String name, final String value) {

    }

    @Override
    public void setIntHeader(final String name, final int value) {

    }

    @Override
    public void addIntHeader(final String name, final int value) {

    }

    @Override
    public void setStatus(final int sc) {

    }

    @Override
    public void setStatus(final int sc, final String sm) {

    }

    @Override
    public int getStatus() {
        return 0;
    }

    @Override
    public String getHeader(final String name) {
        return null;
    }

    @Override
    public Collection<String> getHeaders(final String name) {
        return null;
    }

    @Override
    public Collection<String> getHeaderNames() {
        return null;
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
        if (servletOutputStream == null) {
            if(writer != null) {
                throw UndertowServletMessages.MESSAGES.getWriterAlreadyCalled();
            }
            servletOutputStream = new ServletOutputStreamImpl(exchange.getOutputStream());
        }
        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            if(servletOutputStream != null) {
                throw UndertowServletMessages.MESSAGES.getOutputStreamAlreadyCalled();
            }
            writer = new PrintWriter(exchange.getOutputStream());
        }
        return writer;
    }

    @Override
    public void setCharacterEncoding(final String charset) {

    }

    @Override
    public void setContentLength(final int len) {

    }

    @Override
    public void setContentType(final String type) {

    }

    @Override
    public void setBufferSize(final int size) {

    }

    @Override
    public int getBufferSize() {
        return 0;
    }

    @Override
    public void flushBuffer() throws IOException {

    }

    @Override
    public void resetBuffer() {

    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void reset() {

    }

    @Override
    public void setLocale(final Locale loc) {

    }

    @Override
    public Locale getLocale() {
        return null;
    }
}
