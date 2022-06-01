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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;

import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import io.undertow.server.handlers.resource.ResourceChangeListener;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.LifecycleInterceptor;
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
    private final ServletContextImpl servletContext;

    private volatile boolean started = false;
    private final InstanceStrategy instanceStrategy;
    private volatile boolean permanentlyUnavailable = false;

    private long maxMultipartRequestSize;
    private FormParserFactory formParserFactory;
    private MultipartConfigElement multipartConfig;

    private static final AtomicLongFieldUpdater<ManagedServlet> unavailableUntilUpdater = AtomicLongFieldUpdater.newUpdater(ManagedServlet.class, "unavailableUntil");

    @SuppressWarnings("unused")
    private volatile long unavailableUntil = 0;

    public ManagedServlet(final ServletInfo servletInfo, final ServletContextImpl servletContext) {
        this.servletInfo = servletInfo;
        this.servletContext = servletContext;
        instanceStrategy = new DefaultInstanceStrategy(servletInfo.getInstanceFactory(), servletInfo, servletContext);
        setupMultipart(servletContext);
    }

    public void setupMultipart(ServletContextImpl servletContext) {
        FormEncodedDataDefinition formDataParser = new FormEncodedDataDefinition()
                .setDefaultEncoding(servletContext.getDeployment().getDefaultRequestCharset().name());
        MultipartConfigElement multipartConfig = servletInfo.getMultipartConfig();
        if(multipartConfig == null) {
            multipartConfig = servletContext.getDeployment().getDeploymentInfo().getDefaultMultipartConfig();
        }
        this.multipartConfig = multipartConfig;
        if (multipartConfig != null) {
            //todo: fileSizeThreshold
            MultipartConfigElement config = multipartConfig;
            if (config.getMaxRequestSize() != -1) {
                maxMultipartRequestSize = config.getMaxRequestSize();
            } else {
                maxMultipartRequestSize = -1;
            }
            final Path tempDir;
            if(config.getLocation() == null || config.getLocation().isEmpty()) {
                tempDir = servletContext.getDeployment().getDeploymentInfo().getTempPath();
            } else {
                String location = config.getLocation();
                Path locFile = Paths.get(location);
                if(locFile.isAbsolute()) {
                    tempDir = locFile;
                } else {
                    final DeploymentInfo deploymentInfo = servletContext.getDeployment().getDeploymentInfo();
                    tempDir = deploymentInfo.requireTempPath().resolve(location);
                }
            }

            MultiPartParserDefinition multiPartParserDefinition = new MultiPartParserDefinition(tempDir);
            if(config.getMaxFileSize() > 0) {
                multiPartParserDefinition.setMaxIndividualFileSize(config.getMaxFileSize());
            }
            if (config.getFileSizeThreshold() > 0) {
                multiPartParserDefinition.setFileSizeThreshold(config.getFileSizeThreshold());
            }
            multiPartParserDefinition.setDefaultEncoding(servletContext.getDeployment().getDefaultRequestCharset().name());

            formParserFactory = FormParserFactory.builder(false)
                    .addParser(formDataParser)
                    .addParser(multiPartParserDefinition)
                    .build();

        } else {
            //no multipart config we don't allow multipart requests
            formParserFactory = FormParserFactory.builder(false).addParser(formDataParser).build();
            maxMultipartRequestSize = -1;
        }
    }


    public synchronized void start() throws ServletException {

    }

    public void createServlet() throws ServletException {
        if (permanentlyUnavailable) {
            return;
        }
        try {
            if (!started && servletInfo.getLoadOnStartup() != null && servletInfo.getLoadOnStartup() >= 0) {
                instanceStrategy.start();
                started = true;
            }
        } catch (UnavailableException e) {
            if (e.isPermanent()) {
                permanentlyUnavailable = true;
                stop();
            }
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

    public boolean isPermanentlyUnavailable() {
        return permanentlyUnavailable;
    }

    public boolean isTemporarilyUnavailable() {
        long until = unavailableUntil;
        if (until != 0) {
            if (System.currentTimeMillis() < until) {
                return true;
            } else {
                unavailableUntilUpdater.compareAndSet(this, until, 0);
            }
        }
        return false;
    }

    public void setPermanentlyUnavailable(final boolean permanentlyUnavailable) {
        this.permanentlyUnavailable = permanentlyUnavailable;
    }

    public InstanceHandle<? extends Servlet> getServlet() throws ServletException {
        if(servletContext.getDeployment().getDeploymentState() != DeploymentManager.State.STARTED) {
            throw UndertowServletMessages.MESSAGES.deploymentStopped(servletContext.getDeployment().getDeploymentInfo().getDeploymentName());
        }
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


    public void forceInit() throws ServletException {
        if (!started) {
            if(servletContext.getDeployment().getDeploymentState() != DeploymentManager.State.STARTED) {
                throw UndertowServletMessages.MESSAGES.deploymentStopped(servletContext.getDeployment().getDeploymentInfo().getDeploymentName());
            }
            synchronized (this) {
                if (!started) {
                    try {
                        instanceStrategy.start();
                    } catch (UnavailableException e) {
                        handleUnavailableException(e);
                    }
                    started = true;
                }
            }
        }
    }

    public void handleUnavailableException(UnavailableException e) {
        if (e.isPermanent()) {
            UndertowServletLogger.REQUEST_LOGGER.stoppingServletDueToPermanentUnavailability(getServletInfo().getName(), e);
            stop();
            setPermanentlyUnavailable(true);
        } else {
            long until = System.currentTimeMillis() + e.getUnavailableSeconds() * 1000;
            unavailableUntilUpdater.set(this, until);
            UndertowServletLogger.REQUEST_LOGGER.stoppingServletUntilDueToTemporaryUnavailability(getServletInfo().getName(), new Date(until), e);
        }
    }

    public ServletInfo getServletInfo() {
        return servletInfo;
    }

    /**
     * This value determines max multipart message size
     * @return
     */
    public long getMaxRequestSize() {
        return maxMultipartRequestSize;
    }

    public FormParserFactory getFormParserFactory() {
        return formParserFactory;
    }

    public MultipartConfigElement getMultipartConfig() {
        return multipartConfig;
    }

    @Override
    public String toString() {
        return "ManagedServlet{" +
                "servletInfo=" + servletInfo +
                '}';
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
        private ResourceChangeListener changeListener;
        private final InstanceHandle<Servlet> instanceHandle = new InstanceHandle<Servlet>() {
            @Override
            public Servlet getInstance() {
                return instance;
            }

            @Override
            public void release() {

            }
        };

        DefaultInstanceStrategy(final InstanceFactory<? extends Servlet> factory, final ServletInfo servletInfo, final ServletContextImpl servletContext) {
            this.factory = factory;
            this.servletInfo = servletInfo;
            this.servletContext = servletContext;
        }

        public synchronized void start() throws ServletException {
            try {
                handle = factory.createInstance();
            } catch (Exception e) {
                throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(servletInfo.getName(), e);
            }
            instance = handle.getInstance();
            new LifecyleInterceptorInvocation(servletContext.getDeployment().getDeploymentInfo().getLifecycleInterceptors(), servletInfo, instance, new ServletConfigImpl(servletInfo, servletContext)).proceed();

            //if a servlet implements FileChangeCallback it will be notified of file change events
            final ResourceManager resourceManager = servletContext.getDeployment().getDeploymentInfo().getResourceManager();
            if(instance instanceof ResourceChangeListener && resourceManager.isResourceChangeListenerSupported()) {
                resourceManager.registerResourceChangeListener(changeListener = (ResourceChangeListener) instance);
            }
        }

        public synchronized void stop() {
            if (handle != null) {
                final ResourceManager resourceManager = servletContext.getDeployment().getDeploymentInfo().getResourceManager();
                if(changeListener != null) {
                    resourceManager.removeResourceChangeListener(changeListener);
                }
                invokeDestroy();
                handle.release();
            }
        }

        private void invokeDestroy() {
            List<LifecycleInterceptor> interceptors = servletContext.getDeployment().getDeploymentInfo().getLifecycleInterceptors();
            try {
                new LifecyleInterceptorInvocation(interceptors, servletInfo, instance).proceed();
            } catch (Exception e) {
                UndertowServletLogger.ROOT_LOGGER.failedToDestroy(servletInfo, e);
            }
        }

        public InstanceHandle<? extends Servlet> getServlet() {
            return instanceHandle;
        }
    }

}
