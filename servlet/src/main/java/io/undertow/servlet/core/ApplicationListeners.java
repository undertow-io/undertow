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

import io.undertow.servlet.UndertowServletLogger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
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
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;
import java.util.ArrayList;
import java.util.List;

import static io.undertow.servlet.core.ApplicationListeners.ListenerState.DECLARED_LISTENER;
import static io.undertow.servlet.core.ApplicationListeners.ListenerState.PROGRAMATIC_LISTENER;

/**
 * Class that is responsible for invoking application listeners.
 * <p>
 * This class does not perform any context setup, the context must be setup
 * before invoking this class.
 * <p>
 * Note that arrays are used instead of lists for performance reasons.
 *
 * @author Stuart Douglas
 */
public class ApplicationListeners implements Lifecycle {


    private static final ManagedListener[] EMPTY = {};

    private static final Class[] LISTENER_CLASSES = {ServletContextListener.class,
            ServletContextAttributeListener.class,
            ServletRequestListener.class,
            ServletRequestAttributeListener.class,
            javax.servlet.http.HttpSessionListener.class,
            javax.servlet.http.HttpSessionAttributeListener.class,
            HttpSessionIdListener.class};

    private static final ThreadLocal<ListenerState> IN_PROGRAMATIC_SC_LISTENER_INVOCATION = new ThreadLocal<ListenerState>() {
        @Override
        protected ListenerState initialValue() {
            return ListenerState.NO_LISTENER;
        }
    };

    private ServletContext servletContext;
    private final List<ManagedListener> allListeners = new ArrayList<>();
    private ManagedListener[] servletContextListeners;
    private ManagedListener[] servletContextAttributeListeners;
    private ManagedListener[] servletRequestListeners;
    private ManagedListener[] servletRequestAttributeListeners;
    private ManagedListener[] httpSessionListeners;
    private ManagedListener[] httpSessionAttributeListeners;
    private ManagedListener[] httpSessionIdListeners;
    private volatile boolean started = false;

    public ApplicationListeners(final List<ManagedListener> allListeners, final ServletContext servletContext) {
        this.servletContext = servletContext;
        servletContextListeners = EMPTY;
        servletContextAttributeListeners = EMPTY;
        servletRequestListeners = EMPTY;
        servletRequestAttributeListeners = EMPTY;
        httpSessionListeners = EMPTY;
        httpSessionAttributeListeners = EMPTY;
        httpSessionIdListeners = EMPTY;
        for (final ManagedListener listener : allListeners) {
            addListener(listener);
        }
    }

