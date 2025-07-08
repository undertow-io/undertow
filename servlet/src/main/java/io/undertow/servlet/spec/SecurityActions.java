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
package io.undertow.servlet.spec;

import java.security.PrivilegedAction;

import javax.servlet.ServletContext;

import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;

@SuppressWarnings("removal")
class SecurityActions {
    static ServletRequestContext currentServletRequestContext() {
        if (System.getSecurityManager() == null) {
            return ServletRequestContext.current();
        } else {
            return java.security.AccessController.doPrivileged(
                    (PrivilegedAction<ServletRequestContext>) ServletRequestContext::current);
        }
    }

    static HttpSessionImpl forSession(final Session session, final ServletContext servletContext, final boolean newSession) {
        if (System.getSecurityManager() == null) {
            return HttpSessionImpl.forSession(session, servletContext, newSession);
        } else {
            return java.security.AccessController.doPrivileged(
                    (PrivilegedAction<HttpSessionImpl>) () -> HttpSessionImpl.forSession(session, servletContext, newSession));
        }
    }

    static void setCurrentRequestContext(final ServletRequestContext servletRequestContext) {
        if (System.getSecurityManager() == null) {
            ServletRequestContext.setCurrentRequestContext(servletRequestContext);
        } else {
            java.security.AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                ServletRequestContext.setCurrentRequestContext(servletRequestContext);
                return null;
            });
        }
    }

    static void clearCurrentServletAttachments() {
        if (System.getSecurityManager() == null) {
            ServletRequestContext.clearCurrentServletAttachments();
        } else {
            java.security.AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                ServletRequestContext.clearCurrentServletAttachments();
                return null;
            });
        }
    }
}
