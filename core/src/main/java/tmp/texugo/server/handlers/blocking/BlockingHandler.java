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

import java.util.concurrent.ExecutorService;

import tmp.texugo.server.HttpHandler;
import tmp.texugo.server.HttpServerExchange;

/**
 * A {@link HttpHandler} that initiates a blocking request.
 *
 * @author Stuart Douglas
 */
public class BlockingHandler implements HttpHandler {

    private volatile ExecutorService executorService;
    private volatile BlockingHandler rootHandler;

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final BlockingHttpServerExchange blockingExchange = new BlockingHttpServerExchange(exchange);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Sets the executor service used by this handler. The old executor service will not be shut down.
     * @param executorService The executor service to use
     * @return The previous executor service
     */
    public synchronized ExecutorService setExecutorService(final ExecutorService executorService) {
        ExecutorService old = this.executorService;
        this.executorService = executorService;
        return old;
    }

    public BlockingHandler getRootHandler() {
        return rootHandler;
    }

    public void setRootHandler(final BlockingHandler rootHandler) {
        this.rootHandler = rootHandler;
    }
}
