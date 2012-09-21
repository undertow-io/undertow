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

package io.undertow.servlet.core;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import io.undertow.servlet.UndertowServletLogger;

/**
 * Class that is responsible for invoking application listeners.
 * <p/>
 * This class does not perform any context setup, the context must be setup
 * before invoking this class.
 *
 * @author Stuart Douglas
 */
public class ApplicationListeners implements Lifecycle {

    private final ServletContext servletContext;
    private final List<ManagedListener> allListeners;
    private final List<ManagedListener> servletContextListeners;
    private final List<ManagedListener> servletContextAttributeListeners;
    private final List<ManagedListener> servletRequestListeners;
    private final List<ManagedListener> servletRequestAttributeListeners;
    private final List<ManagedListener> httpSessionListeners;
    private final List<ManagedListener> httpSessionAttributeListeners;
    private volatile boolean started = false;

    public ApplicationListeners(final List<ManagedListener> allListeners, final ServletContext servletContext) {
        this.servletContext = servletContext;
        List<ManagedListener> servletContextListeners = new ArrayList<ManagedListener>();
        List<ManagedListener> servletContextAttributeListeners = new ArrayList<ManagedListener>();
        List<ManagedListener> servletRequestListeners = new ArrayList<ManagedListener>();
        List<ManagedListener> servletRequestAttributeListeners = new ArrayList<ManagedListener>();
        List<ManagedListener> httpSessionListeners = new ArrayList<ManagedListener>();
        List<ManagedListener> httpSessionAttributeListeners = new ArrayList<ManagedListener>();
        for (final ManagedListener listener : allListeners) {
            if (ServletContextListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
                servletContextListeners.add(listener);
            }
            if (ServletContextAttributeListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
                servletContextAttributeListeners.add(listener);
            }
            if (ServletRequestListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
                servletRequestListeners.add(listener);
            }
            if (ServletRequestAttributeListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
                servletRequestAttributeListeners.add(listener);
            }
            if (HttpSessionListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
                httpSessionListeners.add(listener);
            }
            if (HttpSessionAttributeListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
                httpSessionAttributeListeners.add(listener);
            }
        }
        this.servletContextListeners = servletContextListeners;
        this.servletContextAttributeListeners = servletContextAttributeListeners;
        this.servletRequestListeners = servletRequestListeners;
        this.servletRequestAttributeListeners = servletRequestAttributeListeners;
        this.httpSessionListeners = httpSessionListeners;
        this.httpSessionAttributeListeners = httpSessionAttributeListeners;
        this.allListeners = allListeners;
    }

    public void start() {
        started = true;
    }

