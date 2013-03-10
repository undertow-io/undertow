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
import javax.servlet.ServletRequest;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.spec.HttpServletRequestImpl;

/**
 * @author Stuart Douglas
 */
public class RequestListenerHandler implements HttpHandler {

    private final ApplicationListeners listeners;

    private final HttpHandler next;

    public RequestListenerHandler(final ApplicationListeners listeners, final HttpHandler next) {
        this.listeners = listeners;
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        DispatcherType type = exchange.getAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY);
        if (type == DispatcherType.REQUEST) {
            final ServletRequest request = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
            listeners.requestInitialized(request);
            try {
                next.handleRequest(exchange);
            } finally {
                if (!request.isAsyncStarted()) {
                    listeners.requestDestroyed(request);
                }
            }
        } else if (type == DispatcherType.ASYNC) {
            final ServletRequest request = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
            try {
                next.handleRequest(exchange);
            } finally {
                if (!request.isAsyncStarted()) {
                    listeners.requestDestroyed(request);
                }
            }
        } else {
            next.handleRequest(exchange);
        }
    }

    public HttpHandler getNext() {
        return next;
    }
}
