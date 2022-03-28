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
import io.undertow.servlet.api.AuthorizationManager;
import io.undertow.servlet.api.SingleConstraintMatch;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.StatusCodes;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Servlet role handler
 *
 * @author Stuart Douglas
 */
public class ServletSecurityRoleHandler implements HttpHandler {

    private final HttpHandler next;
    private final AuthorizationManager authorizationManager;

    public ServletSecurityRoleHandler(final HttpHandler next, AuthorizationManager authorizationManager) {
        this.next = next;
        this.authorizationManager = authorizationManager;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        ServletRequest request = servletRequestContext.getServletRequest();
        if (request.getDispatcherType() == DispatcherType.REQUEST) {
            List<SingleConstraintMatch> constraints = servletRequestContext.getRequiredConstrains();
            SecurityContext sc = exchange.getSecurityContext();
            if (!authorizationManager.canAccessResource(constraints, sc.getAuthenticatedAccount(), servletRequestContext.getCurrentServlet().getManagedServlet().getServletInfo(), servletRequestContext.getOriginalRequest(), servletRequestContext.getDeployment())) {

                HttpServletResponse response = (HttpServletResponse) servletRequestContext.getServletResponse();
                response.sendError(StatusCodes.FORBIDDEN);
                return;
            }
        }
        next.handleRequest(exchange);
    }


}