    public void stop() {
        if (started) {
            started = false;
            for (final ManagedListener listener : allListeners) {
                listener.stop();
            }
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    public void contextInitialized() {
        final ServletContextEvent event = new ServletContextEvent(servletContext);
        for (ManagedListener listener : servletContextListeners) {
            this.<ServletContextListener>get(listener).contextInitialized(event);
        }
    }

    public void contextDestroyed() {
        final ServletContextEvent event = new ServletContextEvent(servletContext);
        for (ManagedListener listener : servletContextListeners) {
            try {
                this.<ServletContextListener>get(listener).contextDestroyed(event);
            } catch (Exception e) {
                UndertowServletLogger.REQUEST_LOGGER.errorInvokingListener("contextDestroyed", listener.getListenerInfo().getListenerClass(), e);
            }
        }
    }

    public void servletContextAttributeAdded(final String name, final Object value) {
        final ServletContextAttributeEvent sre = new ServletContextAttributeEvent(servletContext, name, value);
        for (final ManagedListener listener : servletContextAttributeListeners) {
            this.<ServletContextAttributeListener>get(listener).attributeAdded(sre);
        }
    }

    public void servletContextAttributeRemoved(final String name, final Object value) {
        final ServletContextAttributeEvent sre = new ServletContextAttributeEvent(servletContext, name, value);
        for (final ManagedListener listener : servletContextAttributeListeners) {
            this.<ServletContextAttributeListener>get(listener).attributeRemoved(sre);
        }
    }

    public void servletContextAttributeReplaced(final String name, final Object value) {
        final ServletContextAttributeEvent sre = new ServletContextAttributeEvent(servletContext, name, value);
        for (final ManagedListener listener : servletContextAttributeListeners) {
            this.<ServletContextAttributeListener>get(listener).attributeReplaced(sre);
        }
    }

    public void requestInitialized(final ServletRequest request) {
        final ServletRequestEvent sre = new ServletRequestEvent(servletContext, request);
        for (final ManagedListener listener : servletRequestListeners) {
            this.<ServletRequestListener>get(listener).requestInitialized(sre);
        }
    }

    public void requestDestroyed(final ServletRequest request) {
        final ServletRequestEvent sre = new ServletRequestEvent(servletContext, request);
        for (final ManagedListener listener : servletRequestListeners) {
            try {
                this.<ServletRequestListener>get(listener).requestDestroyed(sre);
            } catch (Exception e) {
                UndertowServletLogger.REQUEST_LOGGER.errorInvokingListener("requestDestroyed", listener.getListenerInfo().getListenerClass(), e);
            }
        }
    }

    public void servletRequestAttributeAdded(final HttpServletRequest request, final String name, final Object value) {
        final ServletRequestAttributeEvent sre = new ServletRequestAttributeEvent(servletContext, request, name, value);
        for (final ManagedListener listener : servletRequestAttributeListeners) {
            this.<ServletRequestAttributeListener>get(listener).attributeAdded(sre);
        }
    }

    public void servletRequestAttributeRemoved(final HttpServletRequest request, final String name, final Object value) {
        final ServletRequestAttributeEvent sre = new ServletRequestAttributeEvent(servletContext, request, name, value);
        for (final ManagedListener listener : servletRequestAttributeListeners) {
            this.<ServletRequestAttributeListener>get(listener).attributeRemoved(sre);
        }
    }

    public void servletRequestAttributeReplaced(final HttpServletRequest request, final String name, final Object value) {
        final ServletRequestAttributeEvent sre = new ServletRequestAttributeEvent(servletContext, request, name, value);
        for (final ManagedListener listener : servletRequestAttributeListeners) {
            this.<ServletRequestAttributeListener>get(listener).attributeReplaced(sre);
        }
    }

    public void sessionCreated(final HttpSession session) {
        final HttpSessionEvent sre = new HttpSessionEvent(session);
        for (final ManagedListener listener : httpSessionListeners) {
            this.<HttpSessionListener>get(listener).sessionCreated(sre);
        }
    }

    public void sessionDestroyed(final HttpSession session) {
        final HttpSessionEvent sre = new HttpSessionEvent(session);
        for (final ManagedListener listener : httpSessionListeners) {
            this.<HttpSessionListener>get(listener).sessionDestroyed(sre);
        }
    }

    public void httpSessionAttributeAdded(final HttpSession session, final String name, final Object value) {
        final HttpSessionBindingEvent sre = new HttpSessionBindingEvent(session, name, value);
        for (final ManagedListener listener : httpSessionAttributeListeners) {
            this.<HttpSessionAttributeListener>get(listener).attributeAdded(sre);
        }
    }

    public void httpSessionAttributeRemoved(final HttpSession session, final String name, final Object value) {
        final HttpSessionBindingEvent sre = new HttpSessionBindingEvent(session, name, value);
        for (final ManagedListener listener : httpSessionAttributeListeners) {
            this.<HttpSessionAttributeListener>get(listener).attributeRemoved(sre);
        }
    }

    public void httpSessionAttributeReplaced(final HttpSession session, final String name, final Object value) {
        final HttpSessionBindingEvent sre = new HttpSessionBindingEvent(session, name, value);
        for (final ManagedListener listener : httpSessionAttributeListeners) {
            this.<HttpSessionAttributeListener>get(listener).attributeReplaced(sre);
        }
    }


    private <T> T get(final ManagedListener listener) {
        return (T) listener.instance();
    }

}
