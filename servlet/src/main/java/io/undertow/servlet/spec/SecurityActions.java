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

import java.security.AccessController;
import java.security.PrivilegedAction;

import jakarta.servlet.ServletContext;

import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;


class SecurityActions {
    static ServletRequestContext currentServletRequestContext() {
        if (System.getSecurityManager() == null) {
            return ServletRequestContext.current();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ServletRequestContext>() {
                @Override
                public ServletRequestContext run() {
                    return ServletRequestContext.current();
                }
            });
        }
    }

    static HttpSessionImpl forSession(final Session session, final ServletContext servletContext, final boolean newSession) {
        if (System.getSecurityManager() == null) {
            return HttpSessionImpl.forSession(session, servletContext, newSession);
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<HttpSessionImpl>() {
                @Override
                public HttpSessionImpl run() {
                    return HttpSessionImpl.forSession(session, servletContext, newSession);
                }
            });
        }
    }

    static void setCurrentRequestContext(final ServletRequestContext servletRequestContext) {
        if (System.getSecurityManager() == null) {
            ServletRequestContext.setCurrentRequestContext(servletRequestContext);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    ServletRequestContext.setCurrentRequestContext(servletRequestContext);
                    return null;
                }
            });
        }
    }

    static void clearCurrentServletAttachments() {
        if (System.getSecurityManager() == null) {
            ServletRequestContext.clearCurrentServletAttachments();
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    ServletRequestContext.clearCurrentServletAttachments();
                    return null;
                }
            });
        }
    }

    static ServletRequestContext requireCurrentServletRequestContext() {
        if (System.getSecurityManager() == null) {
            return ServletRequestContext.requireCurrent();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ServletRequestContext>() {
                @Override
                public ServletRequestContext run() {
                    return ServletRequestContext.requireCurrent();
                }
            });
        }
    }
}
