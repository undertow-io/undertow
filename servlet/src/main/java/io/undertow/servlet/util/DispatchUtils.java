/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletPathMatch;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.util.BadRequestException;
import io.undertow.util.ParameterLimitException;
import jakarta.servlet.ServletException;
import java.util.Deque;
import java.util.Map;

import static jakarta.servlet.AsyncContext.ASYNC_CONTEXT_PATH;
import static jakarta.servlet.AsyncContext.ASYNC_MAPPING;
import static jakarta.servlet.AsyncContext.ASYNC_PATH_INFO;
import static jakarta.servlet.AsyncContext.ASYNC_QUERY_STRING;
import static jakarta.servlet.AsyncContext.ASYNC_REQUEST_URI;
import static jakarta.servlet.AsyncContext.ASYNC_SERVLET_PATH;
import static jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION;
import static jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION_TYPE;
import static jakarta.servlet.RequestDispatcher.ERROR_MESSAGE;
import static jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI;
import static jakarta.servlet.RequestDispatcher.ERROR_SERVLET_NAME;
import static jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE;
import static jakarta.servlet.RequestDispatcher.FORWARD_CONTEXT_PATH;
import static jakarta.servlet.RequestDispatcher.FORWARD_MAPPING;
import static jakarta.servlet.RequestDispatcher.FORWARD_PATH_INFO;
import static jakarta.servlet.RequestDispatcher.FORWARD_QUERY_STRING;
import static jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI;
import static jakarta.servlet.RequestDispatcher.FORWARD_SERVLET_PATH;
import static jakarta.servlet.RequestDispatcher.INCLUDE_CONTEXT_PATH;
import static jakarta.servlet.RequestDispatcher.INCLUDE_MAPPING;
import static jakarta.servlet.RequestDispatcher.INCLUDE_PATH_INFO;
import static jakarta.servlet.RequestDispatcher.INCLUDE_QUERY_STRING;
import static jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI;
import static jakarta.servlet.RequestDispatcher.INCLUDE_SERVLET_PATH;

/**
 * <p>Utility class to manage the dispatching parsing of the path. The methods
 * fill the exchange, request and response with the needed data for the
 * dispatch.</p>
 *
 * @author rmartinc
 */
public final class DispatchUtils {

    private DispatchUtils() {
        // Utility static class, no constructor
    }

    /**
     * Perform a forward dispatch to a path assigning everything needed to the
     * request, response and exchange.
     *
     * @param path The path to forward scoped to the ServletContext
     * @param requestImpl The request
     * @param responseImpl The response
     * @param servletContext The servlet context
     * @return The match for the path
     * @throws ParameterLimitException parameter limit exceeded
     * @throws BadRequestException
     */
    public static ServletPathMatch dispatchForward(final String path,
            final HttpServletRequestImpl requestImpl,
            final HttpServletResponseImpl responseImpl,
            final ServletContextImpl servletContext) throws ParameterLimitException, BadRequestException {
        //only update if this is the first forward
        if (requestImpl.getAttribute(FORWARD_REQUEST_URI) == null) {
            requestImpl.setAttribute(FORWARD_REQUEST_URI, requestImpl.getRequestURI());
            requestImpl.setAttribute(FORWARD_CONTEXT_PATH, requestImpl.getContextPath());
            requestImpl.setAttribute(FORWARD_SERVLET_PATH, requestImpl.getServletPath());
            requestImpl.setAttribute(FORWARD_PATH_INFO, requestImpl.getPathInfo());
            requestImpl.setAttribute(FORWARD_QUERY_STRING, requestImpl.getQueryString());
            requestImpl.setAttribute(FORWARD_MAPPING, requestImpl.getHttpServletMapping());
        }

        final String newRequestPath = assignRequestPath(path, requestImpl, servletContext, false);
        final ServletPathMatch pathMatch = servletContext.getDeployment().getServletPaths().getServletHandlerByPath(newRequestPath);

        requestImpl.getExchange().getAttachment(ServletRequestContext.ATTACHMENT_KEY).setServletPathMatch(pathMatch);
        requestImpl.setServletContext(servletContext);
        responseImpl.setServletContext(servletContext);
        return pathMatch;
    }

