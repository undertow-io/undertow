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
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Deque;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.handlers.ServletChain;
import io.undertow.servlet.handlers.ServletPathMatch;
import io.undertow.util.QueryParameterUtils;

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
        String basePath = path;
        int qPos = basePath.indexOf("?");
        if (qPos != -1) {
            basePath = basePath.substring(0, qPos);
        }
        int mPos = basePath.indexOf(";");
        if(mPos != -1) {
            basePath = basePath.substring(0, mPos);
        }
        this.pathMatch = servletContext.getDeployment().getServletPaths().getServletHandlerByPath(basePath);
        this.chain = pathMatch.getServletChain();
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
        if(System.getSecurityManager() != null) {
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                    @Override
                    public Object run() throws Exception {
                        forwardImplSetup(request, response);
                        return null;
                    }
                });
            } catch (PrivilegedActionException e) {
                if(e.getCause() instanceof ServletException) {
                    throw (ServletException)e.getCause();
                } else if(e.getCause() instanceof IOException) {
                    throw (IOException)e.getCause();
                } else if(e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException)e.getCause();
                } else {
                    throw new RuntimeException(e.getCause());
                }
            }
        } else {
            forwardImplSetup(request, response);
        }
    }

    private void forwardImplSetup(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        final ServletRequestContext servletRequestContext = SecurityActions.currentServletRequestContext();
        if(servletRequestContext == null) {
            UndertowLogger.REQUEST_LOGGER.debugf("No servlet request context for %s, dispatching mock request", request);
            mock(request, response);
            return;
        }

        ThreadSetupAction.Handle handle = null;
        ServletContextImpl oldServletContext = null;
        HttpSessionImpl oldSession = null;
        if (servletRequestContext.getCurrentServletContext() != this.servletContext) {

            try {
                //cross context request, we need to run the thread setup actions
                oldServletContext = servletRequestContext.getCurrentServletContext();
                oldSession = servletRequestContext.getSession();
                servletRequestContext.setSession(null);
                servletRequestContext.setCurrentServletContext(this.servletContext);
                this.servletContext.invokeAction(servletRequestContext.getExchange(), new ThreadSetupHandler.Action<Void, Object>() {
                    @Override
                    public Void call(HttpServerExchange exchange, Object context) throws Exception {
                        forwardImpl(request, response, servletRequestContext);
                        return null;
                    }
                });

            } finally {
                    servletRequestContext.setSession(oldSession);
                    servletRequestContext.setCurrentServletContext(oldServletContext);
            }
        } else {
            forwardImpl(request, response, servletRequestContext);
        }

    }

    private void forwardImpl(ServletRequest request, ServletResponse response, ServletRequestContext servletRequestContext) throws ServletException, IOException {
        final HttpServletRequestImpl requestImpl = servletRequestContext.getOriginalRequest();
        final HttpServletResponseImpl responseImpl = servletRequestContext.getOriginalResponse();
        if (!servletContext.getDeployment().getDeploymentInfo().isAllowNonStandardWrappers()) {
            if (servletRequestContext.getOriginalRequest() != request) {
                if (!(request instanceof ServletRequestWrapper)) {
                    throw UndertowServletMessages.MESSAGES.requestWasNotOriginalOrWrapper(request);
                }
            }
            if (servletRequestContext.getOriginalResponse() != response) {
                if (!(response instanceof ServletResponseWrapper)) {
                    throw UndertowServletMessages.MESSAGES.responseWasNotOriginalOrWrapper(response);
                }
            }
        }
        response.resetBuffer();

        final ServletRequest oldRequest = servletRequestContext.getServletRequest();
        final ServletResponse oldResponse = servletRequestContext.getServletResponse();

        Map<String, Deque<String>> queryParameters = requestImpl.getQueryParameters();

        request.removeAttribute(INCLUDE_REQUEST_URI);
        request.removeAttribute(INCLUDE_CONTEXT_PATH);
        request.removeAttribute(INCLUDE_SERVLET_PATH);
        request.removeAttribute(INCLUDE_PATH_INFO);
        request.removeAttribute(INCLUDE_QUERY_STRING);

        if (!named) {

            //only update if this is the first forward
            if (request.getAttribute(FORWARD_REQUEST_URI) == null) {
                requestImpl.setAttribute(FORWARD_REQUEST_URI, requestImpl.getRequestURI());
                requestImpl.setAttribute(FORWARD_CONTEXT_PATH, requestImpl.getContextPath());
                requestImpl.setAttribute(FORWARD_SERVLET_PATH, requestImpl.getServletPath());
                requestImpl.setAttribute(FORWARD_PATH_INFO, requestImpl.getPathInfo());
                requestImpl.setAttribute(FORWARD_QUERY_STRING, requestImpl.getQueryString());
            }

            int qsPos = path.indexOf("?");
            String newServletPath = path;
            if (qsPos != -1) {
                String newQueryString = newServletPath.substring(qsPos + 1);
                newServletPath = newServletPath.substring(0, qsPos);

                String encoding = QueryParameterUtils.getQueryParamEncoding(servletRequestContext.getExchange());
                Map<String, Deque<String>> newQueryParameters = QueryParameterUtils.mergeQueryParametersWithNewQueryString(queryParameters, newQueryString, encoding);
                requestImpl.getExchange().setQueryString(newQueryString);
                requestImpl.setQueryParameters(newQueryParameters);
            }
            String newRequestUri = servletContext.getContextPath() + newServletPath;



            requestImpl.getExchange().setRelativePath(newServletPath);
            requestImpl.getExchange().setRequestPath(newRequestUri);
            requestImpl.getExchange().setRequestURI(newRequestUri);
            requestImpl.getExchange().getAttachment(ServletRequestContext.ATTACHMENT_KEY).setServletPathMatch(pathMatch);
            requestImpl.setServletContext(servletContext);
            responseImpl.setServletContext(servletContext);
        }

        try {
            try {
                servletRequestContext.setServletRequest(request);
                servletRequestContext.setServletResponse(response);
                if (named) {
                    servletContext.getDeployment().getServletDispatcher().dispatchToServlet(requestImpl.getExchange(), chain, DispatcherType.FORWARD);
                } else {
                    servletContext.getDeployment().getServletDispatcher().dispatchToPath(requestImpl.getExchange(), pathMatch, DispatcherType.FORWARD);
                }

                //if we are not in an async or error dispatch then we close the response
                if (!request.isAsyncStarted()) {
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
                }
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            servletRequestContext.setServletRequest(oldRequest);
            servletRequestContext.setServletResponse(oldResponse);
        }
    }


    @Override
    public void include(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        if(System.getSecurityManager() != null) {
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                    @Override
                    public Object run() throws Exception {
                        setupIncludeImpl(request, response);
                        return null;
                    }
                });
            } catch (PrivilegedActionException e) {
                if(e.getCause() instanceof ServletException) {
                    throw (ServletException)e.getCause();
                } else if(e.getCause() instanceof IOException) {
                    throw (IOException)e.getCause();
                } else if(e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException)e.getCause();
                } else {
                    throw new RuntimeException(e.getCause());
                }
            }
        } else {
            setupIncludeImpl(request, response);
        }
    }

    private void setupIncludeImpl(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        final ServletRequestContext servletRequestContext = SecurityActions.currentServletRequestContext();
        if(servletRequestContext == null) {
            UndertowLogger.REQUEST_LOGGER.debugf("No servlet request context for %s, dispatching mock request", request);
            mock(request, response);
            return;
        }
        final HttpServletRequestImpl requestImpl = servletRequestContext.getOriginalRequest();
        final HttpServletResponseImpl responseImpl = servletRequestContext.getOriginalResponse();
        ServletContextImpl oldServletContext = null;
        HttpSessionImpl oldSession = null;
        if (servletRequestContext.getCurrentServletContext() != this.servletContext) {
            //cross context request, we need to run the thread setup actions
            oldServletContext = servletRequestContext.getCurrentServletContext();
            oldSession = servletRequestContext.getSession();
            servletRequestContext.setSession(null);
            servletRequestContext.setCurrentServletContext(this.servletContext);
            try {
                servletRequestContext.getCurrentServletContext().invokeAction(servletRequestContext.getExchange(), new ThreadSetupHandler.Action<Void, Object>() {
                    @Override
                    public Void call(HttpServerExchange exchange, Object context) throws Exception {
                        includeImpl(request, response, servletRequestContext, requestImpl, responseImpl);
                        return null;
                    }
                });
            } finally {
                servletRequestContext.setSession(oldSession);
                servletRequestContext.setCurrentServletContext(oldServletContext);
            }
        } else {
            includeImpl(request, response, servletRequestContext, requestImpl, responseImpl);
        }
    }

    private void includeImpl(ServletRequest request, ServletResponse response, ServletRequestContext servletRequestContext, HttpServletRequestImpl requestImpl, HttpServletResponseImpl responseImpl) throws ServletException, IOException {
        if (!servletContext.getDeployment().getDeploymentInfo().isAllowNonStandardWrappers()) {
            if (servletRequestContext.getOriginalRequest() != request) {
                if (!(request instanceof ServletRequestWrapper)) {
                    throw UndertowServletMessages.MESSAGES.requestWasNotOriginalOrWrapper(request);
                }
            }
            if (servletRequestContext.getOriginalResponse() != response) {
                if (!(response instanceof ServletResponseWrapper)) {
                    throw UndertowServletMessages.MESSAGES.responseWasNotOriginalOrWrapper(response);
                }
            }
        }

        final ServletRequest oldRequest = servletRequestContext.getServletRequest();
        final ServletResponse oldResponse = servletRequestContext.getServletResponse();

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

            int qsPos = path.indexOf("?");
            String newServletPath = path;
            if (qsPos != -1) {
                String newQueryString = newServletPath.substring(qsPos + 1);
                newServletPath = newServletPath.substring(0, qsPos);

                String encoding = QueryParameterUtils.getQueryParamEncoding(servletRequestContext.getExchange());
                Map<String, Deque<String>> newQueryParameters = QueryParameterUtils.mergeQueryParametersWithNewQueryString(queryParameters, newQueryString, encoding);
                requestImpl.setQueryParameters(newQueryParameters);
                requestImpl.setAttribute(INCLUDE_QUERY_STRING, newQueryString);
            } else {
                requestImpl.setAttribute(INCLUDE_QUERY_STRING, "");
            }
            String newRequestUri = servletContext.getContextPath() + newServletPath;

            requestImpl.setAttribute(INCLUDE_REQUEST_URI, newRequestUri);
            requestImpl.setAttribute(INCLUDE_CONTEXT_PATH, servletContext.getContextPath());
            requestImpl.setAttribute(INCLUDE_SERVLET_PATH, pathMatch.getMatched());
            requestImpl.setAttribute(INCLUDE_PATH_INFO, pathMatch.getRemaining());
        }
        boolean inInclude = responseImpl.isInsideInclude();
        responseImpl.setInsideInclude(true);
        DispatcherType oldDispatcherType = servletRequestContext.getDispatcherType();

        ServletContextImpl oldContext = requestImpl.getServletContext();
        try {
            requestImpl.setServletContext(servletContext);
            responseImpl.setServletContext(servletContext);
            try {
                servletRequestContext.setServletRequest(request);
                servletRequestContext.setServletResponse(response);
                servletContext.getDeployment().getServletDispatcher().dispatchToServlet(requestImpl.getExchange(), chain, DispatcherType.INCLUDE);
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

            servletRequestContext.setServletRequest(oldRequest);
            servletRequestContext.setServletResponse(oldResponse);
            servletRequestContext.setDispatcherType(oldDispatcherType);
            if (!named) {
                requestImpl.setAttribute(INCLUDE_REQUEST_URI, requestUri);
                requestImpl.setAttribute(INCLUDE_CONTEXT_PATH, contextPath);
                requestImpl.setAttribute(INCLUDE_SERVLET_PATH, servletPath);
                requestImpl.setAttribute(INCLUDE_PATH_INFO, pathInfo);
                requestImpl.setAttribute(INCLUDE_QUERY_STRING, queryString);
                requestImpl.setQueryParameters(queryParameters);
            }
        }
    }

    public void error(ServletRequestContext servletRequestContext, final ServletRequest request, final ServletResponse response, final String servletName, final String message) throws ServletException, IOException {
        error(servletRequestContext, request, response, servletName, null, message);
    }

    public void error(ServletRequestContext servletRequestContext, final ServletRequest request, final ServletResponse response, final String servletName) throws ServletException, IOException {
        error(servletRequestContext, request, response, servletName, null, null);
    }

    public void error(ServletRequestContext servletRequestContext, final ServletRequest request, final ServletResponse response, final String servletName, final Throwable exception) throws ServletException, IOException {
        error(servletRequestContext, request, response, servletName, exception, exception.getMessage());
    }

    private void error(ServletRequestContext servletRequestContext, final ServletRequest request, final ServletResponse response, final String servletName, final Throwable exception, final String message) throws ServletException, IOException {
        if(request.getDispatcherType() == DispatcherType.ERROR) {
            //we have already dispatched once with an error
            //if we dispatch again we run the risk of a stack overflow
            //so we just kill it, the user will just get the basic error page
            UndertowServletLogger.REQUEST_LOGGER.errorGeneratingErrorPage(servletRequestContext.getExchange().getRequestPath(), request.getAttribute(ERROR_EXCEPTION), servletRequestContext.getExchange().getStatusCode(), exception);
            servletRequestContext.getExchange().endExchange();
            return;
        }

        final HttpServletRequestImpl requestImpl = servletRequestContext.getOriginalRequest();
        final HttpServletResponseImpl responseImpl = servletRequestContext.getOriginalResponse();
        if (!servletContext.getDeployment().getDeploymentInfo().isAllowNonStandardWrappers()) {
            if (servletRequestContext.getOriginalRequest() != request) {
                if (!(request instanceof ServletRequestWrapper)) {
                    throw UndertowServletMessages.MESSAGES.requestWasNotOriginalOrWrapper(request);
                }
            }
            if (servletRequestContext.getOriginalResponse() != response) {
                if (!(response instanceof ServletResponseWrapper)) {
                    throw UndertowServletMessages.MESSAGES.responseWasNotOriginalOrWrapper(response);
                }
            }
        }

        final ServletRequest oldRequest = servletRequestContext.getServletRequest();
        final ServletResponse oldResponse = servletRequestContext.getServletResponse();
        servletRequestContext.setDispatcherType(DispatcherType.ERROR);

        //only update if this is the first forward
        requestImpl.setAttribute(ERROR_REQUEST_URI, requestImpl.getRequestURI());
        requestImpl.setAttribute(ERROR_SERVLET_NAME, servletName);
        if (exception != null) {
            requestImpl.setAttribute(ERROR_EXCEPTION, exception);
            requestImpl.setAttribute(ERROR_EXCEPTION_TYPE, exception.getClass());
        }
        requestImpl.setAttribute(ERROR_MESSAGE, message);
        requestImpl.setAttribute(ERROR_STATUS_CODE, responseImpl.getStatus());

        int qsPos = path.indexOf("?");
        String newServletPath = path;
        if (qsPos != -1) {
            Map<String, Deque<String>> queryParameters = requestImpl.getQueryParameters();
            String newQueryString = newServletPath.substring(qsPos + 1);
            newServletPath = newServletPath.substring(0, qsPos);

            String encoding = QueryParameterUtils.getQueryParamEncoding(servletRequestContext.getExchange());
            Map<String, Deque<String>> newQueryParameters = QueryParameterUtils.mergeQueryParametersWithNewQueryString(queryParameters, newQueryString, encoding);
            requestImpl.getExchange().setQueryString(newQueryString);
            requestImpl.setQueryParameters(newQueryParameters);
        }
        String newRequestUri = servletContext.getContextPath() + newServletPath;

        requestImpl.getExchange().setRelativePath(newServletPath);
        requestImpl.getExchange().setRequestPath(newRequestUri);
        requestImpl.getExchange().setRequestURI(newRequestUri);
        requestImpl.getExchange().getAttachment(ServletRequestContext.ATTACHMENT_KEY).setServletPathMatch(pathMatch);
        requestImpl.setServletContext(servletContext);
        responseImpl.setServletContext(servletContext);

        try {
            try {
                servletRequestContext.setServletRequest(request);
                servletRequestContext.setServletResponse(response);
                servletContext.getDeployment().getServletDispatcher().dispatchToPath(requestImpl.getExchange(), pathMatch, DispatcherType.ERROR);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            AsyncContextImpl ac = servletRequestContext.getOriginalRequest().getAsyncContextInternal();
            if(ac != null) {
                ac.complete();
            }
            servletRequestContext.setServletRequest(oldRequest);
            servletRequestContext.setServletResponse(oldResponse);
        }
    }

    public void mock(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;
            servletContext.getDeployment().getServletDispatcher().dispatchMockRequest(req, resp);
        } else {
            throw UndertowServletMessages.MESSAGES.invalidRequestResponseType(request, response);
        }
    }
}
