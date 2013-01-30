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
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.spec.HttpServletRequestImpl;

/**
 * @author Stuart Douglas
 */
public class RequestListenerHandler implements BlockingHttpHandler {

    private final ApplicationListeners listeners;

    private final BlockingHttpHandler next;

    public RequestListenerHandler(final ApplicationListeners listeners, final BlockingHttpHandler next) {
        this.listeners = listeners;
        this.next = next;
    }

    @Override
    public void handleBlockingRequest(final HttpServerExchange exchange) throws Exception {
        DispatcherType type = exchange.getAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY);
        if (type == DispatcherType.REQUEST) {
            final ServletRequest request = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
            listeners.requestInitialized(request);
            try {
                next.handleBlockingRequest(exchange);
            } finally {
                if (!request.isAsyncStarted()) {
                    listeners.requestDestroyed(request);
                }
            }
        } else if (type == DispatcherType.ASYNC) {
            final ServletRequest request = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
            try {
                next.handleBlockingRequest(exchange);
            } finally {
                if (!request.isAsyncStarted()) {
                    listeners.requestDestroyed(request);
                }
            }
        } else {
            next.handleBlockingRequest(exchange);
        }
    }

    public BlockingHttpHandler getNext() {
        return next;
    }
}
