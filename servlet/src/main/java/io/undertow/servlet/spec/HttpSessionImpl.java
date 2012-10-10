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

import java.io.IOException;
import java.util.Enumeration;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.util.IteratorEnumeration;

/**
 * @author Stuart Douglas
 */
public class HttpSessionImpl implements HttpSession {

    private final Session session;
    private final ServletContext servletContext;
    private final ApplicationListeners applicationListeners;
    private final HttpServerExchange exchange;
    private final boolean newSession;
    private volatile boolean invalid;

    public HttpSessionImpl(final Session session, final ServletContext servletContext, final ApplicationListeners applicationListeners, final HttpServerExchange exchange, final boolean newSession) {
        this.session = session;
        this.servletContext = servletContext;
        this.applicationListeners = applicationListeners;
        this.exchange = exchange;
        this.newSession = newSession;
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
        try {
            return session.getAttribute(name).get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object getValue(final String name) {
        return getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        try {
            return new IteratorEnumeration<String>(session.getAttributeNames().get().iterator());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String[] getValueNames() {
        try {
            Set<String> names = session.getAttributeNames().get();
            String[] ret = new String[names.size()];
            int i = 0;
            for(String name : names) {
                ret[i++] = name;
            }
            return ret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setAttribute(final String name, final Object value) {
        try {
            Object old = session.setAttribute(name, value).get();
            if(value == null && old != null) {
                applicationListeners.httpSessionAttributeRemoved(this, name, old);
            } else if(old == null) {
                applicationListeners.httpSessionAttributeAdded(this, name, value);
            } else {
                applicationListeners.httpSessionAttributeReplaced(this, name, old);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void putValue(final String name, final Object value) {
        setAttribute(name, value);
    }

    @Override
    public void removeAttribute(final String name) {
        try {
            Object old = session.removeAttribute(name).get();
            applicationListeners.httpSessionAttributeRemoved(this, name, old);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeValue(final String name) {
        removeAttribute(name);
    }

    @Override
    public void invalidate() {
        invalid = true;
        applicationListeners.sessionDestroyed(this);
        session.invalidate(exchange);
    }

    @Override
    public boolean isNew() {
        if(invalid) {
            throw UndertowServletMessages.MESSAGES.sessionIsInvalid();
        }
        return newSession;
    }
}
