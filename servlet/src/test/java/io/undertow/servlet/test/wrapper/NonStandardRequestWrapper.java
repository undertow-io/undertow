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

package io.undertow.servlet.test.wrapper;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

/**
 * @author Stuart Douglas
 */
public class NonStandardRequestWrapper implements HttpServletRequest {

    private ServletRequest request;

    /**
     * Creates a ServletRequest adaptor wrapping the given request object.
     * @throws java.lang.IllegalArgumentException if the request is null
     */
    public NonStandardRequestWrapper(ServletRequest request) {
        this.request = checkNotNullParam("request", request);
    }

    /**
     * Return the wrapped request object.
     */
    public ServletRequest getRequest() {
        return this.request;
    }


    /**
     * Sets the request object being wrapped.
     * @throws java.lang.IllegalArgumentException if the request is null.
     */
    public void setRequest(ServletRequest request) {
        this.request = checkNotNullParam("request", request);
    }


    /**
     * The default behavior of this method is to call getAttribute(String name)
     * on the wrapped request object.
     */
    public Object getAttribute(String name) {
        return this.request.getAttribute(name);
    }


    /**
     * The default behavior of this method is to return getAttributeNames()
     * on the wrapped request object.
     */
    public Enumeration<String> getAttributeNames() {
        return this.request.getAttributeNames();
    }


    /**
     * The default behavior of this method is to return getCharacterEncoding()
     * on the wrapped request object.
     */
    public String getCharacterEncoding() {
        return this.request.getCharacterEncoding();
    }


    /**
     * The default behavior of this method is to set the character encoding
     * on the wrapped request object.
     */
    public void setCharacterEncoding(String enc)
            throws UnsupportedEncodingException {
        this.request.setCharacterEncoding(enc);
    }


    /**
     * The default behavior of this method is to return getContentLength()
     * on the wrapped request object.
     */
    public int getContentLength() {
        return this.request.getContentLength();
    }

    /**
     * The default behavior of this method is to return getContentLengthLong()
     * on the wrapped request object.
     *
     * @since Servlet 3.1
     */
    public long getContentLengthLong() {
        return this.request.getContentLengthLong();
    }


    /**
     * The default behavior of this method is to return getContentType()
     * on the wrapped request object.
     */
    public String getContentType() {
        return this.request.getContentType();
    }


    /**
     * The default behavior of this method is to return getInputStream()
     * on the wrapped request object.
     */
    public ServletInputStream getInputStream() throws IOException {
        return this.request.getInputStream();
    }


    /**
     * The default behavior of this method is to return
     * getParameter(String name) on the wrapped request object.
     */
    public String getParameter(String name) {
        return this.request.getParameter(name);
    }


    /**
     * The default behavior of this method is to return getParameterMap()
     * on the wrapped request object.
     */
    public Map<String, String[]> getParameterMap() {
        return this.request.getParameterMap();
    }


    /**
     * The default behavior of this method is to return getParameterNames()
     * on the wrapped request object.
     */
    public Enumeration<String> getParameterNames() {
        return this.request.getParameterNames();
    }


    /**
     * The default behavior of this method is to return
     * getParameterValues(String name) on the wrapped request object.
     */
    public String[] getParameterValues(String name) {
        return this.request.getParameterValues(name);
    }


    /**
     * The default behavior of this method is to return getProtocol()
     * on the wrapped request object.
     */
    public String getProtocol() {
        return this.request.getProtocol();
    }


    /**
     * The default behavior of this method is to return getScheme()
     * on the wrapped request object.
     */
    public String getScheme() {
        return this.request.getScheme();
    }


    /**
     * The default behavior of this method is to return getServerName()
     * on the wrapped request object.
     */
    public String getServerName() {
        return this.request.getServerName();
    }


    /**
     * The default behavior of this method is to return getServerPort()
     * on the wrapped request object.
     */
    public int getServerPort() {
        return this.request.getServerPort();
    }


