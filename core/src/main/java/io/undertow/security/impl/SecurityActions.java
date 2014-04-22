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
package io.undertow.security.impl;

import java.security.AccessController;
import java.security.PrivilegedAction;

import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpServerExchange;

class SecurityActions {
    static SecurityContextImpl createSecurityContextImpl(final HttpServerExchange exchange, final AuthenticationMode authenticationMode, final IdentityManager identityManager) {
        if (System.getSecurityManager() == null) {
            return new SecurityContextImpl(exchange, authenticationMode, identityManager);
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<SecurityContextImpl>() {
                @Override
                public SecurityContextImpl run() {
                    return new SecurityContextImpl(exchange, authenticationMode, identityManager);
                }
            });
        }
    }
}
