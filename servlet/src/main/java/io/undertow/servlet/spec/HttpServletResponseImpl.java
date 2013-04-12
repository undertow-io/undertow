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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.handlers.ServletAttachments;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.CanonicalPathUtils;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
public final class HttpServletResponseImpl implements HttpServletResponse {

    public static final AttachmentKey<ServletResponse> ATTACHMENT_KEY = AttachmentKey.create(ServletResponse.class);

    private final HttpServerExchange exchange;
    private volatile ServletContextImpl servletContext;

    private ServletOutputStreamImpl servletOutputStream;
    private ResponseState responseState = ResponseState.NONE;
    private PrintWriter writer;
    private Integer bufferSize;
    private Long contentLength;
    private boolean insideInclude = false;
    private Locale locale;
    private boolean responseDone = false;


    private boolean charsetSet = false; //if a content type has been set either implicitly or implicitly
    private String contentType;
    private String charset;

    public HttpServletResponseImpl(final HttpServerExchange exchange, final ServletContextImpl servletContext) {
        this.exchange = exchange;
        this.servletContext = servletContext;
    }

    public HttpServerExchange getExchange() {
        return exchange;
    }

    @Override
    public void addCookie(final Cookie cookie) {
        if (insideInclude) {
            return;
        }
        final AttachmentList<io.undertow.server.handlers.Cookie> cookies = exchange.getAttachment(io.undertow.server.handlers.Cookie.RESPONSE_COOKIES);
        cookies.add(new ServletCookieAdaptor(cookie));
    }

    @Override
    public boolean containsHeader(final String name) {
        return exchange.getResponseHeaders().contains(new HttpString(name));
    }

    @Override
    public String encodeURL(final String url) {
        return url;
    }

    @Override
    public String encodeRedirectURL(final String url) {
        return url;
    }

    @Override
    public String encodeUrl(final String url) {
        return url;
    }

    @Override
    public String encodeRedirectUrl(final String url) {
        return url;
    }