    /**
     * The default behavior of this method is to return getReader()
     * on the wrapped request object.
     */
    public BufferedReader getReader() throws IOException {
        return this.request.getReader();
    }


    /**
     * The default behavior of this method is to return getRemoteAddr()
     * on the wrapped request object.
     */
    public String getRemoteAddr() {
        return this.request.getRemoteAddr();
    }


    /**
     * The default behavior of this method is to return getRemoteHost()
     * on the wrapped request object.
     */
    public String getRemoteHost() {
        return this.request.getRemoteHost();
    }


    /**
     * The default behavior of this method is to return
     * setAttribute(String name, Object o) on the wrapped request object.
     */
    public void setAttribute(String name, Object o) {
        this.request.setAttribute(name, o);
    }


    /**
     * The default behavior of this method is to call
     * removeAttribute(String name) on the wrapped request object.
     */
    public void removeAttribute(String name) {
        this.request.removeAttribute(name);
    }


    /**
     * The default behavior of this method is to return getLocale()
     * on the wrapped request object.
     */
    public Locale getLocale() {
        return this.request.getLocale();
    }


    /**
     * The default behavior of this method is to return getLocales()
     * on the wrapped request object.
     */
    public Enumeration<Locale> getLocales() {
        return this.request.getLocales();
    }


    /**
     * The default behavior of this method is to return isSecure()
     * on the wrapped request object.
     */
    public boolean isSecure() {
        return this.request.isSecure();
    }


    /**
     * The default behavior of this method is to return
     * getRequestDispatcher(String path) on the wrapped request object.
     */
    public RequestDispatcher getRequestDispatcher(String path) {
        return this.request.getRequestDispatcher(path);
    }


    /**
     * The default behavior of this method is to return
     * getRemotePort() on the wrapped request object.
     *
     * @since Servlet 2.4
     */
    public int getRemotePort(){
        return this.request.getRemotePort();
    }


    /**
     * The default behavior of this method is to return
     * getLocalName() on the wrapped request object.
     *
     * @since Servlet 2.4
     */
    public String getLocalName(){
        return this.request.getLocalName();
    }


    /**
     * The default behavior of this method is to return
     * getLocalAddr() on the wrapped request object.
     *
     * @since Servlet 2.4
     */
    public String getLocalAddr(){
        return this.request.getLocalAddr();
    }


    /**
     * The default behavior of this method is to return
     * getLocalPort() on the wrapped request object.
     *
     * @since Servlet 2.4
     */
    public int getLocalPort(){
        return this.request.getLocalPort();
    }


    /**
     * Gets the servlet context to which the wrapped servlet request was last
     * dispatched.
     *
     * @return the servlet context to which the wrapped servlet request was
     * last dispatched
     *
     * @since Servlet 3.0
     */
    public ServletContext getServletContext() {
        return request.getServletContext();
    }


    /**
     * The default behavior of this method is to invoke
     * {@link ServletRequest#startAsync} on the wrapped request object.
     *
     * @return the (re)initialized AsyncContext
     *
     * @throws IllegalStateException if the request is within the scope of
     * a filter or servlet that does not support asynchronous operations
     * (that is, {@link #isAsyncSupported} returns false),
     * or if this method is called again without any asynchronous dispatch
     * (resulting from one of the {@link AsyncContext#dispatch} methods),
     * is called outside the scope of any such dispatch, or is called again
     * within the scope of the same dispatch, or if the response has
     * already been closed
     *
     * @see ServletRequest#startAsync
     *
     * @since Servlet 3.0
     */
    public AsyncContext startAsync() throws IllegalStateException {
        return request.startAsync();
    }


