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
package io.undertow.servlet.handlers;

import java.security.PrivilegedAction;

import jakarta.servlet.ServletContext;

import io.undertow.server.session.Session;
import io.undertow.servlet.spec.HttpSessionImpl;

@SuppressWarnings("removal")
class SecurityActions {
    static HttpSessionImpl forSession(final Session session, final ServletContext servletContext, final boolean newSession) {
        if (System.getSecurityManager() == null) {
            return HttpSessionImpl.forSession(session, servletContext, newSession);
        } else {
            return java.security.AccessController.doPrivileged(
                    (PrivilegedAction<HttpSessionImpl>) () -> HttpSessionImpl.forSession(session, servletContext, newSession));
        }
    }

    static ServletRequestContext requireCurrentServletRequestContext() {
        if (System.getSecurityManager() == null) {
            return ServletRequestContext.requireCurrent();
        } else {
            return java.security.AccessController.doPrivileged(
                    (PrivilegedAction<ServletRequestContext>) ServletRequestContext::requireCurrent);
        }
    }
}
