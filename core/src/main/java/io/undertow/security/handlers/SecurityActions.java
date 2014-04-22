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
package io.undertow.security.handlers;

import java.security.AccessController;
import java.security.PrivilegedAction;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

class SecurityActions {
    static void setSecurityContext(final HttpServerExchange exchange, final SecurityContext securityContext) {
        if (System.getSecurityManager() == null) {
            exchange.setSecurityContext(securityContext);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    exchange.setSecurityContext(securityContext);
                    return null;
                }
            });
        }
    }

}
