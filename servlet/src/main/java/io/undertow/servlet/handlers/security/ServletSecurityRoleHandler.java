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
package io.undertow.servlet.handlers.security;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.handlers.ServletAttachments;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

import java.util.List;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet role handler
 *
 * @author Stuart Douglas
 */
public class ServletSecurityRoleHandler implements HttpHandler {

    private final HttpHandler next;

    public ServletSecurityRoleHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        List<SingleConstraintMatch> constraints = exchange.getAttachmentList(ServletAttachments.REQUIRED_CONSTRAINTS);
        SecurityContext sc = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        HttpServletRequest request = HttpServletRequestImpl.getRequestImpl(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            next.handleRequest(exchange);
        } else if (constraints == null || constraints.isEmpty()) {
            next.handleRequest(exchange);
        } else {
            Account account = sc.getAuthenticatedAccount();
            for (final SingleConstraintMatch constraint : constraints) {
                boolean found = false;

                Set<String> roleSet = constraint.getRequiredRoles();
                if (roleSet.isEmpty() && constraint.getEmptyRoleSemantic() != SecurityInfo.EmptyRoleSemantic.DENY) {
                    /*
                     * The EmptyRoleSemantic was either PERMIT or AUTHENTICATE, either way a roles check is not needed.
                     */
                    found = true;
                } else {
                    for (String role : roleSet) {
                        if (account.isUserInRole(role)) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    HttpServletResponse response = (HttpServletResponse) exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
                    response.sendError(403);
                    return;
                }
            }
            next.handleRequest(exchange);
        }
    }


}
