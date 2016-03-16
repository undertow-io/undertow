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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import io.undertow.server.session.Session;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.util.IteratorEnumeration;

/**
 * The HTTP session implementation.
 *
 * Note that for security reasons no attribute names that start with io.undertow are allowed.
 *
 * @author Stuart Douglas
 */
public class HttpSessionImpl implements HttpSession {

    private static final RuntimePermission PERMISSION = new RuntimePermission("io.undertow.servlet.spec.UNWRAP_HTTP_SESSION");

    public static final String IO_UNDERTOW = "io.undertow";
    private final Session session;
    private final ServletContext servletContext;
    private final boolean newSession;
    private volatile boolean invalid;
    private final ServletRequestContext servletRequestContext;

    private HttpSessionImpl(final Session session, final ServletContext servletContext, final boolean newSession, ServletRequestContext servletRequestContext) {
        this.session = session;
        this.servletContext = servletContext;
        this.newSession = newSession;
        this.servletRequestContext = servletRequestContext;
    }

    public static HttpSessionImpl forSession(final Session session, final ServletContext servletContext, final boolean newSession) {
        // forSession is called by privileged actions only so no need to do it again
        ServletRequestContext current = ServletRequestContext.current();
        if (current == null) {
            return new HttpSessionImpl(session, servletContext, newSession, null);
        } else {
            HttpSessionImpl httpSession = current.getSession();
            if (httpSession == null) {
                httpSession = new HttpSessionImpl(session, servletContext, newSession, current);
                current.setSession(httpSession);
            } else {
                if(httpSession.session != session) {
                    //in some rare cases it may be that there are two different service contexts involved in the one request
                    //in this case we just return a new session rather than using the thread local version
                    httpSession = new HttpSessionImpl(session, servletContext, newSession, current);
                }
            }
            return httpSession;
        }
    }

    @Override
    public long getCreationTime() {
        return session.getCreationTime();
    }

    @Override
    public String getId() {
        return session.getId();
    }

    @Override
    public long getLastAccessedTime() {
        return session.getLastAccessedTime();
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public void setMaxInactiveInterval(final int interval) {
        session.setMaxInactiveInterval(interval);
    }

    @Override
    public int getMaxInactiveInterval() {
        return session.getMaxInactiveInterval();
    }

    @Override
    public HttpSessionContext getSessionContext() {
        return null;
    }

    @Override
    public Object getAttribute(final String name) {
        if(name.startsWith(IO_UNDERTOW)) {
            throw new SecurityException();
        }
        return session.getAttribute(name);
    }

    @Override
    public Object getValue(final String name) {
        if(name.startsWith(IO_UNDERTOW)) {
            throw new SecurityException();
        }
        return getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        Set<String> attributeNames = getFilteredAttributeNames();
        return new IteratorEnumeration<>(attributeNames.iterator());
    }

    private Set<String> getFilteredAttributeNames() {
        Set<String> attributeNames = new HashSet<>(session.getAttributeNames());
        Iterator<String> it = attributeNames.iterator();
        while (it.hasNext()) {
            if(it.next().startsWith(IO_UNDERTOW)) {
                it.remove();
            }
        }
        return attributeNames;
    }

    @Override
    public String[] getValueNames() {
        Set<String> names = getFilteredAttributeNames();
        String[] ret = new String[names.size()];
        int i = 0;
        for (String name : names) {
            ret[i++] = name;
        }
        return ret;
    }

    @Override
    public void setAttribute(final String name, final Object value) {
        if(name.startsWith(IO_UNDERTOW)) {
            throw new SecurityException();
        }
        if (value == null) {
            removeAttribute(name);
        } else {
            session.setAttribute(name, value);
        }
    }

    @Override
    public void putValue(final String name, final Object value) {
        setAttribute(name, value);
    }

    @Override
    public void removeAttribute(final String name) {
        if(name.startsWith(IO_UNDERTOW)) {
            throw new SecurityException();
        }
        session.removeAttribute(name);
    }

    @Override
    public void removeValue(final String name) {
        removeAttribute(name);
    }

    @Override
    public void invalidate() {
        invalid = true;
        if (servletRequestContext == null) {
            session.invalidate(null);
        } else {
            if(servletRequestContext.getOriginalRequest().getServletContext() == servletContext) {
                session.invalidate(servletRequestContext.getOriginalRequest().getExchange());
            } else {
                session.invalidate(null);
            }
        }
    }

    @Override
    public boolean isNew() {
        if (invalid) {
            throw UndertowServletMessages.MESSAGES.sessionIsInvalid();
        }
        return newSession;
    }

    public Session getSession() {
        SecurityManager sm = System.getSecurityManager();
        if(sm != null) {
            sm.checkPermission(PERMISSION);
        }
        return session;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HttpSessionImpl that = (HttpSessionImpl) o;

        return session.getId().equals(that.session.getId());

    }

    @Override
    public int hashCode() {
        return session.getId().hashCode();
    }

    public boolean isInvalid() {
        return invalid;
    }

    public static class UnwrapSessionAction implements PrivilegedAction<Session> {

        private final HttpSessionImpl session;

        public UnwrapSessionAction(HttpSession session) {
            this.session = (HttpSessionImpl) session;
        }

        @Override
        public Session run() {
            return session.getSession();
        }
    }
}
