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

package io.undertow.servlet.test.listener.servletcontext;

import jakarta.servlet.ServletContextAttributeEvent;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequestAttributeEvent;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;

/**
 * @author Stuart Douglas
 */
public class ServletContextTestListener implements ServletContextAttributeListener, ServletContextListener, ServletRequestListener, ServletRequestAttributeListener {

    public static ServletContextAttributeEvent servletContextAttributeEvent;
    public static ServletContextEvent servletContextInitializedEvent;
    public static ServletContextEvent servletContextDestroyedEvent;
    public static ServletRequestAttributeEvent servletRequestAttributeEvent;
    public static ServletRequestEvent servletRequestInitializedEvent;
    public static ServletRequestEvent servletRequestDestroyedEvent;

    @Override
    public void attributeAdded(final ServletContextAttributeEvent event) {
        servletContextAttributeEvent = event;
    }

    @Override
    public void attributeRemoved(final ServletContextAttributeEvent event) {
        servletContextAttributeEvent = event;
    }

    @Override
    public void attributeReplaced(final ServletContextAttributeEvent event) {
        servletContextAttributeEvent = event;
    }

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        servletContextInitializedEvent = sce;
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        servletContextDestroyedEvent = sce;
    }

    @Override
    public void attributeAdded(final ServletRequestAttributeEvent srae) {
        servletRequestAttributeEvent = srae;
    }

    @Override
    public void attributeRemoved(final ServletRequestAttributeEvent srae) {
        servletRequestAttributeEvent = srae;
    }

    @Override
    public void attributeReplaced(final ServletRequestAttributeEvent srae) {
        servletRequestAttributeEvent = srae;
    }

    @Override
    public void requestDestroyed(final ServletRequestEvent sre) {
        servletRequestDestroyedEvent = sre;
    }

    @Override
    public void requestInitialized(final ServletRequestEvent sre) {
        servletRequestInitializedEvent = sre;
    }
}
