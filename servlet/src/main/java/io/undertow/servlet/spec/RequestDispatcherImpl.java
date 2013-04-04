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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletAttachments;
import io.undertow.servlet.handlers.ServletChain;
import io.undertow.servlet.handlers.ServletPathMatch;

/**
 * @author Stuart Douglas
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    private final String path;
    private final ServletContextImpl servletContext;
    private final ServletChain chain;
    private final ServletPathMatch pathMatch;
    private final boolean named;

    public RequestDispatcherImpl(final String path, final ServletContextImpl servletContext) {
        this.path = path;
        this.servletContext = servletContext;
        int qPos = path.indexOf("?");
        if(qPos == -1) {
            this.pathMatch = servletContext.getDeployment().getServletPaths().getServletHandlerByPath(path);
        } else {
            this.pathMatch = servletContext.getDeployment().getServletPaths().getServletHandlerByPath(path.substring(0, qPos));
        }
        this.chain = pathMatch;
        this.named = false;
    }

    public RequestDispatcherImpl(final ServletChain chain, final ServletContextImpl servletContext) {
        this.chain = chain;
        this.named = true;
        this.servletContext = servletContext;
        this.path = null;
        this.pathMatch = null;
    }

    @Override
    public void forward(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        HttpServletRequestImpl requestImpl = HttpServletRequestImpl.getRequestImpl(request);
        final HttpServletResponseImpl responseImpl = HttpServletResponseImpl.getResponseImpl(response);
        final HttpServerExchange exchange = requestImpl.getExchange();
        response.resetBuffer();


        final ServletRequest oldRequest = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);

        Map<String, Deque<String>> queryParameters = requestImpl.getQueryParameters();

        if (!named) {

            //only update if this is the first forward
            if (request.getAttribute(FORWARD_REQUEST_URI) == null) {
                request.setAttribute(FORWARD_REQUEST_URI, requestImpl.getRequestURI());
                request.setAttribute(FORWARD_CONTEXT_PATH, requestImpl.getContextPath());
                request.setAttribute(FORWARD_SERVLET_PATH, requestImpl.getServletPath());
                request.setAttribute(FORWARD_PATH_INFO, requestImpl.getPathInfo());
                request.setAttribute(FORWARD_QUERY_STRING, requestImpl.getQueryString());
            }

            String newQueryString = "";
            int qsPos = path.indexOf("?");
            String newServletPath = path;
            if (qsPos != -1) {
                newQueryString = newServletPath.substring(qsPos + 1);
                newServletPath = newServletPath.substring(0, qsPos);
            }
            String newRequestUri = servletContext.getContextPath() + newServletPath;

            Map<String, Deque<String>> newQueryParameters = createNewQueryParameters(queryParameters, newQueryString);
            requestImpl.setQueryParameters(newQueryParameters);

            requestImpl.getExchange().setRelativePath(newServletPath);
            requestImpl.getExchange().setQueryString(newQueryString);
            requestImpl.getExchange().setRequestPath(newRequestUri);
            requestImpl.getExchange().setRequestURI(newRequestUri);
            requestImpl.getExchange().putAttachment(ServletAttachments.SERVLET_PATH_MATCH, pathMatch);
            requestImpl.setServletContext(servletContext);
            responseImpl.setServletContext(servletContext);
        }

        try {
            try {
                exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                if (named) {
                    servletContext.getDeployment().getServletDispatcher().dispatchToServlet(exchange, chain, DispatcherType.FORWARD);
                } else {
                    servletContext.getDeployment().getServletDispatcher().dispatchToPath(exchange, pathMatch, DispatcherType.FORWARD);
                }

                if (response instanceof HttpServletResponseImpl) {
                    responseImpl.closeStreamAndWriter();
                } else {
                    try {
                        final PrintWriter writer = response.getWriter();
                        writer.flush();
                        writer.close();
                    } catch (IllegalStateException e) {
                        final ServletOutputStream outputStream = response.getOutputStream();
                        outputStream.flush();
                        outputStream.close();
                    }
                }
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
        }
    }

    private Map<String, Deque<String>> createNewQueryParameters(final Map<String, Deque<String>> queryParameters, final String newQueryString) {
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
        for (Map.Entry<String, Deque<String>> entry : queryParameters.entrySet()) {
            if (!newQueryParameters.containsKey(entry.getKey())) {
                newQueryParameters.put(entry.getKey(), new ArrayDeque<String>(entry.getValue()));
            } else {
                newQueryParameters.get(entry.getKey()).addAll(entry.getValue());
            }
        }
        return newQueryParameters;
    }

    @Override
    public void include(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {

        HttpServletRequestImpl requestImpl = HttpServletRequestImpl.getRequestImpl(request);
        final HttpServletResponseImpl responseImpl = HttpServletResponseImpl.getResponseImpl(response);
        final HttpServerExchange exchange = requestImpl.getExchange();

        final ServletRequest oldRequest = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);

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
            String newRequestUri = servletContext.getContextPath() + newServletPath;

            Map<String, Deque<String>> newQueryParameters = createNewQueryParameters(queryParameters, newQueryString);
            requestImpl.setQueryParameters(newQueryParameters);

            request.setAttribute(INCLUDE_REQUEST_URI, newRequestUri);
            request.setAttribute(INCLUDE_CONTEXT_PATH, servletContext.getContextPath());
            request.setAttribute(INCLUDE_SERVLET_PATH, pathMatch.getMatched());
            request.setAttribute(INCLUDE_PATH_INFO, pathMatch.getRemaining());
            request.setAttribute(INCLUDE_QUERY_STRING, newQueryString);
        }
        boolean inInclude = responseImpl.isInsideInclude();
        responseImpl.setInsideInclude(true);

        ServletContextImpl oldContext = requestImpl.getServletContext();
        try {
            requestImpl.setServletContext(servletContext);
            responseImpl.setServletContext(servletContext);
            try {
                exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                servletContext.getDeployment().getServletDispatcher().dispatchToServlet(exchange, chain, DispatcherType.INCLUDE);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            responseImpl.setInsideInclude(inInclude);
            requestImpl.setServletContext(oldContext);
            responseImpl.setServletContext(oldContext);
            exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
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

    public void error(final ServletRequest request, final ServletResponse response, final String servletName, final String message) throws ServletException, IOException {
        error(request, response, servletName, null, message);
    }

    public void error(final ServletRequest request, final ServletResponse response, final String servletName) throws ServletException, IOException {
        error(request, response, servletName, null, null);
    }

    public void error(final ServletRequest request, final ServletResponse response, final String servletName, final Throwable exception) throws ServletException, IOException {
        error(request, response, servletName, exception, exception.getMessage());
    }


    private void error(final ServletRequest request, final ServletResponse response, final String servletName, final Throwable exception, final String message) throws ServletException, IOException {
        HttpServletRequestImpl requestImpl = HttpServletRequestImpl.getRequestImpl(request);
        final HttpServletResponseImpl responseImpl = HttpServletResponseImpl.getResponseImpl(response);
        final HttpServerExchange exchange = requestImpl.getExchange();
        response.resetBuffer();


        final ServletRequest oldRequest = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        exchange.putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.ERROR);


        //only update if this is the first forward
        request.setAttribute(ERROR_REQUEST_URI, requestImpl.getRequestURI());
        request.setAttribute(ERROR_SERVLET_NAME, servletName);
        if (exception != null) {
            request.setAttribute(ERROR_EXCEPTION, exception);
            request.setAttribute(ERROR_EXCEPTION_TYPE, exception.getClass());
        }
        request.setAttribute(ERROR_MESSAGE, message);
        request.setAttribute(ERROR_STATUS_CODE, exchange.getResponseCode());

        String newQueryString = "";
        int qsPos = path.indexOf("?");
        String newServletPath = path;
        if (qsPos != -1) {
            newQueryString = newServletPath.substring(qsPos + 1);
            newServletPath = newServletPath.substring(0, qsPos);
        }
        String newRequestUri = servletContext.getContextPath() + newServletPath;

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

        requestImpl.getExchange().setRelativePath(newServletPath);
        requestImpl.getExchange().setQueryString(newQueryString);
        requestImpl.getExchange().setRequestPath(newRequestUri);
        requestImpl.getExchange().setRequestURI(newRequestUri);
        requestImpl.getExchange().putAttachment(ServletAttachments.SERVLET_PATH_MATCH, pathMatch);
        requestImpl.setServletContext(servletContext);
        responseImpl.setServletContext(servletContext);


        try {
            try {
                exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                servletContext.getDeployment().getServletDispatcher().dispatchToPath(exchange, pathMatch, DispatcherType.ERROR);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
        }
    }
}
