/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.spec;

import java.util.Enumeration;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import io.undertow.server.session.Session;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.util.IteratorEnumeration;

/**
 * @author Stuart Douglas
 */
public class HttpSessionImpl implements HttpSession {

    private final Session session;
    private final ServletContext servletContext;
    private final boolean newSession;
    private volatile boolean invalid;

    private HttpSessionImpl(final Session session, final ServletContext servletContext, final boolean newSession) {
        this.session = session;
        this.servletContext = servletContext;
        this.newSession = newSession;
    }

    public static HttpSessionImpl forSession(final Session session, final ServletContext servletContext, final boolean newSession) {
        ServletRequestContext current = ServletRequestContext.current();
        if (current == null) {
            return new HttpSessionImpl(session, servletContext, newSession);
        } else {
            HttpSessionImpl httpSession = current.getSession();
            if (httpSession == null) {
                httpSession = new HttpSessionImpl(session, servletContext, newSession);
                current.setSession(httpSession);
            } else {
                if(httpSession.session != session) {
                    //in some rare cases it may be that there are two different service contexts involved in the one request
                    //in this case we just return a new session rather than using the thread local version
                    httpSession = new HttpSessionImpl(session, servletContext, newSession);
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
        return session.getAttribute(name);
    }

    @Override
    public Object getValue(final String name) {
        return getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return new IteratorEnumeration<String>(session.getAttributeNames().iterator());
    }

    @Override
    public String[] getValueNames() {
        Set<String> names = session.getAttributeNames();
        String[] ret = new String[names.size()];
        int i = 0;
        for (String name : names) {
            ret[i++] = name;
        }
        return ret;
    }

    @Override
    public void setAttribute(final String name, final Object value) {
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
        session.removeAttribute(name);
    }

    @Override
    public void removeValue(final String name) {
        removeAttribute(name);
    }

    @Override
    public void invalidate() {
        invalid = true;
        ServletRequestContext current = ServletRequestContext.current();
        if (current == null) {
            session.invalidate(null);
        } else {
            session.invalidate(current.getOriginalRequest().getExchange());
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
        return session;
    }

    public boolean isInvalid() {
        return invalid;
    }
}
