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

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.SingleThreadModel;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.spec.ServletConfigImpl;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * Manager for a servlets lifecycle.
 *
 * @author Stuart Douglas
 */
public class ManagedServlet implements Lifecycle {

    private final ServletInfo servletInfo;

    private volatile boolean started = false;
    private final InstanceStrategy instanceStrategy;

    public ManagedServlet(final ServletInfo servletInfo, final ServletContextImpl servletContext) {
        this.servletInfo = servletInfo;
        if (SingleThreadModel.class.isAssignableFrom(servletInfo.getServletClass())) {
            instanceStrategy = new SingleThreadModelPoolStrategy(servletInfo.getInstanceFactory(), servletInfo, servletContext);
        } else {
            instanceStrategy = new DefaultInstanceStrategy(servletInfo.getInstanceFactory(), servletInfo, servletContext);
        }
    }


    public synchronized void start() throws ServletException {
        if (!started && servletInfo.isLoadOnStartup()) {
            instanceStrategy.start();
            started = true;
        }
    }

    public synchronized void stop() {
        if (started) {
            instanceStrategy.stop();
        }
        started = false;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    public InstanceHandle<? extends Servlet> getServlet() throws ServletException {
        if (!started) {
            synchronized (this) {
                if (!started) {
                    instanceStrategy.start();
                    started = true;
                }
            }
        }
        return instanceStrategy.getServlet();
    }

    public ServletInfo getServletInfo() {
        return servletInfo;
    }

    /**
     * interface used to abstract the difference between single thread model servlets and normal servlets
     */
    interface InstanceStrategy {
        void start() throws ServletException;

        void stop();

        InstanceHandle<? extends Servlet> getServlet() throws ServletException;
    }


    /**
     * The default servlet pooling strategy that just uses a single instance for all requests
     */
    private static class DefaultInstanceStrategy implements InstanceStrategy {

        private final InstanceFactory<? extends Servlet> factory;
        private final ServletInfo servletInfo;
        private final ServletContextImpl servletContext;
        private volatile InstanceHandle<? extends Servlet> handle;
        private volatile Servlet instance;

        private DefaultInstanceStrategy(final InstanceFactory<? extends Servlet> factory, final ServletInfo servletInfo, final ServletContextImpl servletContext) {
            this.factory = factory;
            this.servletInfo = servletInfo;
            this.servletContext = servletContext;
        }

        public void start() throws ServletException {
            try {
                handle = factory.createInstance();
            } catch (Exception e) {
                throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(servletInfo.getName(), e);
            }
            instance = handle.getInstance();
            instance.init(new ServletConfigImpl(servletInfo, servletContext));
        }

        public void stop() {
            if (handle != null) {
                instance.destroy();
                handle.release();
            }
        }

        public InstanceHandle<? extends Servlet> getServlet() {
            return new InstanceHandle<Servlet>() {
                @Override
                public Servlet getInstance() {
                    return instance;
                }

                @Override
                public void release() {

                }
            };
        }
    }

    /**
     * pooling strategy for single thread model servlet
     */
    private static class SingleThreadModelPoolStrategy implements InstanceStrategy {


        private final InstanceFactory<? extends Servlet> factory;
        private final ServletInfo servletInfo;
        private final ServletContextImpl servletContext;

        private SingleThreadModelPoolStrategy(final InstanceFactory<? extends Servlet> factory, final ServletInfo servletInfo, final ServletContextImpl servletContext) {
            this.factory = factory;
            this.servletInfo = servletInfo;
            this.servletContext = servletContext;
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public InstanceHandle<? extends Servlet> getServlet() throws ServletException {
            final InstanceHandle<? extends Servlet> instanceHandle;
            final Servlet instance;
            //TODO: pooling
            try {
                instanceHandle = factory.createInstance();
            } catch (Exception e) {
                throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(servletInfo.getName(), e);
            }
            instance = instanceHandle.getInstance();

            instance.init(new ServletConfigImpl(servletInfo, servletContext));
            return new InstanceHandle<Servlet>() {
                @Override
                public Servlet getInstance() {
                    return instance;
                }

                @Override
                public void release() {
                    instance.destroy();
                    instanceHandle.release();
                }
            };

        }
    }
}