    /**
     * Perform an include dispatch to a path assigning everything needed to the
     * request, response and exchange.
     *
     * @param path The path to include scoped to the ServletContext
     * @param requestImpl The request
     * @param responseImpl The response
     * @param servletContext The servlet context
     * @return The match for the path
     * @throws ParameterLimitException parameter limit exceeded
     * @throws BadRequestException
     */
    public static ServletPathMatch dispatchInclude(final String path,
            final HttpServletRequestImpl requestImpl,
            final HttpServletResponseImpl responseImpl,
            final ServletContextImpl servletContext) throws ParameterLimitException, BadRequestException {
        final String newRequestPath = assignRequestPath(path, requestImpl, servletContext, true);
        final ServletPathMatch pathMatch = servletContext.getDeployment().getServletPaths().getServletHandlerByPath(newRequestPath);

        // add the rest of the include attributes for include from the match
        requestImpl.setAttribute(INCLUDE_CONTEXT_PATH, servletContext.getContextPath());
        requestImpl.setAttribute(INCLUDE_SERVLET_PATH, pathMatch.getMatched());
        requestImpl.setAttribute(INCLUDE_PATH_INFO, pathMatch.getRemaining());
        requestImpl.setAttribute(INCLUDE_MAPPING, requestImpl.getHttpServletMapping());
        return pathMatch;
    }

    /**
     * Perform a error dispatch to a path assigning everything needed to the
     * request, response and exchange.
     *
     * @param path The path to forward scoped to the ServletContext
     * @param servletName The servlet name
     * @param exception The exception for the error
     * @param message The error message
     * @param requestImpl The request
     * @param responseImpl The response
     * @param servletContext The servlet context
     * @return The match for the path
     * @throws ParameterLimitException parameter limit exceeded
     * @throws BadRequestException
     */
    public static ServletPathMatch dispatchError(final String path, final String servletName,
            final Throwable exception, final String message,
            final HttpServletRequestImpl requestImpl,
            final HttpServletResponseImpl responseImpl,
            final ServletContextImpl servletContext) throws ParameterLimitException, BadRequestException {
        //only update if this is the first forward
        if (requestImpl.getAttribute(FORWARD_REQUEST_URI) == null) {
            requestImpl.setAttribute(FORWARD_REQUEST_URI, requestImpl.getRequestURI());
            requestImpl.setAttribute(FORWARD_CONTEXT_PATH, requestImpl.getContextPath());
            requestImpl.setAttribute(FORWARD_SERVLET_PATH, requestImpl.getServletPath());
            requestImpl.setAttribute(FORWARD_PATH_INFO, requestImpl.getPathInfo());
            requestImpl.setAttribute(FORWARD_QUERY_STRING, requestImpl.getQueryString());
            requestImpl.setAttribute(FORWARD_MAPPING, requestImpl.getHttpServletMapping());
        }
        // specific attributes for error
        requestImpl.setAttribute(ERROR_REQUEST_URI, requestImpl.getRequestURI());
        requestImpl.setAttribute(ERROR_SERVLET_NAME, servletName);
        if (exception != null) {
            if (exception instanceof ServletException && ((ServletException)exception).getRootCause() != null) {
                requestImpl.setAttribute(ERROR_EXCEPTION, ((ServletException) exception).getRootCause());
                requestImpl.setAttribute(ERROR_EXCEPTION_TYPE, ((ServletException) exception).getRootCause().getClass());
            } else {
                requestImpl.setAttribute(ERROR_EXCEPTION, exception);
                requestImpl.setAttribute(ERROR_EXCEPTION_TYPE, exception.getClass());
            }
        }
        requestImpl.setAttribute(ERROR_MESSAGE, message);
        requestImpl.setAttribute(ERROR_STATUS_CODE, responseImpl.getStatus());

        final String newRequestPath = assignRequestPath(path, requestImpl, servletContext, false);
        final ServletPathMatch pathMatch = servletContext.getDeployment().getServletPaths().getServletHandlerByPath(newRequestPath);

        requestImpl.getExchange().getAttachment(ServletRequestContext.ATTACHMENT_KEY).setServletPathMatch(pathMatch);
        requestImpl.setServletContext(servletContext);
        responseImpl.setServletContext(servletContext);
        return pathMatch;
    }

