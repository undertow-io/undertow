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

package io.undertow.servlet.spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.CanonicalPathUtils;
import io.undertow.util.DateUtils;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.RedirectBuilder;
import io.undertow.util.StatusCodes;


/**
 * @author Stuart Douglas
 */
public final class HttpServletResponseImpl implements HttpServletResponse {

    private final HttpServerExchange exchange;
    private final ServletContextImpl originalServletContext;
    private volatile ServletContextImpl servletContext;

    private ServletOutputStreamImpl servletOutputStream;
    private ResponseState responseState = ResponseState.NONE;
    private PrintWriter writer;
    private Integer bufferSize;
    private long contentLength = -1;
    private boolean insideInclude = false;
    private Locale locale;
    private boolean responseDone = false;

    private boolean ignoredFlushPerformed = false;

    private boolean treatAsCommitted = false;

    private boolean charsetSet = false; //if a content type has been set either implicitly or implicitly
    private String contentType;
    private String charset;

    public HttpServletResponseImpl(final HttpServerExchange exchange, final ServletContextImpl servletContext) {
        this.exchange = exchange;
        this.servletContext = servletContext;
        this.originalServletContext = servletContext;
    }

    public HttpServerExchange getExchange() {
        return exchange;
    }

    @Override
    public void addCookie(final Cookie cookie) {
        if (insideInclude) {
            return;
        }
        final ServletCookieAdaptor servletCookieAdaptor = new ServletCookieAdaptor(cookie);
        if (cookie.getVersion() == 0) {
            servletCookieAdaptor.setVersion(servletContext.getDeployment().getDeploymentInfo().getDefaultCookieVersion());
        }
        exchange.setResponseCookie(servletCookieAdaptor);
    }

    @Override
    public boolean containsHeader(final String name) {
        return exchange.getResponseHeaders().contains(name);
    }

    @Override
    public String encodeUrl(final String url) {
        return encodeURL(url);
    }

    @Override
    public String encodeRedirectUrl(final String url) {
        return encodeRedirectURL(url);
    }

    @Override
    public void sendError(final int sc, final String msg) throws IOException {
        if(insideInclude) {
            //not 100% sure this is the correct action
            return;
        }
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (responseStarted()) {
            if(src.getErrorCode() > 0) {
                return; //error already set
            }
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }
        if(servletContext.getDeployment().getDeploymentInfo().isSendCustomReasonPhraseOnError()) {
            exchange.setReasonPhrase(msg);
        }
        writer = null;
        responseState = ResponseState.NONE;
        exchange.setStatusCode(sc);
        if(src.isRunningInsideHandler()) {
            //all we do is set the error on the context, we handle it when the request is returned
            treatAsCommitted = true;
            src.setError(sc, msg);
        } else {
            //if the src is null there is no outer handler, as we are in an asnc request
            doErrorDispatch(sc, msg);
        }
    }

    public void doErrorDispatch(int sc, String error) throws IOException {
        writer = null;
        responseState = ResponseState.NONE;
        resetBuffer();
        treatAsCommitted = false;
        final String location = servletContext.getDeployment().getErrorPages().getErrorLocation(sc);
        if (location != null) {
            RequestDispatcherImpl requestDispatcher = new RequestDispatcherImpl(location, servletContext);
            final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            try {
                requestDispatcher.error(servletRequestContext, servletRequestContext.getServletRequest(), servletRequestContext.getServletResponse(), exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getCurrentServlet().getManagedServlet().getServletInfo().getName(), error);
            } catch (ServletException e) {
                throw new RuntimeException(e);
            }
        } else if (error != null) {
            setContentType("text/html");
            setCharacterEncoding("UTF-8");
            if(servletContext.getDeployment().getDeploymentInfo().isEscapeErrorMessage()) {
                getWriter().write("<html><head><title>Error</title></head><body>" + escapeHtml(error) + "</body></html>");
            } else {
                getWriter().write("<html><head><title>Error</title></head><body>" + error + "</body></html>");
            }
            getWriter().close();
        }
        responseDone();
    }

