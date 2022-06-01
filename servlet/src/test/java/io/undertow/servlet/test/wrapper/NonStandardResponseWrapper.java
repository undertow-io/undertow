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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class NonStandardResponseWrapper implements HttpServletResponse {


    private ServletResponse response;

    /**
     * Creates a ServletResponse adaptor wrapping the given response object.
     *
     * @throws java.lang.IllegalArgumentException
     *          if the response is null.
     */
    public NonStandardResponseWrapper(ServletResponse response) {
        this.response = checkNotNullParam("response", response);
    }

    /**
     * Return the wrapped ServletResponse object.
     */

    public ServletResponse getResponse() {
        return this.response;
    }


    /**
     * Sets the response being wrapped.
     *
     * @throws java.lang.IllegalArgumentException
     *          if the response is null.
     */

    public void setResponse(ServletResponse response) {
        this.response = checkNotNullParam("response", response);
    }

    /**
     * The default behavior of this method is to call setCharacterEncoding(String charset)
     * on the wrapped response object.
     *
     * @since Servlet 2.4
     */

    public void setCharacterEncoding(String charset) {
        this.response.setCharacterEncoding(charset);
    }

    /**
     * The default behavior of this method is to return getCharacterEncoding()
     * on the wrapped response object.
     */

    public String getCharacterEncoding() {
        return this.response.getCharacterEncoding();
    }


    /**
     * The default behavior of this method is to return getOutputStream()
     * on the wrapped response object.
     */

    public ServletOutputStream getOutputStream() throws IOException {
        return this.response.getOutputStream();
    }

    /**
     * The default behavior of this method is to return getWriter()
     * on the wrapped response object.
     */


    public PrintWriter getWriter() throws IOException {
        return this.response.getWriter();
    }

    /**
     * The default behavior of this method is to call setContentLength(int len)
     * on the wrapped response object.
     */

    public void setContentLength(int len) {
        this.response.setContentLength(len);
    }

    /**
     * The default behavior of this method is to call setContentLengthLong(long len)
     * on the wrapped response object.
     */

    public void setContentLengthLong(long len) {
        this.response.setContentLengthLong(len);
    }

    /**
     * The default behavior of this method is to call setContentType(String type)
     * on the wrapped response object.
     */

    public void setContentType(String type) {
        this.response.setContentType(type);
    }

    /**
     * The default behavior of this method is to return getContentType()
     * on the wrapped response object.
     *
     * @since Servlet 2.4
     */

    public String getContentType() {
        return this.response.getContentType();
    }

    /**
     * The default behavior of this method is to call setBufferSize(int size)
     * on the wrapped response object.
     */
    public void setBufferSize(int size) {
        this.response.setBufferSize(size);
    }

    /**
     * The default behavior of this method is to return getBufferSize()
     * on the wrapped response object.
     */
    public int getBufferSize() {
        return this.response.getBufferSize();
    }

    /**
     * The default behavior of this method is to call flushBuffer()
     * on the wrapped response object.
     */

    public void flushBuffer() throws IOException {
        this.response.flushBuffer();
    }

    /**
     * The default behavior of this method is to return isCommitted()
     * on the wrapped response object.
     */
    public boolean isCommitted() {
        return this.response.isCommitted();
    }

    /**
     * The default behavior of this method is to call reset()
     * on the wrapped response object.
     */

    public void reset() {
        this.response.reset();
    }

    /**
     * The default behavior of this method is to call resetBuffer()
     * on the wrapped response object.
     */

    public void resetBuffer() {
        this.response.resetBuffer();
    }

    /**
     * The default behavior of this method is to call setLocale(Locale loc)
     * on the wrapped response object.
     */

    public void setLocale(Locale loc) {
        this.response.setLocale(loc);
    }

    /**
     * The default behavior of this method is to return getLocale()
     * on the wrapped response object.
     */
    public Locale getLocale() {
        return this.response.getLocale();
    }


    /**
     * Checks (recursively) if this ServletResponseWrapper wraps the given
     * {@link ServletResponse} instance.
     *
     * @param wrapped the ServletResponse instance to search for
     * @return true if this ServletResponseWrapper wraps the
     *         given ServletResponse instance, false otherwise
     * @since Servlet 3.0
     */
    public boolean isWrapperFor(ServletResponse wrapped) {
        if (response == wrapped) {
            return true;
        } else if (response instanceof ServletResponseWrapper) {
            return ((ServletResponseWrapper) response).isWrapperFor(wrapped);
        } else {
            return false;
        }
    }


    /**
     * Checks (recursively) if this ServletResponseWrapper wraps a
     * {@link ServletResponse} of the given class type.
     *
     * @param wrappedType the ServletResponse class type to
     *                    search for
     * @return true if this ServletResponseWrapper wraps a
     *         ServletResponse of the given class type, false otherwise
     * @throws IllegalArgumentException if the given class does not
     *                                  implement {@link ServletResponse}
     * @since Servlet 3.0
     */
    public boolean isWrapperFor(Class<?> wrappedType) {
        if (!ServletResponse.class.isAssignableFrom(wrappedType)) {
            throw new IllegalArgumentException("Given class " +
                    wrappedType.getName() + " not a subinterface of " +
                    ServletResponse.class.getName());
        }
        if (wrappedType.isAssignableFrom(response.getClass())) {
            return true;
        } else if (response instanceof ServletResponseWrapper) {
            return ((ServletResponseWrapper) response).isWrapperFor(wrappedType);
        } else {
            return false;
        }
    }


    private HttpServletResponse _getHttpServletResponse() {
        return (HttpServletResponse) response;
    }

    /**
     * The default behavior of this method is to call addCookie(Cookie cookie)
     * on the wrapped response object.
     */
    @Override
    public void addCookie(Cookie cookie) {
        this._getHttpServletResponse().addCookie(cookie);
    }

    /**
     * The default behavior of this method is to call containsHeader(String name)
     * on the wrapped response object.
     */
    @Override
    public boolean containsHeader(String name) {
        return this._getHttpServletResponse().containsHeader(name);
    }

    /**
     * The default behavior of this method is to call encodeURL(String url)
     * on the wrapped response object.
     */
    @Override
    public String encodeURL(String url) {
        return this._getHttpServletResponse().encodeURL(url);
    }

    /**
     * The default behavior of this method is to return encodeRedirectURL(String url)
     * on the wrapped response object.
     */
    @Override
    public String encodeRedirectURL(String url) {
        return this._getHttpServletResponse().encodeRedirectURL(url);
    }

    /**
     * The default behavior of this method is to call sendError(int sc, String msg)
     * on the wrapped response object.
     */
    @Override
    public void sendError(int sc, String msg) throws IOException {
        this._getHttpServletResponse().sendError(sc, msg);
    }

    /**
     * The default behavior of this method is to call sendError(int sc)
     * on the wrapped response object.
     */
    @Override
    public void sendError(int sc) throws IOException {
        this._getHttpServletResponse().sendError(sc);
    }

    /**
     * The default behavior of this method is to return sendRedirect(String location)
     * on the wrapped response object.
     */
    @Override
    public void sendRedirect(String location) throws IOException {
        this._getHttpServletResponse().sendRedirect(location);
    }

    /**
     * The default behavior of this method is to call setDateHeader(String name, long date)
     * on the wrapped response object.
     */
    @Override
    public void setDateHeader(String name, long date) {
        this._getHttpServletResponse().setDateHeader(name, date);
    }

    /**
     * The default behavior of this method is to call addDateHeader(String name, long date)
     * on the wrapped response object.
     */
    @Override
    public void addDateHeader(String name, long date) {
        this._getHttpServletResponse().addDateHeader(name, date);
    }

    /**
     * The default behavior of this method is to return setHeader(String name, String value)
     * on the wrapped response object.
     */
    @Override
    public void setHeader(String name, String value) {
        this._getHttpServletResponse().setHeader(name, value);
    }

    /**
     * The default behavior of this method is to return addHeader(String name, String value)
     * on the wrapped response object.
     */
    @Override
    public void addHeader(String name, String value) {
        this._getHttpServletResponse().addHeader(name, value);
    }

    /**
     * The default behavior of this method is to call setIntHeader(String name, int value)
     * on the wrapped response object.
     */
    @Override
    public void setIntHeader(String name, int value) {
        this._getHttpServletResponse().setIntHeader(name, value);
    }

    /**
     * The default behavior of this method is to call addIntHeader(String name, int value)
     * on the wrapped response object.
     */
    @Override
    public void addIntHeader(String name, int value) {
        this._getHttpServletResponse().addIntHeader(name, value);
    }

    /**
     * The default behavior of this method is to call setStatus(int sc)
     * on the wrapped response object.
     */
    @Override
    public void setStatus(int sc) {
        this._getHttpServletResponse().setStatus(sc);
    }

    /**
     * The default behaviour of this method is to call
     * {@link HttpServletResponse#getStatus} on the wrapped response
     * object.
     *
     * @return the current status code of the wrapped response
     */
    @Override
    public int getStatus() {
        return _getHttpServletResponse().getStatus();
    }

    /**
     * The default behaviour of this method is to call
     * {@link HttpServletResponse#getHeader} on the wrapped response
     * object.
     *
     * @param name the name of the response header whose value to return
     * @return the value of the response header with the given name,
     *         or <tt>null</tt> if no header with the given name has been set
     *         on the wrapped response
     * @since Servlet 3.0
     */
    @Override
    public String getHeader(String name) {
        return _getHttpServletResponse().getHeader(name);
    }

    /**
     * The default behaviour of this method is to call
     * {@link HttpServletResponse#getHeaders} on the wrapped response
     * object.
     * <p>
     * <p>Any changes to the returned <code>Collection</code> must not
     * affect this <code>HttpServletResponseWrapper</code>.
     *
     * @param name the name of the response header whose values to return
     * @return a (possibly empty) <code>Collection</code> of the values
     *         of the response header with the given name
     * @since Servlet 3.0
     */
    @Override
    public Collection<String> getHeaders(String name) {
        return _getHttpServletResponse().getHeaders(name);
    }

    /**
     * The default behaviour of this method is to call
     * {@link HttpServletResponse#getHeaderNames} on the wrapped response
     * object.
     * <p>
     * <p>Any changes to the returned <code>Collection</code> must not
     * affect this <code>HttpServletResponseWrapper</code>.
     *
     * @return a (possibly empty) <code>Collection</code> of the names
     *         of the response headers
     * @since Servlet 3.0
     */
    @Override
    public Collection<String> getHeaderNames() {
        return _getHttpServletResponse().getHeaderNames();
    }
}
