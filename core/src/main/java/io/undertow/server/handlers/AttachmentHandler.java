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

package io.undertow.server.handlers;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Handler that adds an attachment to the request
 *
 * @author Stuart Douglas
 */
public class AttachmentHandler<T> implements HttpHandler {

    private final AttachmentKey<T> key;
    private volatile T instance;
    private volatile HttpHandler next;

    public AttachmentHandler(final AttachmentKey<T> key, final HttpHandler next, final T instance) {
        this.next = next;
        this.key = key;
        this.instance = instance;
    }

    public AttachmentHandler(final AttachmentKey<T> key, final HttpHandler next) {
        this(key, next, null);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.putAttachment(key, instance);
        next.handleRequest(exchange);
    }

    public T getInstance() {
        return instance;
    }

    public void setInstance(final T instance) {
        this.instance = instance;
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
    }
}