    /**
     * The default behavior of this method is to invoke
     * {@link ServletRequest#startAsync(ServletRequest, ServletResponse)}
     * on the wrapped request object.
     *
     * @param servletRequest the ServletRequest used to initialize the
     * AsyncContext
     * @param servletResponse the ServletResponse used to initialize the
     * AsyncContext
     *
     * @return the (re)initialized AsyncContext
     *
     * @throws IllegalStateException if the request is within the scope of
     * a filter or servlet that does not support asynchronous operations
     * (that is, {@link #isAsyncSupported} returns false),
     * or if this method is called again without any asynchronous dispatch
     * (resulting from one of the {@link AsyncContext#dispatch} methods),
     * is called outside the scope of any such dispatch, or is called again
     * within the scope of the same dispatch, or if the response has
     * already been closed
     *
     * @see ServletRequest#startAsync(ServletRequest, ServletResponse)
     *
     * @since Servlet 3.0
     */
    public AsyncContext startAsync(ServletRequest servletRequest,
                                   ServletResponse servletResponse)
            throws IllegalStateException {
        return request.startAsync(servletRequest, servletResponse);
    }


    /**
     * Checks if the wrapped request has been put into asynchronous mode.
     *
     * @return true if this request has been put into asynchronous mode,
     * false otherwise
     *
     * @see ServletRequest#isAsyncStarted
     *
     * @since Servlet 3.0
     */
    public boolean isAsyncStarted() {
        return request.isAsyncStarted();
    }


    /**
     * Checks if the wrapped request supports asynchronous operation.
     *
     * @return true if this request supports asynchronous operation, false
     * otherwise
     *
     * @see ServletRequest#isAsyncSupported
     *
     * @since Servlet 3.0
     */
    public boolean isAsyncSupported() {
        return request.isAsyncSupported();
    }


    /**
     * Gets the AsyncContext that was created or reinitialized by the
     * most recent invocation of {@link #startAsync} or
     * {@link #startAsync(ServletRequest,ServletResponse)} on the wrapped
     * request.
     *
     * @return the AsyncContext that was created or reinitialized by the
     * most recent invocation of {@link #startAsync} or
     * {@link #startAsync(ServletRequest,ServletResponse)} on
     * the wrapped request
     *
     * @throws IllegalStateException if this request has not been put
     * into asynchronous mode, i.e., if neither {@link #startAsync} nor
     * {@link #startAsync(ServletRequest,ServletResponse)} has been called
     *
     * @see ServletRequest#getAsyncContext
     *
     * @since Servlet 3.0
     */
    public AsyncContext getAsyncContext() {
        return request.getAsyncContext();
    }


    /**
     * Checks (recursively) if this ServletRequestWrapper wraps the given
     * {@link ServletRequest} instance.
     *
     * @param wrapped the ServletRequest instance to search for
     *
     * @return true if this ServletRequestWrapper wraps the
     * given ServletRequest instance, false otherwise
     *
     * @since Servlet 3.0
     */
    public boolean isWrapperFor(ServletRequest wrapped) {
        if (request == wrapped) {
            return true;
        } else if (request instanceof ServletRequestWrapper) {
            return ((ServletRequestWrapper) request).isWrapperFor(wrapped);
        } else {
            return false;
        }
    }


    /**
     * Checks (recursively) if this ServletRequestWrapper wraps a
     * {@link ServletRequest} of the given class type.
     *
     * @param wrappedType the ServletRequest class type to
     * search for
     *
     * @return true if this ServletRequestWrapper wraps a
     * ServletRequest of the given class type, false otherwise
     *
     * @throws IllegalArgumentException if the given class does not
     * implement {@link ServletRequest}
     *
     * @since Servlet 3.0
     */
    public boolean isWrapperFor(Class<?> wrappedType) {
        if (!ServletRequest.class.isAssignableFrom(wrappedType)) {
            throw new IllegalArgumentException("Given class " +
                wrappedType.getName() + " not a subinterface of " +
                ServletRequest.class.getName());
        }
        if (wrappedType.isAssignableFrom(request.getClass())) {
            return true;
        } else if (request instanceof ServletRequestWrapper) {
            return ((ServletRequestWrapper) request).isWrapperFor(wrappedType);
        } else {
            return false;
        }
    }


