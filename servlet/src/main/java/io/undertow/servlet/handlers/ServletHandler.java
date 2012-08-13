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

package io.undertow.servlet.handlers;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.SingleThreadModel;
import javax.servlet.UnavailableException;

import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import io.undertow.servlet.spec.ServletConfigImpl;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * The handler that is responsible for invoking the servlet
 * <p/>
 * TODO: do we want to move lifecycle considerations out of this handler?
 *
 * @author Stuart Douglas
 */
public class ServletHandler implements BlockingHttpHandler {

    private final ServletInfo servletInfo;
    private final Class<?> servletClass;

    private volatile boolean started = false;
    private volatile boolean stopped = false;
    private final InstanceStrategy instanceStrategy;

    private static final AtomicLongFieldUpdater<ServletHandler> unavailableUntilUpdater = AtomicLongFieldUpdater.newUpdater(ServletHandler.class, "unavailableUntil");

    @SuppressWarnings("unused")
    private volatile long unavailableUntil = 0;

    public ServletHandler(final ServletInfo servletInfo, final Class<?> servletClass, final ServletContextImpl servletContext) {
        this.servletInfo = servletInfo;
        this.servletClass = servletClass;
        if (SingleThreadModel.class.isAssignableFrom(servletClass)) {
            instanceStrategy = new SingleThreadModelPoolStrategy(servletInfo.getInstanceFactory(), servletClass, servletInfo, servletContext);
        } else {
            instanceStrategy = new DefaultInstanceStrategy(servletInfo.getInstanceFactory(), servletClass, servletInfo, servletContext);
        }
    }

    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws IOException, ServletException {
        if (stopped) {
            UndertowServletLogger.REQUEST_LOGGER.debugf("Returning 503 for servlet %s due to permanent unavailability", servletClass);
            exchange.getExchange().setResponseCode(503);
            return;
        }

        long until = unavailableUntilUpdater.get(this);
        if (until != 0) {
            UndertowServletLogger.REQUEST_LOGGER.debugf("Returning 503 for servlet %s due to temporary unavailability", servletClass);
            if (System.currentTimeMillis() < until) {
                exchange.getExchange().setResponseCode(503);
                return;
            } else {
                unavailableUntilUpdater.compareAndSet(this, until, 0);
            }
        }
        if (!started) {
            start();
        }
        HttpServletRequestImpl request = (HttpServletRequestImpl) exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        HttpServletResponseImpl response = (HttpServletResponseImpl) exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        final InstanceHandle servlet = instanceStrategy.getServlet();
        try {
            ((Servlet) servlet.getInstance()).service(request, response);
        } catch (UnavailableException e) {
            if (e.isPermanent()) {
                UndertowServletLogger.REQUEST_LOGGER.stoppingServletDueToPermanentUnavailability(servletInfo.getName(), e);
                stop();
            } else {
                unavailableUntilUpdater.set(this, System.currentTimeMillis() + e.getUnavailableSeconds() * 1000);
                UndertowServletLogger.REQUEST_LOGGER.stoppingServletUntilDueToTemporaryUnavailability(servletInfo.getName(), new Date(until), e);
            }
            throw e;
        } finally {
            servlet.release();
        }
    }

    public synchronized void start() throws ServletException {
        if (!started) {
            instanceStrategy.start();
            started = true;
        }
    }

    public synchronized void stop() {
        if (!stopped && started) {
            instanceStrategy.stop();
        }
        stopped = true;
    }

    /**
     * interface used to abstract the difference between single thread model servlets and normal servlets
     */
    interface InstanceStrategy {
        void start() throws ServletException;

        void stop();

        InstanceHandle getServlet() throws ServletException;
    }


    /**
     * The default servlet pooling strategy that just uses a single instance for all requests
     */
    private static class DefaultInstanceStrategy implements InstanceStrategy {

        private final InstanceFactory factory;
        private final Class<?> servletClass;
        private final ServletInfo servletInfo;
        private final ServletContextImpl servletContext;
        private volatile InstanceHandle handle;
        private volatile Servlet instance;

        private DefaultInstanceStrategy(final InstanceFactory factory, final Class<?> servletClass, final ServletInfo servletInfo, final ServletContextImpl servletContext) {
            this.factory = factory;
            this.servletClass = servletClass;
            this.servletInfo = servletInfo;
            this.servletContext = servletContext;
        }

        public void start() throws ServletException {
            if (factory != null) {
                handle = factory.createInstance();
                instance = (Servlet) handle.getInstance();
            } else {
                try {
                    instance = (Servlet) servletClass.newInstance();
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            instance.init(new ServletConfigImpl(servletInfo, servletContext));
        }

        public void stop() {
            if (handle != null) {
                instance.destroy();
                handle.release();
            }
        }

        public InstanceHandle getServlet() {
            return new InstanceHandle() {
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


        private final InstanceFactory factory;
        private final Class<?> servletClass;
        private final ServletInfo servletInfo;
        private final ServletContextImpl servletContext;
        private volatile InstanceHandle handle;
        private volatile Servlet instance;

        private SingleThreadModelPoolStrategy(final InstanceFactory factory, final Class<?> servletClass, final ServletInfo servletInfo, final ServletContextImpl servletContext) {
            this.factory = factory;
            this.servletClass = servletClass;
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
        public InstanceHandle getServlet() throws ServletException {
            final InstanceHandle instanceHandle;
            final Servlet instance;
            if (factory != null) {
                //TODO: pooling
                instanceHandle = factory.createInstance();
                instance = (Servlet) instanceHandle.getInstance();
            } else {
                try {
                    instance = (Servlet) servletClass.newInstance();
                    instanceHandle = null;
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

            }
            instance.init(new ServletConfigImpl(servletInfo, servletContext));
            return new InstanceHandle() {
                @Override
                public Object getInstance() {
                    return instance;
                }

                @Override
                public void release() {
                    instance.destroy();
                    if (instanceHandle != null) {
                        instanceHandle.release();
                    }
                }
            };

        }
    }
}
