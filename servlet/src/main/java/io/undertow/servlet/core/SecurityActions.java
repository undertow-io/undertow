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

import io.undertow.server.HttpHandler;
import io.undertow.server.session.Session;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import jakarta.servlet.ServletContext;

import java.security.PrivilegedAction;

@SuppressWarnings("removal")
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
            return java.security.AccessController.doPrivileged(
                    (PrivilegedAction<ClassLoader>) () -> Thread.currentThread().getContextClassLoader());
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
            java.security.AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                Thread.currentThread().setContextClassLoader(classLoader);
                return null;
            });
        }
    }

    static String getSystemProperty(final String prop) {
        if (System.getSecurityManager() == null) {
           return System.getProperty(prop);
        } else {
            return (String) java.security.AccessController.doPrivileged((PrivilegedAction<Object>) () -> System.getProperty(prop));
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

    static ServletRequestContext currentServletRequestContext() {
        if (System.getSecurityManager() == null) {
            return ServletRequestContext.current();
        } else {
            return java.security.AccessController.doPrivileged(
                    (PrivilegedAction<ServletRequestContext>) ServletRequestContext::current);
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

    static ServletInitialHandler createServletInitialHandler(final ServletPathMatches paths, final HttpHandler next, final Deployment deployment, final ServletContextImpl servletContext) {
        if (System.getSecurityManager() == null) {
            return new ServletInitialHandler(paths, next, deployment, servletContext);
        } else {
            return java.security.AccessController.doPrivileged(
                    (PrivilegedAction<ServletInitialHandler>) () -> new ServletInitialHandler(paths, next, deployment, servletContext));
        }
    }
}
