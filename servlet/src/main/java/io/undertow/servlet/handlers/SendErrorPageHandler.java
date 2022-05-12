/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import jakarta.servlet.http.HttpServletResponse;

/**
 * A handler that sends the servlet's error page if the status code is greater than 399
 *
 * @author Brad Wood
 */
public class SendErrorPageHandler implements HttpHandler {

    private final HttpHandler next;

    /**
     * Construct a new instance.
     *
     * @param next The next handler to call if there is no error response
     */
    public SendErrorPageHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);

        // If the servlet is available and the status code has been set to an error, return the error page
        if( src != null && exchange.getStatusCode() > 399 && !exchange.isResponseStarted() ) {
            ((HttpServletResponse)src.getServletResponse()).sendError(exchange.getStatusCode());
        } else {
            next.handleRequest(exchange);
        }
    }

}
