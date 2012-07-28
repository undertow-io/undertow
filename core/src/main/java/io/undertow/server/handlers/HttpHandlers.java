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

import io.undertow.TexugoMessages;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Utility methods pertaining to HTTP handlers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpHandlers {

    /**
     * Safely execute a handler.  If the handler throws an exception before completing, this method will attempt
     * to set a 500 status code and complete the request.
     *
     * @param handler the handler to execute
     * @param exchange the HTTP exchange for the request
     * @param completionHandler the completion handler
     */
    public static void executeHandler(final HttpHandler handler, final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        try {
            handler.handleRequest(exchange, completionHandler);
        } catch (Throwable t) {
            try {
                exchange.setResponseCode(500);
                completionHandler.handleComplete();
            } catch (Throwable ignored) {}
        }
    }

    public static void handlerNotNull(final HttpHandler handler) {
        if(handler == null) {
            throw TexugoMessages.MESSAGES.handlerCannotBeNull();
        }
    }
}
