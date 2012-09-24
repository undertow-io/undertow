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

package io.undertow.server.handlers.blocking;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.WorkerDispatcher;

/**
 * A {@link HttpHandler} that initiates a blocking request.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class BlockingHandler implements HttpHandler {

    private volatile Executor executor;
    private volatile BlockingHttpHandler handler;

    private static final AtomicReferenceFieldUpdater<BlockingHandler, Executor> executorUpdater = AtomicReferenceFieldUpdater.newUpdater(BlockingHandler.class, Executor.class, "executor");
    private static final AtomicReferenceFieldUpdater<BlockingHandler, BlockingHttpHandler> handlerUpdater = AtomicReferenceFieldUpdater.newUpdater(BlockingHandler.class, BlockingHttpHandler.class, "handler");

    public BlockingHandler(final Executor executor, final BlockingHttpHandler handler) {
        this.executor = executor;
        this.handler = handler;
    }

    public BlockingHandler(final BlockingHttpHandler handler) {
        this(null, handler);
    }

    public BlockingHandler() {
        this(null, null);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final BlockingHttpServerExchange blockingExchange = new BlockingHttpServerExchange(exchange);
        final Executor executor = this.executor;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    final BlockingHttpHandler handler = BlockingHandler.this.handler;
                    if (handler != null) {
                        handler.handleRequest(blockingExchange);
                    }
                } catch (Throwable t) {
                    if (!exchange.isResponseStarted()) {
                        exchange.setResponseCode(500);
                    }
                    UndertowLogger.REQUEST_LOGGER.errorf(t, "Blocking request failed %s", blockingExchange);
                } finally {
                    completionHandler.handleComplete();
                }
            }
        };
        WorkerDispatcher.dispatch(executor, exchange, runnable);
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

    public BlockingHttpHandler setRootHandler(final BlockingHttpHandler rootHandler) {
        return handlerUpdater.getAndSet(this, rootHandler);
    }
}
