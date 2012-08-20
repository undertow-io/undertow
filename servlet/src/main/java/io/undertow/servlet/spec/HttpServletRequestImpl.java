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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.util.EmptyEnumeration;
import io.undertow.servlet.util.IteratorEnumeration;
import io.undertow.util.DateUtils;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

/**
 * The http servlet request implementation. This class is not thread safe
 *
 * @author Stuart Douglas
 */
public class HttpServletRequestImpl implements HttpServletRequest {

    public static final String ATTACHMENT_KEY = HttpServletRequestImpl.class.getName();

    private final BlockingHttpServerExchange exchange;

    private final HashMap<String, Object> attributes = new HashMap<String, Object>();

    private ServletInputStream servletInputStream;
    private BufferedReader reader;

    private Cookie[] cookies;

    public HttpServletRequestImpl(final BlockingHttpServerExchange exchange) {
        this.exchange = exchange;
    }

    public BlockingHttpServerExchange getExchange() {
        return exchange;
    }

    @Override
    public String getAuthType() {
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        if(cookies == null) {
            Map<String, io.undertow.server.handlers.Cookie> cookies = io.undertow.server.handlers.Cookie.getRequestCookies(exchange.getExchange());
            Cookie[] value = new Cookie[cookies.size()];
            int i = 0;
            for(Map.Entry<String, io.undertow.server.handlers.Cookie> entry : cookies.entrySet()) {
                io.undertow.server.handlers.Cookie cookie = entry.getValue();
                Cookie c = new Cookie(cookie.getName(), cookie.getValue());
                c.setDomain(cookie.getDomain());
                c.setHttpOnly(cookie.isHttpOnly());
                c.setMaxAge(cookie.getMaxAge());
                c.setPath(cookie.getPath());
                c.setSecure(cookie.isSecure());
                c.setVersion(cookie.getVersion());
                value[i++] = c;
            }
            this.cookies = value;
        }
        return cookies;
    }

    @Override
    public long getDateHeader(final String name) {
        String header = exchange.getExchange().getRequestHeaders().getFirst(name);
        if(header == null) {
            return -1;
        }
        Date date = DateUtils.parseDate(header);
        if(date == null) {
            throw UndertowServletMessages.MESSAGES.headerCannotBeConvertedToDate(header);
        }
        return date.getTime();
    }

    @Override
    public String getHeader(final String name) {
        HeaderMap headers = exchange.getExchange().getRequestHeaders();
        if(headers == null) {
            return null;
        }
        return headers.getFirst(name);
    }

    @Override
    public Enumeration<String> getHeaders(final String name) {
        Deque<String> headers = exchange.getExchange().getRequestHeaders().get(name);
        if(headers == null) {
            return EmptyEnumeration.instance();
        }
        return new IteratorEnumeration<String>(headers.iterator());
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return new IteratorEnumeration<String>(exchange.getExchange().getRequestHeaders().iterator());
    }

    @Override
    public int getIntHeader(final String name) {
        return Integer.parseInt(getHeader(name));
    }

    @Override
    public String getMethod() {
        return exchange.getExchange().getRequestMethod();
    }

    @Override
    public String getPathInfo() {
        return null;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getContextPath() {
        return null;
    }

    @Override
    public String getQueryString() {
        return null;
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public boolean isUserInRole(final String role) {
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public String getRequestURI() {
        return null;
    }

    @Override
    public StringBuffer getRequestURL() {
        return null;
    }

    @Override
    public String getServletPath() {
        return null;
    }

    @Override
    public HttpSession getSession(final boolean create) {
        return null;
    }

    @Override
    public HttpSession getSession() {
        return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public boolean authenticate(final HttpServletResponse response) throws IOException, ServletException {
        return false;
    }

    @Override
    public void login(final String username, final String password) throws ServletException {

    }

    @Override
    public void logout() throws ServletException {

    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return null;
    }

    @Override
    public Part getPart(final String name) throws IOException, ServletException {
        return null;
    }

    @Override
    public Object getAttribute(final String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return new IteratorEnumeration<String>(attributes.keySet().iterator());
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public void setCharacterEncoding(final String env) throws UnsupportedEncodingException {

    }

    @Override
    public int getContentLength() {
        final String contentLength = getHeader(Headers.CONTENT_LENGTH);
        if (contentLength == null || contentLength.isEmpty()) {
            return -1;
        }
        return Integer.parseInt(contentLength);
    }

    @Override
    public String getContentType() {
        return getHeader(Headers.CONTENT_TYPE);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (servletInputStream == null) {
            if(reader != null) {
                throw UndertowServletMessages.MESSAGES.getReaderAlreadyCalled();
            }
            servletInputStream = new ServletInputStreamImpl(exchange.getInputStream());
        }
        return servletInputStream;
    }

    @Override
    public String getParameter(final String name) {
        Deque<String> params = exchange.getExchange().getQueryParameters().get(name);
        if(params == null) {
            return null;
        }
        return params.getFirst();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return new IteratorEnumeration<String>(exchange.getExchange().getQueryParameters().keySet().iterator());
    }

    @Override
    public String[] getParameterValues(final String name) {
        Deque<String> params = exchange.getExchange().getQueryParameters().get(name);
        if(params == null) {
            return null;
        }
        return params.toArray(new String[params.size()]);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        final Map<String, String[] > ret = new HashMap<String, String[]>();
        for(Map.Entry<String, Deque<String>> entry : exchange.getExchange().getQueryParameters().entrySet()) {
            ret.put(entry.getKey(), entry.getValue().toArray(new String[entry.getValue().size()]));
        }
        return ret;
    }

    @Override
    public String getProtocol() {
        return exchange.getExchange().getProtocol();
    }

    @Override
    public String getScheme() {
        return exchange.getExchange().getRequestScheme();
    }

    @Override
    public String getServerName() {
        return null;
    }

    @Override
    public int getServerPort() {
        return 0;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            if(servletInputStream != null) {
                throw UndertowServletMessages.MESSAGES.getInputStreamAlreadyCalled();
            }
            reader = new BufferedReader(new InputStreamReader(exchange.getInputStream()));
        }
        return reader;
    }

    @Override
    public String getRemoteAddr() {
        return exchange.getExchange().getSourceAddress().getAddress().getHostAddress();
    }

    @Override
    public String getRemoteHost() {
        return exchange.getExchange().getSourceAddress().getHostName();
    }

    @Override
    public void setAttribute(final String name, final Object o) {
        attributes.put(name, o);
    }

    @Override
    public void removeAttribute(final String name) {
        attributes.remove(name);
    }

    @Override
    public Locale getLocale() {
        return null;
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return null;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        return null;
    }

    @Override
    public String getRealPath(final String path) {
        return null;
    }

    @Override
    public int getRemotePort() {
        return exchange.getExchange().getSourceAddress().getPort();
    }

    @Override
    public String getLocalName() {
        return null;
    }

    @Override
    public String getLocalAddr() {
        return null;
    }

    @Override
    public int getLocalPort() {
        return 0;
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return null;
    }

    @Override
    public AsyncContext startAsync(final ServletRequest servletRequest, final ServletResponse servletResponse) throws IllegalStateException {
        return null;
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        return null;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return null;
    }
}
