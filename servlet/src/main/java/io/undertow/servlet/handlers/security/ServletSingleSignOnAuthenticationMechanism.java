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

import io.undertow.security.impl.SingleSignOnAuthenticationMechanism;
import io.undertow.security.impl.SingleSignOnManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;

import java.security.AccessController;

/**
 * Servlet version of the single sign on authentication mechanism.
 *
 * @author Stuart Douglas
 */
public class ServletSingleSignOnAuthenticationMechanism extends SingleSignOnAuthenticationMechanism {
    public ServletSingleSignOnAuthenticationMechanism(SingleSignOnManager storage) {
        super(storage);
    }

    @Override
    protected Session getSession(HttpServerExchange exchange) {
        ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        final HttpSessionImpl session = servletRequestContext.getCurrentServletContext().getSession(exchange, true);
        if(System.getSecurityManager() == null) {
            return session.getSession();
        } else {
            return AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(session));
        }
    }
}
