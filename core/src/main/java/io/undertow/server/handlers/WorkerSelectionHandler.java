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

package io.undertow.server.handlers;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.WorkerDispatcher;

/**
 * Handler that sets the current executor to use for blocking operations.
 *
 * If this executor is null than any previously set executor will be null and the
 * XNIO worker will be used instead;
 *
 *
 * @author Stuart Douglas
 */
public class WorkerSelectionHandler implements HttpHandler {

    private volatile Executor executor;
    private volatile HttpHandler next;
    private final AtomicReferenceFieldUpdater<WorkerSelectionHandler, Executor> executorUpdater = AtomicReferenceFieldUpdater.newUpdater(WorkerSelectionHandler.class, Executor.class, "executor");

    public WorkerSelectionHandler(final HttpHandler next, final Executor executor) {
        this.next = next;
        this.executor = executor;
    }

    public WorkerSelectionHandler(final HttpHandler next) {
        this(next, null);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        exchange.putAttachment(WorkerDispatcher.EXECUTOR_ATTACHMENT_KEY, executor);
        HttpHandlers.executeHandler(next, exchange, completionHandler);
    }

    public Executor getExecutor() {
        return executor;
    }

    public Executor setExecutor(final Executor executor) {
        return executorUpdater.getAndSet(this, executor);
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }
}
