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

package io.undertow.servlet.handlers;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.servlet.api.ServletDispatcher;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.core.ServletBlockingHttpExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.RequestDispatcherImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import org.xnio.IoUtils;

/**
 * This must be the initial handler in the blocking servlet chain. This sets up the request and response objects,
 * and attaches them the to exchange.
 *
 * @author Stuart Douglas
 */
public class ServletInitialHandler implements HttpHandler, ServletDispatcher {

    private final HttpHandler next;
    //private final HttpHandler asyncPath;

    private final CompositeThreadSetupAction setupAction;

    private final ServletContextImpl servletContext;

    private final ApplicationListeners listeners;

    private final ServletPathMatches paths;

    public ServletInitialHandler(final ServletPathMatches paths, final HttpHandler next, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext) {
        this.next = next;
        this.setupAction = setupAction;
        this.servletContext = servletContext;
        this.paths = paths;
        this.listeners = servletContext.getDeployment().getApplicationListeners();
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        final String path = exchange.getRelativePath();
        final ServletPathMatch info = paths.getServletHandlerByPath(path);
        final HttpServletResponseImpl response = new HttpServletResponseImpl(exchange, servletContext);
        final HttpServletRequestImpl request = new HttpServletRequestImpl(exchange, servletContext);
        exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
        exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);

        try {
            exchange.startBlocking(new ServletBlockingHttpExchange(exchange));
            exchange.putAttachment(ServletAttachments.SERVLET_PATH_MATCH, info);
            dispatchRequest(exchange, info, request, response, DispatcherType.REQUEST);
        } catch (Throwable t) {
            UndertowLogger.REQUEST_LOGGER.errorf(t, "Internal error handling servlet request %s", exchange.getRequestURI());
            exchange.endExchange();
        }
    }

    public void dispatchToPath(final HttpServerExchange exchange, final ServletPathMatch pathInfo, final DispatcherType dispatcherType) throws Exception {
        HttpServletRequestImpl req = HttpServletRequestImpl.getRequestImpl(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        HttpServletResponseImpl resp = HttpServletResponseImpl.getResponseImpl(exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY));
        exchange.putAttachment(ServletAttachments.SERVLET_PATH_MATCH, pathInfo);
        dispatchRequest(exchange, pathInfo, req, resp, dispatcherType);
    }

    @Override
    public void dispatchToServlet(final HttpServerExchange exchange, final ServletChain servletchain, final DispatcherType dispatcherType) throws Exception {
        HttpServletRequestImpl req = HttpServletRequestImpl.getRequestImpl(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        HttpServletResponseImpl resp = HttpServletResponseImpl.getResponseImpl(exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY));
        dispatchRequest(exchange, servletchain, req, resp, dispatcherType);
    }

    public void dispatchRequest(final HttpServerExchange exchange, final ServletChain servletChain, final HttpServletRequestImpl request, final HttpServletResponseImpl response, final DispatcherType dispatcherType) throws Exception {
        exchange.putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, dispatcherType);
        exchange.putAttachment(ServletAttachments.CURRENT_SERVLET, servletChain);
        if (dispatcherType == DispatcherType.REQUEST || dispatcherType == DispatcherType.ASYNC) {
            handleFirstRequest(exchange, servletChain, request, response);
        } else {
            next.handleRequest(exchange);
        }
    }

    public void handleFirstRequest(final HttpServerExchange exchange, final ServletChain servletChain, final HttpServletRequestImpl request, final HttpServletResponseImpl response) throws Exception {

        ThreadSetupAction.Handle handle = setupAction.setup(exchange);
        try {

            listeners.requestInitialized(request);
            next.handleRequest(exchange);
            if (!exchange.isResponseStarted() && exchange.getResponseCode() >= 400) {
                String location = servletContext.getDeployment().getErrorPages().getErrorLocation(exchange.getResponseCode());
                if (location != null) {
                    RequestDispatcherImpl dispatcher = new RequestDispatcherImpl(location, servletContext);
                    dispatcher.error(request, response, servletChain.getManagedServlet().getServletInfo().getName());
                }
            }
        } catch (Throwable t) {
            exchange.unDispatch();
            HttpServletRequestImpl.getRequestImpl(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY)).onAsyncError(t);
            if (!exchange.isResponseStarted()) {
                exchange.setResponseCode(500);
                exchange.getResponseHeaders().clear();
                String location = servletContext.getDeployment().getErrorPages().getErrorLocation(t);
                if (location == null && t instanceof ServletException) {
                    location = servletContext.getDeployment().getErrorPages().getErrorLocation(t.getCause());
                }
                if (location != null) {
                    RequestDispatcherImpl dispatcher = new RequestDispatcherImpl(location, servletContext);
                    try {
                        dispatcher.error(request, response, servletChain.getManagedServlet().getServletInfo().getName(), t);
                    } catch (Exception e) {
                        UndertowLogger.REQUEST_LOGGER.errorf(e, "Exception while generating error page %s", location);
                    }
                } else {
                    UndertowLogger.REQUEST_LOGGER.errorf(t, "Servlet request failed %s", exchange);
                }
            }
        } finally {
            handle.tearDown();
        }

        if (!exchange.isDispatched()) {
            try {
                request.getServletContext().getDeployment().getApplicationListeners().requestDestroyed(request);
            } finally {
                response.responseDone();
                //this request is done, so we close any parser that may have been used
                final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
                IoUtils.safeClose(parser);
            }
        }
    }

    public HttpHandler getNext() {
        return next;
    }

}
