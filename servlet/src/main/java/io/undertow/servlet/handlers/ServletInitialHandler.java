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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.servlet.DispatcherType;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.ServletContextImpl;

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

    private volatile Executor executor;
    private volatile BlockingHttpHandler handler;

    private static final AtomicReferenceFieldUpdater<ServletInitialHandler, Executor> executorUpdater = AtomicReferenceFieldUpdater.newUpdater(ServletInitialHandler.class, Executor.class, "executor");

    public ServletInitialHandler(final BlockingHttpHandler next, final HttpHandler asyncPath, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext) {
        this.next = next;
        this.asyncPath = asyncPath;
        this.setupAction = setupAction;
        this.servletContext = servletContext;
    }

    public ServletInitialHandler(final BlockingHttpHandler next, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext) {
        this(next, null, setupAction, servletContext);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        if (asyncPath != null) {
            //if the next handler is the default servlet we just execute it directly
            HttpHandlers.executeHandler(asyncPath, exchange, completionHandler);
            return;
        }
        final BlockingHttpServerExchange blockingExchange = new BlockingHttpServerExchange(exchange);
        final Executor executor = this.executor;
        (executor == null ? exchange.getConnection().getWorker() : executor).execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final BlockingHttpHandler handler = ServletInitialHandler.this;
                    handler.handleRequest(blockingExchange);
                } catch (Throwable t) {
                    if (!exchange.isResponseStarted()) {
                        exchange.setResponseCode(500);
                    }
                    UndertowLogger.REQUEST_LOGGER.errorf(t, "Blocking request failed %s", blockingExchange);
                } finally {
                    completionHandler.handleComplete();
                }
            }
        });
    }


    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        ThreadSetupAction.Handle handle = setupAction.setup(exchange);
        if (exchange.getExchange().getAttachment(FilterHandler.DISPATCHER_TYPE_ATTACHMENT_KEY) == null) {
            exchange.getExchange().putAttachment(FilterHandler.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.REQUEST);
        }
        boolean first = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY) == null;
        if (first) {
            final HttpServletRequestImpl request = new HttpServletRequestImpl(exchange, servletContext);
            final HttpServletResponseImpl response = new HttpServletResponseImpl(exchange);
            try {
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                next.handleRequest(exchange);
            } finally {
                handle.tearDown();
                response.flushBuffer();
            }
        } else {
            next.handleRequest(exchange);
        }
    }

    public Executor getExecutor() {
        return executor;
    }

    /**
     * Sets the executor used by this handler. The old executor will not be shut down.
     *
     * @param executor The executor to use
     * @return The previous executor
     */
    public Executor setExecutor(final Executor executor) {
        return executorUpdater.getAndSet(this, executor);
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
}
