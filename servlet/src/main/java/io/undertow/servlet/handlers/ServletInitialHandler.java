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

import java.util.concurrent.Executor;

import javax.servlet.DispatcherType;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.util.WorkerDispatcher;

/**
 * This must be the initial handler in the blocking servlet chain. This sets up the request and response objects,
 * and attaches them the to exchange.
 * <p/>
 * This is both an async and a blocking handler, if it recieves an asyncrounous request it translates it to a blocking
 * request before continuing
 *
 * @author Stuart Douglas
 */
public class ServletInitialHandler implements BlockingHttpHandler, HttpHandler {

    private final BlockingHttpHandler next;
    private final HttpHandler asyncPath;

    final CompositeThreadSetupAction setupAction;

    private final ServletContextImpl servletContext;

    private volatile BlockingHttpHandler handler;
    /**
     * The information about the target5 servlet. This may be null.
     */
    private final ServletInfo servletInfo;

    public ServletInitialHandler(final BlockingHttpHandler next, final HttpHandler asyncPath, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext, final ServletInfo servletInfo) {
        this.next = next;
        this.asyncPath = asyncPath;
        this.setupAction = setupAction;
        this.servletContext = servletContext;
        this.servletInfo = servletInfo;
    }

    public ServletInitialHandler(final BlockingHttpHandler next, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext, final ServletInfo servletInfo) {
        this(next, null, setupAction, servletContext, servletInfo);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        if (asyncPath != null) {
            //if the next handler is the default servlet we just execute it directly
            HttpHandlers.executeHandler(asyncPath, exchange, completionHandler);
            return;
        }
        final BlockingHttpServerExchange blockingExchange = new BlockingHttpServerExchange(exchange, completionHandler);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    final BlockingHttpHandler handler = ServletInitialHandler.this;
                    handler.handleRequest(blockingExchange);
                } catch (Throwable t) {
                    try {
                        if (!exchange.isResponseStarted()) {
                            exchange.setResponseCode(500);
                        }
                        UndertowLogger.REQUEST_LOGGER.errorf(t, "Servlet request failed %s", blockingExchange);
                    } finally {
                        completionHandler.handleComplete();
                    }
                }
            }
        };
        WorkerDispatcher.dispatch(exchange, runnable);
    }


    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        boolean first = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY) == null;
        if (first) {
            handleFirstRequest(exchange);
        } else {
            handleDispatchedRequest(exchange);
        }
    }


    private void handleDispatchedRequest(final BlockingHttpServerExchange exchange) throws Exception {
        final ThreadSetupAction.Handle handle = setupAction.setup(exchange);
        try {
            next.handleRequest(exchange);
        } finally {
            handle.tearDown();
        }
    }

    private void handleFirstRequest(final BlockingHttpServerExchange exchange) throws Exception {
        exchange.getExchange().putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.REQUEST);
        final HttpServletResponseImpl response = new HttpServletResponseImpl(exchange);
        HttpServletRequestImpl request = new HttpServletRequestImpl(exchange, servletContext);
        ThreadSetupAction.Handle handle = setupAction.setup(exchange);
        try {
            exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
            exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
            next.handleRequest(exchange);
        } finally {
            handle.tearDown();
        }
        //exceptions that can be handled will not be propagated to this point, they will
        //be handled by other handlers in the chain. If an exception propages to this point
        //this is does not matter that the response is not finished here, as the
        //outer runnable will call it
        response.responseDone(exchange.getCompletionHandler());
    }

    public BlockingHttpHandler getHandler() {
        return handler;
    }

    public void setRootHandler(final BlockingHttpHandler rootHandler) {
        HttpHandlers.handlerNotNull(rootHandler);
        this.handler = rootHandler;
    }

    public BlockingHttpHandler getNext() {
        return next;
    }

    public ServletInfo getServletInfo() {
        return servletInfo;
    }
}
