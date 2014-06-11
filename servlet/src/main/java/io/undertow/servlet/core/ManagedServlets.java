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

import java.util.HashMap;
import java.util.Map;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.ServletHandler;
import io.undertow.servlet.handlers.ServletPathMatches;
import io.undertow.util.CopyOnWriteMap;

/**
 * Runtime representation of servlets. Basically a container for {@link ManagedServlet} instances
 *
 * @author Stuart Douglas
 */
public class ManagedServlets {

    private final Map<String, ServletHandler> managedServletMap = new CopyOnWriteMap<>();
    private final DeploymentImpl deployment;
    private final ServletPathMatches servletPaths;

    public ManagedServlets(final DeploymentImpl deployment, final ServletPathMatches servletPaths) {
        this.deployment = deployment;
        this.servletPaths = servletPaths;
    }

    public ServletHandler addServlet(final ServletInfo servletInfo) {
        ManagedServlet managedServlet = new ManagedServlet(servletInfo, deployment.getServletContext());
        ServletHandler servletHandler = new ServletHandler(managedServlet);
        managedServletMap.put(servletInfo.getName(), servletHandler);
        deployment.addLifecycleObjects(managedServlet);
        this.servletPaths.invalidate();

        return servletHandler;
    }

    public ManagedServlet getManagedServlet(final String name) {
        ServletHandler servletHandler = managedServletMap.get(name);
        if(servletHandler == null) {
            return null;
        }
        return servletHandler.getManagedServlet();
    }

    public ServletHandler getServletHandler(final String name) {
        return managedServletMap.get(name);
    }

    public Map<String, ServletHandler> getServletHandlers() {
        return new HashMap<>(managedServletMap);
    }

}
