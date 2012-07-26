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

package tmp.texugo.server.handlers.blocking;

import org.xnio.IoUtils;
import tmp.texugo.TexugoLogger;
import tmp.texugo.server.HttpCompletionHandler;
import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

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

    public BlockingHandler() {
        this(null, null);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final BlockingHttpServerExchange blockingExchange = new BlockingHttpServerExchange(exchange);
        final Executor executor = this.executor;
        (executor == null ? exchange.getConnection().getWorker() : executor).execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final BlockingHttpHandler handler = BlockingHandler.this.handler;
                    if (handler != null) {
                        handler.handleRequest(blockingExchange);
                    }
                } catch (Throwable t) {
                    if (TexugoLogger.REQUEST_LOGGER.isDebugEnabled()) {
                        TexugoLogger.REQUEST_LOGGER.debugf(t, "Blocking request failed %s", blockingExchange);
                    }
                } finally {
                    IoUtils.safeClose(blockingExchange.getOutputStream());
                    completionHandler.handleComplete();
                }
            }
        });
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
