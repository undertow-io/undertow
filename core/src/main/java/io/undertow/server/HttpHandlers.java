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

package io.undertow.server;

import java.util.concurrent.Executor;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;

/**
 * Utility methods pertaining to HTTP handlers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpHandlers {

    public static void executeRootHandler(final HttpHandler handler, final HttpServerExchange exchange, boolean inIoThread) {
        try {
            exchange.setInCall(true);
            handler.handleRequest(exchange);
            exchange.setInCall(false);
            if (exchange.isDispatched()) {
                final Runnable dispatchTask = exchange.getAttachment(HttpServerExchange.DISPATCH_TASK);
                Executor executor = exchange.getAttachment(HttpServerExchange.DISPATCH_EXECUTOR);
                exchange.unDispatch();
                if (dispatchTask != null) {
                    executor = executor == null ? exchange.getConnection().getWorker() : executor;
                    executor.execute(dispatchTask);
                }
            } else {
                exchange.endExchange();
            }
        } catch (Throwable t) {
            exchange.setInCall(false);
            if (!exchange.isResponseStarted()) {
                exchange.setResponseCode(500);
            }
            UndertowLogger.REQUEST_LOGGER.errorf(t, "Blocking request failed %s", exchange);
            exchange.endExchange();
        }
    }

    public static void handlerNotNull(final HttpHandler handler) {
        if (handler == null) {
            throw UndertowMessages.MESSAGES.handlerCannotBeNull();
        }
    }

}