    @Override
    public void sendError(final int sc, final String msg) throws IOException {
        if (exchange.isResponseStarted()) {
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }
        resetBuffer();
        writer = null;
        responseState = ResponseState.NONE;
        exchange.setResponseCode(sc);
        //todo: is this the best way to handle errors?
        final String location = servletContext.getDeployment().getErrorPages().getErrorLocation(sc);
        if (location != null) {
            RequestDispatcherImpl requestDispatcher = new RequestDispatcherImpl(location, servletContext);
            try {
                requestDispatcher.error(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY), exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY), exchange.getAttachment(ServletAttachments.CURRENT_SERVLET).getManagedServlet().getServletInfo().getName(), msg);
            } catch (ServletException e) {
                throw new RuntimeException(e);
            }
        } else if (msg != null) {
            setContentType("text/html");
            getWriter().write(msg);
            getWriter().close();
        }
        responseDone();
    }

    @Override
    public void sendError(final int sc) throws IOException {
        sendError(sc, null);
    }

    @Override
    public void sendRedirect(final String location) throws IOException {
        if (exchange.isResponseStarted()) {
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }
        resetBuffer();
        setStatus(302);
        String realPath;
        if (location.startsWith("/")) {
            realPath = location;
        } else {
            String current = exchange.getRelativePath();
            int lastSlash = current.lastIndexOf("/");
            if (lastSlash != -1) {
                current = current.substring(0, lastSlash + 1);
            }
            realPath = servletContext.getContextPath() + CanonicalPathUtils.canonicalize(current + location);
        }
        String host = exchange.getRequestHeaders().getFirst(Headers.HOST);
        if (host == null) {
            host = exchange.getDestinationAddress().getAddress().getHostAddress();
        }
        String loc = exchange.getRequestScheme() + "://" + host + realPath;
        exchange.getResponseHeaders().put(Headers.LOCATION, loc);
        responseDone();
    }

    @Override
    public void setDateHeader(final String name, final long date) {
        setHeader(name, DateUtils.toDateString(new Date(date)));
    }

    @Override
    public void addDateHeader(final String name, final long date) {
        addHeader(name, DateUtils.toDateString(new Date(date)));
    }

    @Override
    public void setHeader(final String name, final String value) {
        setHeader(new HttpString(name), value);
    }


    public void setHeader(final HttpString name, final String value) {
        if (insideInclude) {
            return;
        }
        exchange.getResponseHeaders().put(name, value);
    }

    @Override
    public void addHeader(final String name, final String value) {
        addHeader(new HttpString(name), value);
    }

    public void addHeader(final HttpString name, final String value) {
        if (insideInclude) {
            return;
        }
        exchange.getResponseHeaders().add(name, value);
    }

    @Override
    public void setIntHeader(final String name, final int value) {
        setHeader(name, Integer.toString(value));
    }

    @Override
    public void addIntHeader(final String name, final int value) {
        addHeader(name, Integer.toString(value));
    }

    @Override
    public void setStatus(final int sc) {
        if (insideInclude) {
            return;
        }
        exchange.setResponseCode(sc);
    }

    @Override
    public void setStatus(final int sc, final String sm) {
        if (insideInclude) {
            return;
        }
        setStatus(sc);
    }

    @Override
    public int getStatus() {
        return exchange.getResponseCode();
    }

    @Override
    public String getHeader(final String name) {
        return exchange.getResponseHeaders().getFirst(new HttpString(name));
    }

    @Override
    public Collection<String> getHeaders(final String name) {
        return new ArrayList<String>(exchange.getResponseHeaders().get(new HttpString(name)));
    }

    @Override
    public Collection<String> getHeaderNames() {
        final Set<String> headers = new HashSet<String>();
        for (final HttpString i : exchange.getResponseHeaders().getHeaderNames()) {
            headers.add(i.toString());
        }
        return headers;
    }

    @Override
    public String getCharacterEncoding() {
        if (charset == null) {
            return "ISO-8859-1";
        }
        return charset;
    }

    @Override
    public String getContentType() {
        if (contentType != null) {
            if (charsetSet) {
                return contentType + ";charset=" + getCharacterEncoding();
            } else {
                return contentType;
            }
        }
        return null;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (responseState == ResponseState.WRITER) {
            throw UndertowServletMessages.MESSAGES.getWriterAlreadyCalled();
        }
        responseState = ResponseState.STREAM;
        createOutputStream();
        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            if (responseState == ResponseState.STREAM) {
                throw UndertowServletMessages.MESSAGES.getOutputStreamAlreadyCalled();
            }
            responseState = ResponseState.WRITER;
            createOutputStream();
            final ServletPrintWriter servletPrintWriter = new ServletPrintWriter(servletOutputStream, getCharacterEncoding());
            writer = ServletPrintWriterDelegate.newInstance(servletPrintWriter);
        }
        return writer;
    }

    private void createOutputStream() {
        if (servletOutputStream == null) {
            if (bufferSize == null) {
                servletOutputStream = new ServletOutputStreamImpl(contentLength, this);
            } else {
                servletOutputStream = new ServletOutputStreamImpl(contentLength, this, bufferSize);
            }
        }
    }

    @Override
    public void setCharacterEncoding(final String charset) {
        if (insideInclude || exchange.isResponseStarted() || writer != null || isCommitted()) {
            return;
        }
        charsetSet = true;
        this.charset = charset;
        if (contentType != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType());
        }
    }

    @Override
    public void setContentLength(final int len) {
        if (insideInclude || exchange.isResponseStarted()) {
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + len);
        this.contentLength = (long) len;
    }

    @Override
    public void setContentLengthLong(final long len) {
        if (insideInclude || exchange.isResponseStarted()) {
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + len);
        this.contentLength = len;
    }

    @Override
    public void setContentType(final String type) {
        if (insideInclude || exchange.isResponseStarted()) {
            return;
        }
        contentType = type;
        int split = type.indexOf(";");
        if (split != -1) {
            int pos = type.indexOf("charset=");
            if (pos != -1) {
                int i = pos + "charset=".length();
                do {
                    char c = type.charAt(i);
                    if (c == ' ' || c == '\t' || c == ';') {
                        break;
                    }
                    ++i;
                } while (i < type.length());
                if (writer == null && !isCommitted()) {
                    charsetSet = true;
                    //we only change the charset if the writer has not been retrieved yet
                    this.charset = type.substring(pos + "charset=".length(), i);
                }
                int charsetStart = pos;
                while (type.charAt(--charsetStart) != ';' && charsetStart >0) {}
                StringBuilder contentTypeBuilder = new StringBuilder();
                contentTypeBuilder.append(type.substring(0, charsetStart));
                if (i != type.length()) {
                    contentTypeBuilder.append(type.substring(i));
                }
                contentType = contentTypeBuilder.toString();
            }
            //strip any trailing semicolon
            for (int i = contentType.length() - 1; i >= 0; --i) {
                char c = contentType.charAt(i);
                if (c == ' ' || c == '\t') {
                    continue;
                }
                if(c == ';') {
                    contentType = contentType.substring(0, i);
                }
                break;
            }
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType());
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
        if (bufferSize == null) {
            return exchange.getConnection().getBufferSize();
        }
        return bufferSize;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        } else if (servletOutputStream != null) {
            servletOutputStream.flush();
        } else {
            createOutputStream();
            servletOutputStream.flush();
        }
    }

    public void closeStreamAndWriter() throws IOException {
        if (writer != null) {
            if (!servletOutputStream.isClosed()) {
                writer.flush();
            }
            writer.close();
        } else if (servletOutputStream == null) {
            createOutputStream();
        }
        //close also flushes
        servletOutputStream.close();
    }

    @Override
    public void resetBuffer() {
        if (servletOutputStream != null) {
            servletOutputStream.resetBuffer();
        }
        if (writer != null) {
            writer = new PrintWriter(servletOutputStream, false);
        }
    }

    @Override
    public boolean isCommitted() {
        return exchange.isResponseStarted();
    }

    @Override
    public void reset() {
        if (servletOutputStream != null) {
            servletOutputStream.resetBuffer();
        }
        writer = null;
        responseState = ResponseState.NONE;
        exchange.getResponseHeaders().clear();
        exchange.setResponseCode(200);
    }

    @Override
    public void setLocale(final Locale loc) {
        if (insideInclude || exchange.isResponseStarted()) {
            return;
        }
        this.locale = loc;
        exchange.getResponseHeaders().put(Headers.CONTENT_LANGUAGE, loc.getLanguage() + "-" + loc.getCountry());
        if (!charsetSet && writer == null) {
            final Map<String, String> localeCharsetMapping = servletContext.getDeployment().getDeploymentInfo().getLocaleCharsetMapping();
            // Match full language_country_variant first, then language_country,
            // then language only
            String charset = localeCharsetMapping.get(locale.toString());
            if (charset == null) {
                charset = localeCharsetMapping.get(locale.getLanguage() + "_"
                        + locale.getCountry());
                if (charset == null) {
                    charset = localeCharsetMapping.get(locale.getLanguage());
                }
            }
            if (charset != null) {
                this.charset = charset;
                if (contentType != null) {
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType());
                }
            }
        }

    }

    @Override
    public Locale getLocale() {
        if (locale != null) {
            return locale;
        }
        return Locale.getDefault();
    }

    public void responseDone() {
        if (responseDone) {
            return;
        }
        servletContext.updateSessionAccessTime(exchange);
        responseDone = true;
        if (writer != null) {
            writer.close();
        }
        if (servletOutputStream != null) {
            try {
                servletOutputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            exchange.endExchange();
        }
    }

    public boolean isInsideInclude() {
        return insideInclude;
    }

    public void setInsideInclude(final boolean insideInclude) {
        this.insideInclude = insideInclude;
    }

    public void setServletContext(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
    }

    public static HttpServletResponseImpl getResponseImpl(final ServletResponse response) {
        final HttpServletResponseImpl requestImpl;
        if (response instanceof HttpServletResponseImpl) {
            requestImpl = (HttpServletResponseImpl) response;
        } else if (response instanceof ServletResponseWrapper) {
            requestImpl = getResponseImpl(((ServletResponseWrapper) response).getResponse());
        } else {
            throw UndertowServletMessages.MESSAGES.responseWasNotOriginalOrWrapper(response);
        }
        return requestImpl;
    }

    public ServletContextImpl getServletContext() {
        return servletContext;
    }

    public static enum ResponseState {
        NONE,
        STREAM,
        WRITER
    }
}