    /**
     * Gets the dispatcher type of the wrapped request.
     *
     * @return the dispatcher type of the wrapped request
     *
     * @see ServletRequest#getDispatcherType
     *
     * @since Servlet 3.0
     */
    public DispatcherType getDispatcherType() {
        return request.getDispatcherType();
    }


    private HttpServletRequest _getHttpServletRequest() {
        return (HttpServletRequest) getRequest();
    }

    /**
     * The default behavior of this method is to return getAuthType()
     * on the wrapped request object.
     */
    @Override
    public String getAuthType() {
        return this._getHttpServletRequest().getAuthType();
    }

    /**
     * The default behavior of this method is to return getCookies()
     * on the wrapped request object.
     */
    @Override
    public Cookie[] getCookies() {
        return this._getHttpServletRequest().getCookies();
    }

    /**
     * The default behavior of this method is to return getDateHeader(String name)
     * on the wrapped request object.
     */
    @Override
    public long getDateHeader(String name) {
        return this._getHttpServletRequest().getDateHeader(name);
    }

    /**
     * The default behavior of this method is to return getHeader(String name)
     * on the wrapped request object.
     */
    @Override
    public String getHeader(String name) {
        return this._getHttpServletRequest().getHeader(name);
    }

    /**
     * The default behavior of this method is to return getHeaders(String name)
     * on the wrapped request object.
     */
    @Override
    public Enumeration<String> getHeaders(String name) {
        return this._getHttpServletRequest().getHeaders(name);
    }

    /**
     * The default behavior of this method is to return getHeaderNames()
     * on the wrapped request object.
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        return this._getHttpServletRequest().getHeaderNames();
    }

    /**
     * The default behavior of this method is to return
     * getIntHeader(String name) on the wrapped request object.
     */
    @Override
     public int getIntHeader(String name) {
        return this._getHttpServletRequest().getIntHeader(name);
    }

    /**
     * The default behavior of this method is to return getMethod()
     * on the wrapped request object.
     */
    @Override
    public String getMethod() {
        return this._getHttpServletRequest().getMethod();
    }

    /**
     * The default behavior of this method is to return getPathInfo()
     * on the wrapped request object.
     */
    @Override
    public String getPathInfo() {
        return this._getHttpServletRequest().getPathInfo();
    }

    /**
     * The default behavior of this method is to return getPathTranslated()
     * on the wrapped request object.
     */
    @Override
    public String getPathTranslated() {
        return this._getHttpServletRequest().getPathTranslated();
    }

    /**
     * The default behavior of this method is to return getContextPath()
     * on the wrapped request object.
     */
    @Override
    public String getContextPath() {
        return this._getHttpServletRequest().getContextPath();
    }

    /**
     * The default behavior of this method is to return getQueryString()
     * on the wrapped request object.
     */
    @Override
    public String getQueryString() {
        return this._getHttpServletRequest().getQueryString();
    }

    /**
     * The default behavior of this method is to return getRemoteUser()
     * on the wrapped request object.
     */
    @Override
    public String getRemoteUser() {
        return this._getHttpServletRequest().getRemoteUser();
    }

    /**
     * The default behavior of this method is to return isUserInRole(String role)
     * on the wrapped request object.
     */
    @Override
    public boolean isUserInRole(String role) {
        return this._getHttpServletRequest().isUserInRole(role);
    }

    /**
     * The default behavior of this method is to return getUserPrincipal()
     * on the wrapped request object.
     */
    @Override
    public java.security.Principal getUserPrincipal() {
        return this._getHttpServletRequest().getUserPrincipal();
    }

    /**
     * The default behavior of this method is to return getRequestedSessionId()
     * on the wrapped request object.
     */
    @Override
    public String getRequestedSessionId() {
        return this._getHttpServletRequest().getRequestedSessionId();
    }

