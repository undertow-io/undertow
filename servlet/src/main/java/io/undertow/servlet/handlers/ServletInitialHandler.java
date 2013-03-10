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
import io.undertow.server.HttpHandlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.core.ManagedServlet;
import io.undertow.servlet.core.ServletBlockingHttpExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.RequestDispatcherImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import org.xnio.IoUtils;

/**
 * This must be the initial handler in the blocking servlet chain. This sets up the request and response objects,
 * and attaches them the to exchange.
 * <p/>
 * This is both an async and a blocking handler, if it receives an asynchronous request it translates it to a blocking
 * request before continuing
 *
 * @author Stuart Douglas
 */
public class ServletInitialHandler implements BlockingHttpHandler, HttpHandler {

    private final BlockingHttpHandler next;
    //private final HttpHandler asyncPath;

    private final CompositeThreadSetupAction setupAction;

    private final ServletContextImpl servletContext;

    private volatile BlockingHttpHandler handler;
    /**
     * The target servlet
     */
    private final ManagedServlet managedServlet;

    public ServletInitialHandler(final BlockingHttpHandler next, final HttpHandler asyncPath, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext, final ManagedServlet managedServlet) {
        this.next = next;
        //this.asyncPath = asyncPath;
        this.setupAction = setupAction;
        this.servletContext = servletContext;
        this.managedServlet = managedServlet;
    }

    public ServletInitialHandler(final BlockingHttpHandler next, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext, final ManagedServlet managedServlet) {
        this(next, null, setupAction, servletContext, managedServlet);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
//        if (asyncPath != null) {
//            //if the next handler is the default servlet we just execute it directly
//            HttpHandlers.executeHandler(asyncPath, exchange);
//            //this is not great, but as the file was not found we need to do error handling
//            //so re just run the request again but via the normal servlet path
//            //todo: fix this, we should just be able to run the error handling code without copy/pasting heaps of
//            //code
//            if (exchange.getResponseCode() != 404) {
//                return;
//            }
//        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    exchange.startBlocking(new ServletBlockingHttpExchange(exchange));
                    final BlockingHttpHandler handler = ServletInitialHandler.this;
                    handler.handleBlockingRequest(exchange);
                } catch (Throwable t) {
                    UndertowLogger.REQUEST_LOGGER.errorf(t, "Internal error handling servlet request %s", exchange.getRequestURI());
                    exchange.endExchange();
                }
            }
        };
        exchange.dispatch(runnable);
    }


    @Override
    public void handleBlockingRequest(final HttpServerExchange exchange) throws Exception {
        ServletInfo old = exchange.getAttachment(ServletAttachments.CURRENT_SERVLET);
        try {
            exchange.putAttachment(ServletAttachments.CURRENT_SERVLET, managedServlet.getServletInfo());
            DispatcherType dispatcher = exchange.getAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY);
            boolean first = dispatcher == null || dispatcher == DispatcherType.ASYNC;
            if (first) {
                handleFirstRequest(exchange, dispatcher);
            } else {
                handleDispatchedRequest(exchange);
            }
        } finally {
            exchange.putAttachment(ServletAttachments.CURRENT_SERVLET, old);
        }
    }


    private void handleDispatchedRequest(final HttpServerExchange exchange) throws Exception {
        final ThreadSetupAction.Handle handle = setupAction.setup(exchange);
        try {
            next.handleBlockingRequest(exchange);
        } finally {
            handle.tearDown();
        }
    }

    private void handleFirstRequest(final HttpServerExchange exchange, final DispatcherType dispatcherType) throws Exception {

        ThreadSetupAction.Handle handle = setupAction.setup(exchange);
        try {
            if (dispatcherType == null) {
                final HttpServletResponseImpl response = new HttpServletResponseImpl(exchange, servletContext);
                HttpServletRequestImpl request = new HttpServletRequestImpl(exchange, servletContext);
                exchange.putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.REQUEST);
                exchange.putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
            }

            next.handleBlockingRequest(exchange);
            if (!exchange.isResponseStarted() && exchange.getResponseCode() >= 400) {
                String location = servletContext.getDeployment().getErrorPages().getErrorLocation(exchange.getResponseCode());
                if (location != null) {
                    RequestDispatcherImpl dispatcher = new RequestDispatcherImpl(location, servletContext);
                    dispatcher.error(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY), exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY), managedServlet.getServletInfo().getName());
                }
            }
        } catch (Throwable t) {
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
                        dispatcher.error(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY), exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY), managedServlet.getServletInfo().getName(), t);
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
        //exceptions that can be handled will not be propagated to this point, they will
        //be handled by other handlers in the chain. If an exception propagates to this point
        //this is does not matter that the response is not finished here, as the
        //outer runnable will call the completion handler
        final HttpServletRequestImpl request = HttpServletRequestImpl.getRequestImpl(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        final HttpServletResponseImpl response = HttpServletResponseImpl.getResponseImpl(exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY));

        if (!request.isAsyncStarted()) {
            response.responseDone();
            //this request is done, so we close any parser that may have been used
            final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
            IoUtils.safeClose(parser);
        } else {
            request.asyncInitialRequestDone();
        }
    }

    public BlockingHttpHandler getHandler() {
        return handler;
    }

    public ServletInitialHandler setRootHandler(final BlockingHttpHandler rootHandler) {
        HttpHandlers.handlerNotNull(rootHandler);
        this.handler = rootHandler;
        return this;
    }

    public BlockingHttpHandler getNext() {
        return next;
    }

    public ManagedServlet getManagedServlet() {
        return managedServlet;
    }
}