    public void addListener(final ManagedListener listener) {
        if (ServletContextListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
            ManagedListener[] old = servletContextListeners;
            servletContextListeners = new ManagedListener[old.length + 1];
            System.arraycopy(old, 0, servletContextListeners, 0, old.length);
            servletContextListeners[old.length] = listener;
        }
        if (ServletContextAttributeListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {

            ManagedListener[] old = servletContextAttributeListeners;
            servletContextAttributeListeners = new ManagedListener[old.length + 1];
            System.arraycopy(old, 0, servletContextAttributeListeners, 0, old.length);
            servletContextAttributeListeners[old.length] = listener;
        }
        if (ServletRequestListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
            ManagedListener[] old = servletRequestListeners;
            servletRequestListeners = new ManagedListener[old.length + 1];
            System.arraycopy(old, 0, servletRequestListeners, 0, old.length);
            servletRequestListeners[old.length] = listener;
        }
        if (ServletRequestAttributeListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
            ManagedListener[] old = servletRequestAttributeListeners;
            servletRequestAttributeListeners = new ManagedListener[old.length + 1];
            System.arraycopy(old, 0, servletRequestAttributeListeners, 0, old.length);
            servletRequestAttributeListeners[old.length] = listener;
        }
        if (HttpSessionListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
            ManagedListener[] old = httpSessionListeners;
            httpSessionListeners = new ManagedListener[old.length + 1];
            System.arraycopy(old, 0, httpSessionListeners, 0, old.length);
            httpSessionListeners[old.length] = listener;
        }
        if (HttpSessionAttributeListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
            ManagedListener[] old = httpSessionAttributeListeners;
            httpSessionAttributeListeners = new ManagedListener[old.length + 1];
            System.arraycopy(old, 0, httpSessionAttributeListeners, 0, old.length);
            httpSessionAttributeListeners[old.length] = listener;
        }
        if (HttpSessionIdListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
            ManagedListener[] old = httpSessionIdListeners;
            httpSessionIdListeners = new ManagedListener[old.length + 1];
            System.arraycopy(old, 0, httpSessionIdListeners, 0, old.length);
            httpSessionIdListeners[old.length] = listener;
        }
        this.allListeners.add(listener);
        if(started) {
            try {
                listener.start();
            } catch (ServletException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void start() throws ServletException {
        started = true;
        for (ManagedListener listener : allListeners) {
            listener.start();
        }
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
        if(!started) {
            return;
        }
        //new listeners can be added here, so we don't use an iterator
        final ServletContextEvent event = new ServletContextEvent(servletContext);
        for (int i = 0; i < servletContextListeners.length; ++i) {
            ManagedListener listener = servletContextListeners[i];
            IN_PROGRAMATIC_SC_LISTENER_INVOCATION.set(listener.isProgramatic() ? PROGRAMATIC_LISTENER : DECLARED_LISTENER);
            try {
                this.<ServletContextListener>get(listener).contextInitialized(event);
            } finally {
                IN_PROGRAMATIC_SC_LISTENER_INVOCATION.remove();
            }
        }
    }

    public void contextDestroyed() {
        if(!started) {
            return;
        }
        final ServletContextEvent event = new ServletContextEvent(servletContext);
        for (int i = servletContextListeners.length - 1; i >= 0; --i) {
            ManagedListener listener = servletContextListeners[i];
            try {
                this.<ServletContextListener>get(listener).contextDestroyed(event);
            } catch (Exception e) {
                UndertowServletLogger.REQUEST_LOGGER.errorInvokingListener("contextDestroyed", listener.getListenerInfo().getListenerClass(), e);
            }
        }
    }

    public void servletContextAttributeAdded(final String name, final Object value) {
        if(!started) {
            return;
        }
        final ServletContextAttributeEvent sre = new ServletContextAttributeEvent(servletContext, name, value);
        for (int i = 0; i < servletContextAttributeListeners.length; ++i) {
            this.<ServletContextAttributeListener>get(servletContextAttributeListeners[i]).attributeAdded(sre);
        }
    }

    public void servletContextAttributeRemoved(final String name, final Object value) {
        if(!started) {
            return;
        }
        final ServletContextAttributeEvent sre = new ServletContextAttributeEvent(servletContext, name, value);
        for (int i = 0; i < servletContextAttributeListeners.length; ++i) {
            this.<ServletContextAttributeListener>get(servletContextAttributeListeners[i]).attributeRemoved(sre);
        }
    }

    public void servletContextAttributeReplaced(final String name, final Object value) {
        if(!started) {
            return;
        }
        final ServletContextAttributeEvent sre = new ServletContextAttributeEvent(servletContext, name, value);
        for (int i = 0; i < servletContextAttributeListeners.length; ++i) {
            this.<ServletContextAttributeListener>get(servletContextAttributeListeners[i]).attributeReplaced(sre);
        }
    }

    public void requestInitialized(final ServletRequest request) {
        if(!started) {
            return;
        }
        if(servletRequestListeners.length > 0) {
            final ServletRequestEvent sre = new ServletRequestEvent(servletContext, request);
            for (int i = 0; i < servletRequestListeners.length; ++i) {
                this.<ServletRequestListener>get(servletRequestListeners[i]).requestInitialized(sre);
            }
        }
    }

    public void requestDestroyed(final ServletRequest request) {
        if(!started) {
            return;
        }
        if(servletRequestListeners.length > 0) {
            final ServletRequestEvent sre = new ServletRequestEvent(servletContext, request);
            for (int i = servletRequestListeners.length - 1; i >= 0; --i) {
                ManagedListener listener = servletRequestListeners[i];
                try {
                    this.<ServletRequestListener>get(listener).requestDestroyed(sre);
                } catch (Exception e) {
                    UndertowServletLogger.REQUEST_LOGGER.errorInvokingListener("requestDestroyed", listener.getListenerInfo().getListenerClass(), e);
                }
            }
        }
    }

    public void servletRequestAttributeAdded(final HttpServletRequest request, final String name, final Object value) {
        if(!started) {
            return;
        }
        final ServletRequestAttributeEvent sre = new ServletRequestAttributeEvent(servletContext, request, name, value);
        for (int i = 0; i < servletRequestAttributeListeners.length; ++i) {
            this.<ServletRequestAttributeListener>get(servletRequestAttributeListeners[i]).attributeAdded(sre);
        }
    }

    public void servletRequestAttributeRemoved(final HttpServletRequest request, final String name, final Object value) {
        if(!started) {
            return;
        }
        final ServletRequestAttributeEvent sre = new ServletRequestAttributeEvent(servletContext, request, name, value);
        for (int i = 0; i < servletRequestAttributeListeners.length; ++i) {
            this.<ServletRequestAttributeListener>get(servletRequestAttributeListeners[i]).attributeRemoved(sre);
        }
    }

    public void servletRequestAttributeReplaced(final HttpServletRequest request, final String name, final Object value) {
        if(!started) {
            return;
        }
        final ServletRequestAttributeEvent sre = new ServletRequestAttributeEvent(servletContext, request, name, value);
        for (int i = 0; i < servletRequestAttributeListeners.length; ++i) {
            this.<ServletRequestAttributeListener>get(servletRequestAttributeListeners[i]).attributeReplaced(sre);
        }
    }

    public void sessionCreated(final HttpSession session) {
        if(!started) {
            return;
        }
        final HttpSessionEvent sre = new HttpSessionEvent(session);
        for (int i = 0; i < httpSessionListeners.length; ++i) {
            this.<HttpSessionListener>get(httpSessionListeners[i]).sessionCreated(sre);
        }
    }

    public void sessionDestroyed(final HttpSession session) {
        if(!started) {
            return;
        }
        final HttpSessionEvent sre = new HttpSessionEvent(session);
        for (int i = httpSessionListeners.length - 1; i >= 0; --i) {
            ManagedListener listener = httpSessionListeners[i];
            this.<HttpSessionListener>get(listener).sessionDestroyed(sre);
        }
    }

    public void httpSessionAttributeAdded(final HttpSession session, final String name, final Object value) {
        if(!started) {
            return;
        }
        final HttpSessionBindingEvent sre = new HttpSessionBindingEvent(session, name, value);
        for (int i = 0; i < httpSessionAttributeListeners.length; ++i) {
            this.<HttpSessionAttributeListener>get(httpSessionAttributeListeners[i]).attributeAdded(sre);
        }
    }

    public void httpSessionAttributeRemoved(final HttpSession session, final String name, final Object value) {
        if(!started) {
            return;
        }
        final HttpSessionBindingEvent sre = new HttpSessionBindingEvent(session, name, value);
        for (int i = 0; i < httpSessionAttributeListeners.length; ++i) {
            this.<HttpSessionAttributeListener>get(httpSessionAttributeListeners[i]).attributeRemoved(sre);
        }
    }

    public void httpSessionAttributeReplaced(final HttpSession session, final String name, final Object value) {
        if(!started) {
            return;
        }
        final HttpSessionBindingEvent sre = new HttpSessionBindingEvent(session, name, value);
        for (int i = 0; i < httpSessionAttributeListeners.length; ++i) {
            this.<HttpSessionAttributeListener>get(httpSessionAttributeListeners[i]).attributeReplaced(sre);
        }
    }

    public void httpSessionIdChanged(final HttpSession session, final String oldSessionId) {
        if(!started) {
            return;
        }
        final HttpSessionEvent sre = new HttpSessionEvent(session);
        for (int i = 0; i < httpSessionIdListeners.length; ++i) {
            this.<HttpSessionIdListener>get(httpSessionIdListeners[i]).sessionIdChanged(sre, oldSessionId);
        }
    }

    private <T> T get(final ManagedListener listener) {
        return (T) listener.instance();
    }

    /**
     * returns true if this is in in a
     */
    public static ListenerState listenerState() {
        return IN_PROGRAMATIC_SC_LISTENER_INVOCATION.get();
    }

    /**
     * @param clazz The potential listener class
     * @return true if the provided class is a valid listener class
     */
    public static boolean isListenerClass(final Class<?> clazz) {
        for (Class c : LISTENER_CLASSES) {
            if (c.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }

    public enum ListenerState {
        NO_LISTENER,
        DECLARED_LISTENER,
        PROGRAMATIC_LISTENER,
    }

}