    /**
     * Perform an async dispatch to a path assigning everything needed to the
     * request, response and exchange.
     *
     * @param path The path to include scoped to the ServletContext
     * @param requestImpl The request
     * @param responseImpl The response
     * @param servletContext The servlet context
     * @return The match for the path
     * @throws ParameterLimitException parameter limit exceeded
     * @throws BadRequestException
     */
    public static ServletPathMatch dispatchAsync(final String path,
            final HttpServletRequestImpl requestImpl,
            final HttpServletResponseImpl responseImpl,
            final ServletContextImpl servletContext) throws ParameterLimitException, BadRequestException {
        requestImpl.setAttribute(ASYNC_REQUEST_URI, requestImpl.getOriginalRequestURI());
        requestImpl.setAttribute(ASYNC_CONTEXT_PATH, requestImpl.getOriginalContextPath());
        requestImpl.setAttribute(ASYNC_SERVLET_PATH, requestImpl.getOriginalServletPath());
        requestImpl.setAttribute(ASYNC_PATH_INFO, requestImpl.getOriginalPathInfo());
        requestImpl.setAttribute(ASYNC_QUERY_STRING, requestImpl.getOriginalQueryString());
        requestImpl.setAttribute(ASYNC_MAPPING, requestImpl.getHttpServletMapping());

        final String newRequestPath = assignRequestPath(path, requestImpl, servletContext, false);
        final ServletPathMatch pathMatch = servletContext.getDeployment().getServletPaths().getServletHandlerByPath(newRequestPath);

        requestImpl.getExchange().getAttachment(ServletRequestContext.ATTACHMENT_KEY).setServletPathMatch(pathMatch);
        requestImpl.setServletContext(servletContext);
        responseImpl.setServletContext(servletContext);
        return pathMatch;
    }

    private static Map<String, Deque<String>> mergeQueryParameters(final Map<String, Deque<String>> newParams, final Map<String, Deque<String>> oldParams) {
        for (Map.Entry<String, Deque<String>> entry : oldParams.entrySet()) {
            Deque<String> values = newParams.get(entry.getKey());
            if (values == null) {
                // add all the values as new params do not contain this key
                newParams.put(entry.getKey(), entry.getValue());
            } else {
                // merge values new params first
                for (String v : entry.getValue()) {
                    if (!values.contains(v)) {
                        values.add(v);
                    }
                }
            }
        }
        return newParams;
    }

    private static String assignRequestPath(final String path, final HttpServletRequestImpl requestImpl,
            final ServletContextImpl servletContext, final boolean include) throws ParameterLimitException, BadRequestException {
        final StringBuilder sb = new StringBuilder();
        final HttpServerExchange exchange = requestImpl.getExchange();
        // create a fake exchange to parse the path
        final HttpServerExchange fake = new HttpServerExchange(exchange.getConnection(),
                exchange.getRequestHeaders(), exchange.getResponseHeaders(), exchange.getMaxEntitySize());
        Connectors.setExchangeRequestPath(fake, servletContext.getContextPath() + path, sb);
        // get the relative path respect to the servlet context as the request path to return
        final String newRequestPath = fake.getRequestPath().substring(servletContext.getContextPath().length());
        if (include) {
            // include does not modify exchange paths, just add the query string and request uri
            // the rest of attributes are added via the match later
            requestImpl.setAttribute(INCLUDE_QUERY_STRING, fake.getQueryString());
            requestImpl.setAttribute(INCLUDE_REQUEST_URI, fake.getRequestURI());
        } else {
            exchange.setRelativePath(newRequestPath);
            exchange.setRequestPath(fake.getRequestPath());
            exchange.setRequestURI(fake.getRequestURI());
            if (!fake.getQueryString().isEmpty()) {
                exchange.setQueryString(fake.getQueryString());
            }
        }
        // both forward and include merge parameters by spec
        if (!fake.getQueryString().isEmpty()) {
            requestImpl.setQueryParameters(mergeQueryParameters(fake.getQueryParameters(), requestImpl.getQueryParameters()));
        }
        return newRequestPath;
    }
}
