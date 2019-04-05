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

package io.undertow.servlet.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.AccessController;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpSession;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.undertow.UndertowLogger;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.HttpString;
import io.undertow.util.UndertowOptions;

/**
 * Saved servlet request.
 *
 * @author Stuart Douglas
 */
public class SavedRequest implements Serializable {

    private static final String SESSION_KEY = SavedRequest.class.getName();

    private final byte[] data;
    private final int dataLength;
    private final String method;
    private final String requestPath;
    private final HttpHeaders headerMap;

    public SavedRequest(byte[] data, int dataLength, String method, String requestPath, HttpHeaders headerMap) {
        this.data = data;
        this.dataLength = dataLength;
        this.method = method;
        this.requestPath = requestPath;
        this.headerMap = headerMap;
    }

    /**
     * With added possibility to save data from buffer instead f from request body, there has to be method which returns max allowed buffer size to save.
     *
     * @param exchange
     * @return
     */
    public static int getMaxBufferSizeToSave(final HttpServerExchange exchange) {
        int maxSize = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, UndertowOptions.DEFAULT_MAX_BUFFERED_REQUEST_SIZE);
        return  maxSize;
    }

    public static void trySaveRequest(final HttpServerExchange exchange) {
        int maxSize = getMaxBufferSizeToSave(exchange);
        if (maxSize > 0) {
            //if this request has a body try and cache the response
            if (!exchange.isRequestComplete()) {
                final long requestContentLength = exchange.getRequestContentLength();
                if (requestContentLength > maxSize) {
                    UndertowLogger.REQUEST_LOGGER.debugf("Request to %s was to large to save", exchange.getRequestURI());
                    return;//failed to save the request, we just return
                }
                //TODO: we should really be used pooled buffers
                //TODO: we should probably limit the number of saved requests at any given time
                byte[] buffer = new byte[maxSize];
                int read = 0;
                int res = 0;
                InputStream in = exchange.getInputStream();
                try {
                    while ((res = in.read(buffer, read, buffer.length - read)) > 0) {
                        read += res;
                        if (read == maxSize) {
                            UndertowLogger.REQUEST_LOGGER.debugf("Request to %s was to large to save", exchange.getRequestURI());
                            return;//failed to save the request, we just return
                        }
                    }
                    //save request from buffer
                    trySaveRequest(exchange, buffer, read);
                } catch (IOException e) {
                    UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                }
            }
        }
    }

    public static void trySaveRequest(final HttpServerExchange exchange, final byte[] buffer, int length) {
        int maxSize = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, UndertowOptions.DEFAULT_MAX_BUFFERED_REQUEST_SIZE);
        if (maxSize > 0) {
            if (length > maxSize) {
                UndertowLogger.REQUEST_LOGGER.debugf("Request to %s was to large to save", exchange.getRequestURI());
                return;//failed to save the request, we just return
            }
            //TODO: we should really be used pooled buffers
            //TODO: we should probably limit the number of saved requests at any given time
            HttpHeaders headers = new DefaultHttpHeaders();
            for (Map.Entry<String, String> entry : exchange.requestHeaders()) {
                if (entry.getKey().equals(HttpHeaderNames.CONTENT_LENGTH) ||
                        entry.getKey().equals(HttpHeaderNames.TRANSFER_ENCODING) ||
                        entry.getKey().equals(HttpHeaderNames.CONNECTION)) {
                    continue;
                }
                headers.set(entry.getKey(), exchange.requestHeaders().getAll(entry.getKey()));
            }
            SavedRequest request = new SavedRequest(buffer, length, exchange.requestMethod(), exchange.getRelativePath(), exchange.requestHeaders());
            final ServletRequestContext sc = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            HttpSessionImpl session = sc.getCurrentServletContext().getSession(exchange, true);
            Session underlyingSession;
            if (System.getSecurityManager() == null) {
                underlyingSession = session.getSession();
            } else {
                underlyingSession = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(session));
            }
            underlyingSession.setAttribute(SESSION_KEY, request);
        }
    }

    public static void tryRestoreRequest(final HttpServerExchange exchange, HttpSession session) {
        if(session instanceof HttpSessionImpl) {

            Session underlyingSession;
            if(System.getSecurityManager() == null) {
                underlyingSession = ((HttpSessionImpl) session).getSession();
            } else {
                underlyingSession = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(session));
            }
            SavedRequest request = (SavedRequest) underlyingSession.getAttribute(SESSION_KEY);
            if(request != null) {
                if(request.requestPath.equals(exchange.getRelativePath()) && exchange.isRequestComplete()) {
                    UndertowLogger.REQUEST_LOGGER.debugf("restoring request body for request to %s", request.requestPath);
                    exchange.requestMethod(request.method);
                    Connectors.ungetRequestBytes(exchange, Unpooled.wrappedBuffer(request.data, 0, request.dataLength));
                    underlyingSession.removeAttribute(SESSION_KEY);
                    //clear the existing header map of everything except the connection header
                    //TODO: are there other headers we should preserve?
                    Iterator<Map.Entry<String, String>> headerIterator = exchange.requestHeaders().iterator();
                    while (headerIterator.hasNext()) {
                        Map.Entry<String, String> header = headerIterator.next();
                        if(!header.getKey().equals(HttpHeaderNames.CONNECTION)) {
                            headerIterator.remove();
                        }
                    }
                    for(Map.Entry<String, String> header : request.headerMap) {
                        exchange.requestHeaders().add(header.getKey(), request.headerMap.getAll(header.getKey()));
                    }
                }
            }
        }
    }

}
