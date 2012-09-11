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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.undertow.server.HttpHandler;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * Class that represents the mutable state associated with a servlet deployment that is built up
 * during the bootstrap process.
 *
 * Classes calling deployment methods during bootstrap must be aware of ordering concerns.
 *
 * @author Stuart Douglas
 */
public class DeploymentImpl implements Deployment {

    private final DeploymentInfo deploymentInfo;
    private final List<Lifecycle> lifecycleObjects = new ArrayList<Lifecycle>();
    private volatile ApplicationListeners applicationListeners;
    private volatile ServletContextImpl servletContext;
    private volatile HttpHandler servletHandler;


    public DeploymentImpl(final DeploymentInfo deploymentInfo) {
        this.deploymentInfo = deploymentInfo;
    }

    public void setApplicationListeners(final ApplicationListeners applicationListeners) {
        this.applicationListeners = applicationListeners;
    }

    public void setServletContext(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public DeploymentInfo getDeploymentInfo() {
        return deploymentInfo;
    }

    @Override
    public ApplicationListeners getApplicationListeners() {
        return applicationListeners;
    }

    @Override
    public ServletContextImpl getServletContext() {
        return servletContext;
    }

    @Override
    public HttpHandler getServletHandler() {
        return servletHandler;
    }

    public void setServletHandler(final HttpHandler servletHandler) {
        this.servletHandler = servletHandler;
    }

    public void addLifecycleObjects(final Collection<Lifecycle> objects) {
        lifecycleObjects.addAll(objects);
    }

    public void addLifecycleObjects(final Lifecycle ... objects) {
        lifecycleObjects.addAll(Arrays.asList(objects));
    }

    public List<Lifecycle> getLifecycleObjects() {
        return Collections.unmodifiableList(lifecycleObjects);
    }
}