    /**
     * The default behavior of this method is to return getRequestURI()
     * on the wrapped request object.
     */
    @Override
    public String getRequestURI() {
        return this._getHttpServletRequest().getRequestURI();
    }

    /**
     * The default behavior of this method is to return getRequestURL()
     * on the wrapped request object.
     */
    @Override
    public StringBuffer getRequestURL() {
        return this._getHttpServletRequest().getRequestURL();
    }

    /**
     * The default behavior of this method is to return getServletPath()
     * on the wrapped request object.
     */
    @Override
    public String getServletPath() {
        return this._getHttpServletRequest().getServletPath();
    }

    /**
     * The default behavior of this method is to return getSession(boolean create)
     * on the wrapped request object.
     */
    @Override
    public HttpSession getSession(boolean create) {
        return this._getHttpServletRequest().getSession(create);
    }

    /**
     * The default behavior of this method is to return getSession()
     * on the wrapped request object.
     */
    @Override
    public HttpSession getSession() {
        return this._getHttpServletRequest().getSession();
    }

    /**
     * The default behavior of this method is to return changeSessionId()
     * on the wrapped request object.
     */
    @Override
    public String changeSessionId() {
        return this._getHttpServletRequest().changeSessionId();
    }

    /**
     * The default behavior of this method is to return isRequestedSessionIdValid()
     * on the wrapped request object.
     */
    @Override
    public boolean isRequestedSessionIdValid() {
        return this._getHttpServletRequest().isRequestedSessionIdValid();
    }

    /**
     * The default behavior of this method is to return isRequestedSessionIdFromCookie()
     * on the wrapped request object.
     */
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return this._getHttpServletRequest().isRequestedSessionIdFromCookie();
    }

    /**
     * The default behavior of this method is to return isRequestedSessionIdFromURL()
     * on the wrapped request object.
     */
    @Override
    public boolean isRequestedSessionIdFromURL() {
        return this._getHttpServletRequest().isRequestedSessionIdFromURL();
    }

    /**
     * The default behavior of this method is to call authenticate on the
     * wrapped request object.
     *
     * @since Servlet 3.0
     */
    @Override
    public boolean authenticate(HttpServletResponse response)
            throws IOException, ServletException {
        return this._getHttpServletRequest().authenticate(response);
    }

    /**
     * The default behavior of this method is to call login on the wrapped
     * request object.
     *
     * @since Servlet 3.0
     */
    @Override
    public void login(String username, String password)
            throws ServletException {
        this._getHttpServletRequest().login(username,password);
    }

    /**
     * The default behavior of this method is to call login on the wrapped
     * request object.
     *
     * @since Servlet 3.0
     */
    @Override
    public void logout() throws ServletException {
        this._getHttpServletRequest().logout();
    }

    /**
     * The default behavior of this method is to call getParts on the wrapped
     * request object.
     *
     * <p>Any changes to the returned <code>Collection</code> must not
     * affect this <code>HttpServletRequestWrapper</code>.
     *
     * @since Servlet 3.0
     */
    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return this._getHttpServletRequest().getParts();
    }

    /**
     * The default behavior of this method is to call getPart on the wrapped
     * request object.
     *
     * @since Servlet 3.0
     */
    @Override
    public Part getPart(String name) throws IOException, ServletException {
        return this._getHttpServletRequest().getPart(name);

    }

    /**
     * Create an instance of <code>HttpUpgradeHandler</code> for an given
     * class and uses it for the http protocol upgrade processing.
     *
     * @since Servlet 3.1
     */
    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        return this._getHttpServletRequest().upgrade(handlerClass);
    }

    @Override
    public String getRequestId() {
        return this._getHttpServletRequest().getRequestId();
    }

    @Override
    public String getProtocolRequestId() {
        return this._getHttpServletRequest().getProtocolRequestId();
    }

    @Override
    public ServletConnection getServletConnection() {
        return this._getHttpServletRequest().getServletConnection();
    }
}