    @Override
    public void sendError(final int sc) throws IOException {
        sendError(sc, StatusCodes.getReason(sc));
    }

    @Override
    public void sendRedirect(final String location) throws IOException {
        if (responseStarted()) {
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }
        resetBuffer();
        setStatus(StatusCodes.FOUND);
        String realPath;
        if (location.contains("://")) {//absolute url
            exchange.getResponseHeaders().put(Headers.LOCATION, location);
        } else {
            if (location.startsWith("/")) {
                realPath = location;
            } else {
                String current = exchange.getRelativePath();
                int lastSlash = current.lastIndexOf("/");
                if (lastSlash != -1) {
                    current = current.substring(0, lastSlash + 1);
                }
                realPath = CanonicalPathUtils.canonicalize(servletContext.getContextPath() + current + location);
            }
            String loc = exchange.getRequestScheme() + "://" + exchange.getHostAndPort() + realPath;
            exchange.getResponseHeaders().put(Headers.LOCATION, loc);
        }
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
        if(name == null) {
            throw UndertowServletMessages.MESSAGES.headerNameWasNull();
        }
        setHeader(HttpString.tryFromString(name), value);
    }


    public void setHeader(final HttpString name, final String value) {
        if(name == null) {
            throw UndertowServletMessages.MESSAGES.headerNameWasNull();
        }
        if (insideInclude || ignoredFlushPerformed) {
            return;
        }
        if(name.equals(Headers.CONTENT_TYPE)) {
            setContentType(value);
        } else {
            exchange.getResponseHeaders().put(name, value);
        }
    }

    @Override
    public void addHeader(final String name, final String value) {
        if(name == null) {
            throw UndertowServletMessages.MESSAGES.headerNameWasNull();
        }
        addHeader(HttpString.tryFromString(name), value);
    }

    public void addHeader(final HttpString name, final String value) {
        if(name == null) {
            throw UndertowServletMessages.MESSAGES.headerNameWasNull();
        }
        if (insideInclude || ignoredFlushPerformed || treatAsCommitted) {
            return;
        }
        if(name.equals(Headers.CONTENT_TYPE) && !exchange.getResponseHeaders().contains(Headers.CONTENT_TYPE)) {
            setContentType(value);
        } else {
            exchange.getResponseHeaders().add(name, value);
        }
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
        if (insideInclude || treatAsCommitted) {
            return;
        }
        if (responseStarted()) {
            return;
        }
        exchange.setStatusCode(sc);
    }

    @Override
    public void setStatus(final int sc, final String sm) {
        setStatus(sc);
        if(!insideInclude && servletContext.getDeployment().getDeploymentInfo().isSendCustomReasonPhraseOnError()) {
            exchange.setReasonPhrase(sm);
        }
    }

    @Override
    public int getStatus() {
        return exchange.getStatusCode();
    }

    @Override
    public String getHeader(final String name) {
        return exchange.getResponseHeaders().getFirst(name);
    }

    @Override
    public Collection<String> getHeaders(final String name) {
        HeaderValues headers = exchange.getResponseHeaders().get(name);
        if(headers == null) {
            return Collections.emptySet();
        }
        return new ArrayList<>(headers);
    }

    @Override
    public Collection<String> getHeaderNames() {
        final Set<String> headers = new HashSet<>();
        for (final HttpString i : exchange.getResponseHeaders().getHeaderNames()) {
            headers.add(i.toString());
        }
        return headers;
    }

