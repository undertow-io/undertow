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
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;

import io.undertow.servlet.UndertowServletLogger;

/**
 * Class that is responsible for invoking application listeners.
 * <p/>
 * This class does not perform any context setup, the context must be setup
 * before invoking this class.
 *
 * @author Stuart Douglas
 */
public class ApplicationListeners {

    private final ServletContext servletContext;
    private final List<ManagedListener> servletContextListeners;
    private final List<ManagedListener> servletRequestListeners;

    public ApplicationListeners(final List<ManagedListener> allListeners, final ServletContext servletContext) {
        this.servletContext = servletContext;
        List<ManagedListener> servletContextListeners = new ArrayList<ManagedListener>();
        List<ManagedListener> servletRequestListeners = new ArrayList<ManagedListener>();
        for (final ManagedListener listener : allListeners) {
            if (ServletContextListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
                servletContextListeners.add(listener);
            }
            if (ServletRequestListener.class.isAssignableFrom(listener.getListenerInfo().getListenerClass())) {
                servletRequestListeners.add(listener);
            }
        }
        this.servletContextListeners = servletContextListeners;
        this.servletRequestListeners = servletRequestListeners;
    }


    public void contextInitialized() {
        final ServletContextEvent event = new ServletContextEvent(servletContext);
        for (ManagedListener listener : servletContextListeners) {
            listener.contextInitialized(event);
        }
    }

    public void contextDestroyed() {
        final ServletContextEvent event = new ServletContextEvent(servletContext);
        for (ManagedListener listener : servletContextListeners) {
            try {
                listener.contextDestroyed(event);
            } catch (Exception e) {
                UndertowServletLogger.REQUEST_LOGGER.errorInvokingListener("contextDestroyed", listener.getListenerInfo().getListenerClass(), e);
            }
        }
    }

    public void requestInitialized(final ServletRequest request) {
        final ServletRequestEvent sre = new ServletRequestEvent(servletContext, request);
        for (final ManagedListener listener : servletRequestListeners) {
            listener.requestInitialized(sre);
        }
    }

    public void requestDestroyed(final ServletRequest request) {
        final ServletRequestEvent sre = new ServletRequestEvent(servletContext, request);
        for (final ManagedListener listener : servletRequestListeners) {
            try {
                listener.requestDestroyed(sre);
            } catch (Exception e) {
                UndertowServletLogger.REQUEST_LOGGER.errorInvokingListener("requestDestroyed", listener.getListenerInfo().getListenerClass(), e);
            }
        }
    }

}
