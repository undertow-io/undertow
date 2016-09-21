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

package io.undertow.servlet.core;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * @author Stuart Douglas
 */
class ServletRequestContextThreadSetupAction implements ThreadSetupHandler {

    static final ServletRequestContextThreadSetupAction INSTANCE = new ServletRequestContextThreadSetupAction();

    private ServletRequestContextThreadSetupAction() {

    }

    @Override
    public <T, C> Action<T, C> create(final Action<T, C> action) {
        return new Action<T, C>() {
            @Override
            public T call(HttpServerExchange exchange, C context) throws Exception {
                if (exchange == null) {
                    return action.call(null, context);
                } else {
                    ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
                    final ServletRequestContext old = ServletRequestContext.current();
                    SecurityActions.setCurrentRequestContext(servletRequestContext);
                    try {
                        return action.call(exchange, context);
                    } finally {
                        ServletRequestContext.setCurrentRequestContext(old);
                    }
                }
            }
        };
    }
}