    @Override
    public String getCharacterEncoding() {
        if (charset == null) {
            return servletContext.getDeployment().getDeploymentInfo().getDefaultEncoding();
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
    public ServletOutputStream getOutputStream() {
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
            if (!charsetSet) {
                //servet 5.5
                setCharacterEncoding(getCharacterEncoding());
            }
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
                servletOutputStream = new ServletOutputStreamImpl(exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY));
            } else {
                servletOutputStream = new ServletOutputStreamImpl(exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY), bufferSize);
            }
        }
    }

    @Override
    public void setCharacterEncoding(final String charset) {
        if (insideInclude || responseStarted() || writer != null || isCommitted()) {
            return;
        }
        charsetSet = charset != null;
        this.charset = charset;
        if (contentType != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType());
        }
    }

    @Override
    public void setContentLength(final int len) {
        if (insideInclude || responseStarted()) {
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Integer.toString(len));
        this.contentLength = (long) len;
    }

    @Override
    public void setContentLengthLong(final long len) {
        if (insideInclude || responseStarted()) {
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Long.toString(len));
        this.contentLength = len;
    }

    boolean isIgnoredFlushPerformed() {
        return ignoredFlushPerformed;
    }

    void setIgnoredFlushPerformed(boolean ignoredFlushPerformed) {
        this.ignoredFlushPerformed = ignoredFlushPerformed;
    }

    private boolean responseStarted() {
        return exchange.isResponseStarted() || ignoredFlushPerformed || treatAsCommitted;
    }

    @Override
    public void setContentType(final String type) {
        if (type == null || insideInclude || responseStarted()) {
            return;
        }
        ContentTypeInfo ct = servletContext.parseContentType(type);
        contentType = ct.getContentType();
        boolean useCharset = false;
        if(ct.getCharset() != null && writer == null && !isCommitted()) {
            charset = ct.getCharset();
            charsetSet = true;
            useCharset = true;
        }
        if(useCharset || !charsetSet) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ct.getHeader());
        } else if(ct.getCharset() == null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ct.getHeader() + "; charset=" + charset);
        }else {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ct.getContentType() + "; charset=" + charset);
        }
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
        if(treatAsCommitted) {
            return;
        }
        if (writer != null) {
            writer.close();
        } else {
            if (servletOutputStream == null) {
                createOutputStream();
            }
            //close also flushes
            servletOutputStream.close();
        }
    }

    public void freeResources() throws IOException {
        if(writer != null) {
            writer.close();
        }
        if(servletOutputStream != null) {
            servletOutputStream.close();
        }
    }

    @Override
    public void resetBuffer() {
        if (servletOutputStream != null) {
            servletOutputStream.resetBuffer();
        }
        if (writer != null) {
            final ServletPrintWriter servletPrintWriter;
            try {
                servletPrintWriter = new ServletPrintWriter(servletOutputStream, getCharacterEncoding());
            writer = ServletPrintWriterDelegate.newInstance(servletPrintWriter);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e); //should never happen
            }
        }
    }

    @Override
    public boolean isCommitted() {
        return responseStarted();
    }

    @Override
    public void reset() {
        if (servletOutputStream != null) {
            servletOutputStream.resetBuffer();
        }
        writer = null;
        responseState = ResponseState.NONE;
        exchange.getResponseHeaders().clear();
        exchange.setStatusCode(StatusCodes.OK);
        treatAsCommitted = false;
    }

    @Override
    public void setLocale(final Locale loc) {
        if (insideInclude || responseStarted()) {
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
        if (responseDone || treatAsCommitted) {
            return;
        }
        responseDone = true;
        try {
            closeStreamAndWriter();
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
        } finally {
            servletContext.updateSessionAccessTime(exchange);
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

    public ServletContextImpl getServletContext() {
        return servletContext;
    }

    public String encodeURL(String url) {
        String absolute = toAbsolute(url);
        if (isEncodeable(absolute)) {
            // W3c spec clearly said
            if (url.equalsIgnoreCase("")) {
                url = absolute;
            }
            return originalServletContext.getSessionConfig().rewriteUrl(url, servletContext.getSession(originalServletContext, exchange, true).getId());
        } else {
            return (url);
        }

    }

    /**
     * Encode the session identifier associated with this response
     * into the specified redirect URL, if necessary.
     *
     * @param url URL to be encoded
     */
    public String encodeRedirectURL(String url) {
        if (isEncodeable(toAbsolute(url))) {
            return originalServletContext.getSessionConfig().rewriteUrl(url, servletContext.getSession(originalServletContext, exchange, true).getId());
        } else {
            return url;
        }
    }

    /**
     * Convert (if necessary) and return the absolute URL that represents the
     * resource referenced by this possibly relative URL.  If this URL is
     * already absolute, return it unchanged.
     *
     * @param location URL to be (possibly) converted and then returned
     * @throws IllegalArgumentException if a MalformedURLException is
     *                                  thrown when converting the relative URL to an absolute one
     */
    private String toAbsolute(String location) {

        if (location == null) {
            return location;
        }

        boolean leadingSlash = location.startsWith("/");

        if (leadingSlash || !hasScheme(location)) {
            return RedirectBuilder.redirect(exchange, location, false);
        } else {
            return location;
        }

    }

    /**
     * Determine if a URI string has a <code>scheme</code> component.
     */
    private boolean hasScheme(String uri) {
        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char c = uri.charAt(i);
            if (c == ':') {
                return i > 0;
            } else if (!Character.isLetterOrDigit(c) &&
                    (c != '+' && c != '-' && c != '.')) {
                return false;
            }
        }
        return false;
    }

    /**
     * Return <code>true</code> if the specified URL should be encoded with
     * a session identifier.  This will be true if all of the following
     * conditions are met:
     * <ul>
     * <li>The request we are responding to asked for a valid session
     * <li>The requested session ID was not received via a cookie
     * <li>The specified URL points back to somewhere within the web
     * application that is responding to this request
     * </ul>
     *
     * @param location Absolute URL to be validated
     */
    private boolean isEncodeable(final String location) {

        if (location == null)
            return (false);

        // Is this an intra-document reference?
        if (location.startsWith("#"))
            return (false);

        // Are we in a valid session that is not using cookies?
        final HttpServletRequestImpl hreq = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getOriginalRequest();

        // Is URL encoding permitted
        if (!originalServletContext.getEffectiveSessionTrackingModes().contains(SessionTrackingMode.URL)) {
            return false;
        }

        final HttpSession session = hreq.getSession(false);
        if (session == null) {
            return false;
        } else if(hreq.isRequestedSessionIdFromCookie()) {
            return false;
        } else if (!hreq.isRequestedSessionIdFromURL() && !session.isNew()) {
            return false;
        }

        return doIsEncodeable(hreq, session, location);
    }

    private boolean doIsEncodeable(HttpServletRequestImpl hreq, HttpSession session,
                                   String location) {
        // Is this a valid absolute URL?
        URL url = null;
        try {
            url = new URL(location);
        } catch (MalformedURLException e) {
            return false;
        }

        // Does this URL match down to (and including) the context path?
        if (!hreq.getScheme().equalsIgnoreCase(url.getProtocol())) {
            return false;
        }
        if (!hreq.getServerName().equalsIgnoreCase(url.getHost())) {
            return false;
        }
        int serverPort = hreq.getServerPort();
        if (serverPort == -1) {
            if ("https".equals(hreq.getScheme())) {
                serverPort = 443;
            } else {
                serverPort = 80;
            }
        }
        int urlPort = url.getPort();
        if (urlPort == -1) {
            if ("https".equals(url.getProtocol())) {
                urlPort = 443;
            } else {
                urlPort = 80;
            }
        }
        if (serverPort != urlPort) {
            return false;
        }

        String file = url.getFile();
        if (file == null) {
            return false;
        }
        String tok = originalServletContext.getSessionCookieConfig().getName().toLowerCase() + "=" + session.getId();
        if (file.contains(tok)) {
            return false;
        }

        // This URL belongs to our web application, so it is encodeable
        return true;

    }

    public long getContentLength() {
        return contentLength;
    }

    public enum ResponseState {
        NONE,
        STREAM,
        WRITER
    }

    private static String escapeHtml(String msg) {
        return msg.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;");
    }

    public boolean isTreatAsCommitted() {
        return treatAsCommitted;
    }
}
