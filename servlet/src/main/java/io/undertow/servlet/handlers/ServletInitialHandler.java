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
 *
 * @author Stuart Douglas
 */
public class ServletInitialHandler implements BlockingHttpHandler {

    private final BlockingHttpHandler next;

    final CompositeThreadSetupAction setupAction;

    private final ServletContextImpl servletContext;

    public ServletInitialHandler(final BlockingHttpHandler next, final CompositeThreadSetupAction setupAction, final ServletContextImpl servletContext) {
        this.next = next;
        this.setupAction = setupAction;
        this.servletContext = servletContext;
    }

    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        ThreadSetupAction.Handle handle = setupAction.setup(exchange);
        final HttpServletRequestImpl request = new HttpServletRequestImpl(exchange, servletContext);
        final HttpServletResponseImpl response = new HttpServletResponseImpl(exchange);
        if(exchange.getExchange().getAttachment(FilterHandler.DISPATCHER_TYPE_ATTACHMENT_KEY) == null) {
            exchange.getExchange().putAttachment(FilterHandler.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.REQUEST);
        }
        try {
            exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
            exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
            next.handleRequest(exchange);
        } finally {
            handle.tearDown();
            response.flushBuffer();
        }
    }

    public BlockingHttpHandler getNext() {
        return next;
    }
}
