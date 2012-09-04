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

import java.util.EventListener;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ListenerInfo;

/**
 * @author Stuart Douglas
 */
public class ManagedListener implements Lifecycle,
        ServletContextListener, ServletRequestListener {

    private final ListenerInfo listenerInfo;
    private final ServletContext servletContext;

    private volatile boolean started = false;
    private volatile EventListener listener;
    private volatile InstanceHandle<? extends EventListener> handle;

    public ManagedListener(final ListenerInfo listenerInfo, final ServletContext servletContext) {
        this.listenerInfo = listenerInfo;
        this.servletContext = servletContext;
    }

    public synchronized void start() throws ServletException {
        if (!started) {
            try {
                handle = listenerInfo.getInstanceFactory().createInstance();
            } catch (Exception e) {
                throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(listenerInfo.getListenerClass().getName(), e);
            }
            listener = handle.getInstance();
            started = true;
        }
    }

    public synchronized void stop() {
        started = false;
        if (handle != null) {
            handle.release();
        }
    }

    public ListenerInfo getListenerInfo() {
        return listenerInfo;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    private EventListener instance() {
        if (!started) {
            try {
                start();
            } catch (ServletException e) {
                throw new RuntimeException(e);
            }
        }
        return handle.getInstance();
    }

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        ((ServletContextListener) instance()).contextInitialized(sce);
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        ((ServletContextListener) instance()).contextDestroyed(sce);
    }

    @Override
    public void requestDestroyed(final ServletRequestEvent sre) {
        ((ServletRequestListener)instance()).requestDestroyed(sre);
    }

    @Override
    public void requestInitialized(final ServletRequestEvent sre) {
        ((ServletRequestListener)instance()).requestInitialized(sre);
    }
}
