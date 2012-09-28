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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.util.DelegatingHttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    private final String path;
    private final String servletContext;
    private final ServletInitialHandler handler;
    private final boolean named;

    public RequestDispatcherImpl(final String path, final String servletContext, final ServletInitialHandler handler) {
        this.path = path;
        this.servletContext = servletContext;
        this.handler = handler;
        this.named = false;
    }


    public RequestDispatcherImpl(final ServletInitialHandler handler) {
        this.handler = handler;
        this.named = true;
        this.servletContext = null;
        this.path = null;
    }

    @Override
    public void forward(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        final BlockingHttpServerExchange exchange = getExchange(request);
        response.resetBuffer();

        HttpServletRequestImpl requestImpl = getRequestImpl(request);

        final ServletRequest oldRequest = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        exchange.getExchange().putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.INCLUDE);

        Map<String, Deque<String>> queryParameters = requestImpl.getQueryParameters();

        if (!named) {

            //only update if this is the first forward
            if (request.getAttribute(FORWARD_REQUEST_URI) == null) {
                request.setAttribute(FORWARD_REQUEST_URI, requestImpl.getRequestURI());
                request.setAttribute(FORWARD_CONTEXT_PATH, requestImpl.getContextPath());
                request.setAttribute(FORWARD_SERVLET_PATH, requestImpl.getServletPath());
                //request.setAttribute(FORWARD_PATH_INFO, path);
                request.setAttribute(FORWARD_QUERY_STRING, requestImpl.getQueryString());
            }

            String newQueryString = "";
            int qsPos = path.indexOf("?");
            String newServletPath = path;
            if (qsPos != -1) {
                newQueryString = newServletPath.substring(qsPos + 1);
                newServletPath = newServletPath.substring(0, qsPos);
            }
            String newRequestUri = servletContext + newServletPath;

            //todo: a more efficent impl
            Map<String, Deque<String>> newQueryParameters = new HashMap<String, Deque<String>>();
            for (String part : newQueryString.split("&")) {
                String name = part;
                String value = "";
                int equals = part.indexOf('=');
                if (equals != -1) {
                    name = part.substring(0, equals);
                    value = part.substring(equals + 1);
                }
                Deque<String> queue = newQueryParameters.get(name);
                if (queue == null) {
                    newQueryParameters.put(name, queue = new ArrayDeque<String>(1));
                }
                queue.add(value);
            }
            requestImpl.setQueryParameters(newQueryParameters);

            requestImpl.getExchange().getExchange().setRelativePath(newServletPath);
            requestImpl.getExchange().getExchange().setQueryString(newQueryString);
            requestImpl.getExchange().getExchange().setRequestPath(newRequestUri);
            requestImpl.getExchange().getExchange().setRequestURI(newRequestUri);
        }


        try {
            try {
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                handler.handleRequest(exchange);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
        }
    }

    private HttpServletRequestImpl getRequestImpl(final ServletRequest request) {
        final HttpServletRequestImpl requestImpl;
        if (request instanceof HttpServletRequestImpl) {
            requestImpl = (HttpServletRequestImpl) request;
        } else if (request instanceof HttpServletRequestWrapper) {
            requestImpl = (HttpServletRequestImpl) ((HttpServletRequestWrapper) request).getRequest();
        } else {
            throw UndertowServletMessages.MESSAGES.requestWasNotOriginalOrWrapper(request);
        }
        return requestImpl;
    }

    @Override
    public void include(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        final BlockingHttpServerExchange exchange = getExchange(request);

        HttpServletRequestImpl requestImpl = getRequestImpl(request);

        final ServletRequest oldRequest = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        exchange.getExchange().putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.INCLUDE);

        Object requestUri = null;
        Object contextPath = null;
        Object servletPath = null;
        Object pathInfo = null;
        Object queryString = null;
        Map<String, Deque<String>> queryParameters = requestImpl.getQueryParameters();

        if (!named) {
            requestUri = request.getAttribute(INCLUDE_REQUEST_URI);
            contextPath = request.getAttribute(INCLUDE_CONTEXT_PATH);
            servletPath = request.getAttribute(INCLUDE_SERVLET_PATH);
            pathInfo = request.getAttribute(INCLUDE_PATH_INFO);
            queryString = request.getAttribute(INCLUDE_QUERY_STRING);

            String newQueryString = "";
            int qsPos = path.indexOf("?");
            String newServletPath = path;
            if (qsPos != -1) {
                newQueryString = newServletPath.substring(qsPos + 1);
                newServletPath = newServletPath.substring(0, qsPos);
            }
            String newRequestUri = servletContext + newServletPath;

            //todo: a more efficent impl
            Map<String, Deque<String>> newQueryParameters = new HashMap<String, Deque<String>>();
            for (String part : newQueryString.split("&")) {
                String name = part;
                String value = "";
                int equals = part.indexOf('=');
                if (equals != -1) {
                    name = part.substring(0, equals);
                    value = part.substring(equals + 1);
                }
                Deque<String> queue = newQueryParameters.get(name);
                if (queue == null) {
                    newQueryParameters.put(name, queue = new ArrayDeque<String>(1));
                }
                queue.add(value);
            }
            requestImpl.setQueryParameters(newQueryParameters);

            request.setAttribute(INCLUDE_REQUEST_URI, newRequestUri);
            request.setAttribute(INCLUDE_CONTEXT_PATH, servletContext);
            request.setAttribute(INCLUDE_SERVLET_PATH, newServletPath);
            //request.setAttribute(INCLUDE_PATH_INFO, path);
            request.setAttribute(INCLUDE_QUERY_STRING, newQueryString);
        }

        try {
            try {
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                handler.handleRequest(exchange);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
            if (!named) {
                request.setAttribute(INCLUDE_REQUEST_URI, requestUri);
                request.setAttribute(INCLUDE_CONTEXT_PATH, contextPath);
                request.setAttribute(INCLUDE_SERVLET_PATH, servletPath);
                request.setAttribute(INCLUDE_PATH_INFO, pathInfo);
                request.setAttribute(INCLUDE_QUERY_STRING, queryString);
                requestImpl.setQueryParameters(queryParameters);
            }
        }
    }

    private BlockingHttpServerExchange getExchange(final ServletRequest request) {
        if (request instanceof HttpServletRequestImpl) {
            return ((HttpServletRequestImpl) request).getExchange();
        } else if (request instanceof ServletRequestWrapper) {
            return getExchange(((ServletRequestWrapper) request).getRequest());
        } else {
            throw UndertowServletMessages.MESSAGES.requestNoOfCorrectType();
        }
    }

}
