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


import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.servlet.ServletContext;

import io.undertow.server.HttpHandler;
import io.undertow.server.session.Session;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.spec.ServletContextImpl;

final class SecurityActions {

    private SecurityActions() {
        // forbidden inheritance
    }

    /**
     * Gets context classloader.
     *
     * @return the current context classloader
     */
    static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
        }
    }

    /**
     * Sets context classloader.
     *
     * @param classLoader
     *            the classloader
     */
    static void setContextClassLoader(final ClassLoader classLoader) {
        if (System.getSecurityManager() == null) {
            Thread.currentThread().setContextClassLoader(classLoader);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    Thread.currentThread().setContextClassLoader(classLoader);
                    return null;
                }
            });
        }
    }

    static String getSystemProperty(final String prop) {
        if (System.getSecurityManager() == null) {
           return System.getProperty(prop);
        } else {
            return (String) AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    return System.getProperty(prop);
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
    static ServletInitialHandler createServletInitialHandler(final ServletPathMatches paths, final HttpHandler next, final Deployment deployment, final ServletContextImpl servletContext) {
        if (System.getSecurityManager() == null) {
            return new ServletInitialHandler(paths, next, deployment, servletContext);
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ServletInitialHandler>() {
                @Override
                public ServletInitialHandler run() {
                    return new ServletInitialHandler(paths, next, deployment, servletContext);
                }
            });
        }
    }
}
