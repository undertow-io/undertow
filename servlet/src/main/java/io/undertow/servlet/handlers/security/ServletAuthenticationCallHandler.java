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
package io.undertow.servlet.handlers.security;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.StatusCodes;

/**
 * This is the final {@link io.undertow.server.HttpHandler} in the security chain, it's purpose is to act as a barrier at the end of the chain to
 * ensure authenticate is called after the mechanisms have been associated with the context and the constraint checked.
 *
 * This handler uses the Servlet {@link jakarta.servlet.http.HttpServletResponse#sendError(int)} method to make
 * sure the correct error page is displayed.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ServletAuthenticationCallHandler implements HttpHandler {

    private final HttpHandler next;

    public ServletAuthenticationCallHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     * Only allow the request through if successfully authenticated or if authentication is not required.
     *
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if(exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        SecurityContext context = exchange.getSecurityContext();
        if (context.authenticate()) {
            if(!exchange.isComplete()) {
               next.handleRequest(exchange);
            }
        } else {
            if(exchange.getStatusCode() >= StatusCodes.BAD_REQUEST && !exchange.isComplete()) {
                ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
                src.getOriginalResponse().sendError(exchange.getStatusCode());
            } else {
                exchange.endExchange();
            }
        }
    }

}
