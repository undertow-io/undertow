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

package io.undertow.servlet.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.undertow.servlet.UndertowServletMessages;

/**
 * Represents a servlet deployment.
 *
 * @author Stuart Douglas
 */
public class DeploymentInfo {

    private final String deploymentName;
    private final String contextPath;
    private final ClassLoader classLoader;
    private final ResourceLoader resourceLoader;
    private final int majorVersion;
    private final int minorVersion;
    private final Map<String, ServletInfo> servlets;
    private final Map<String, FilterInfo> filters;
    private final List<Class<?>> listeners;

    DeploymentInfo(final String deploymentName, final String contextPath, final ClassLoader classLoader,
                   final ResourceLoader resourceLoader, final Map<String, ServletInfo> servlets,
                   final Map<String, FilterInfo> filters, final List<Class<?>> listeners, final int majorVersion, final int minorVersion) {
        this.deploymentName = deploymentName;
        this.contextPath = contextPath;
        this.classLoader = classLoader;
        this.resourceLoader = resourceLoader;
        this.listeners = listeners;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.servlets = Collections.unmodifiableMap(new LinkedHashMap<String, ServletInfo>(servlets));
        this.filters = Collections.unmodifiableMap(new LinkedHashMap<String, FilterInfo>(filters));

    }

    /**
     * Gets the deployment name
     *
     * @return The deployment name
     */
    public String getDeploymentName() {
        return deploymentName;
    }

    public String getContextPath() {
        return contextPath;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    public Map<String, ServletInfo> getServlets() {
        return servlets;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public Map<String, FilterInfo> getFilters() {
        return filters;
    }

    public List<Class<?>> getListeners() {
        return listeners;
    }

    public static DeploymentInfoBuilder builder() {
        return new DeploymentInfoBuilder();
    }

    public DeploymentInfoBuilder copy() {
        final DeploymentInfoBuilder builder = new DeploymentInfoBuilder()
                .setClassLoader(classLoader)
                .setContextPath(contextPath)
                .setResourceLoader(resourceLoader)
                .setMajorVersion(majorVersion)
                .setMinorVersion(minorVersion)
                .setDeploymentName(deploymentName);

        for(Map.Entry<String, ServletInfo> e : servlets.entrySet()) {
            builder.addServlet(e.getValue().copy());
        }

        for(Map.Entry<String, FilterInfo> e : filters.entrySet()) {
            builder.addFilter(e.getValue().copy());
        }
        builder.listeners.addAll(listeners);

        return builder;
    }

    public static class DeploymentInfoBuilder {

        private String deploymentName;
        private String contextPath;
        private ClassLoader classLoader;
        private ResourceLoader resourceLoader;
        private int majorVersion = 3;
        private int minorVersion = 0;
        private final List<ServletInfo.ServletInfoBuilder> servlets = new ArrayList<ServletInfo.ServletInfoBuilder>();
        private final List<FilterInfo.FilterInfoBuilder> filters = new ArrayList<FilterInfo.FilterInfoBuilder>();
        private final List<Class<?>> listeners = new ArrayList<Class<?>>();

        DeploymentInfoBuilder() {

        }

        public DeploymentInfo build() {

            if (deploymentName == null) {
                throw UndertowServletMessages.MESSAGES.paramCannotBeNull("deploymentName");
            }
            if (contextPath == null) {
                throw UndertowServletMessages.MESSAGES.paramCannotBeNull("contextName");
            }
            if (classLoader == null) {
                throw UndertowServletMessages.MESSAGES.paramCannotBeNull("classLoader");
            }
            if (resourceLoader == null) {
                throw UndertowServletMessages.MESSAGES.paramCannotBeNull("resourceLoader");
            }

            final Map<String, ServletInfo> servlets = new LinkedHashMap<String, ServletInfo>();
            for (final ServletInfo.ServletInfoBuilder servlet : this.servlets) {
                if (servlets.containsKey(servlet.getName())) {
                    throw UndertowServletMessages.MESSAGES.twoServletsWithSameName();
                }
                servlets.put(servlet.getName(), servlet.build());
            }
            final Map<String, FilterInfo> filters = new LinkedHashMap<String, FilterInfo>();
            for (final FilterInfo.FilterInfoBuilder filter : this.filters) {
                if (filters.containsKey(filter.getName())) {
                    throw UndertowServletMessages.MESSAGES.twoFiltersWithSameName();
                }
                filters.put(filter.getName(), filter.build());
            }
            return new DeploymentInfo(deploymentName, contextPath, classLoader, resourceLoader, servlets, filters, listeners, majorVersion, minorVersion);
        }

        public String getDeploymentName() {
            return deploymentName;
        }

        public DeploymentInfoBuilder setDeploymentName(final String deploymentName) {
            this.deploymentName = deploymentName;
            return this;
        }

        public String getContextPath() {
            return contextPath;
        }

        public DeploymentInfoBuilder setContextPath(final String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public DeploymentInfoBuilder setClassLoader(final ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public ResourceLoader getResourceLoader() {
            return resourceLoader;
        }

        public DeploymentInfoBuilder setResourceLoader(final ResourceLoader resourceLoader) {
            this.resourceLoader = resourceLoader;
            return this;
        }

        public DeploymentInfoBuilder addServlet(final ServletInfo.ServletInfoBuilder servlet) {
            servlets.add(servlet);
            return this;
        }

        public DeploymentInfoBuilder addServlets(final ServletInfo.ServletInfoBuilder ... servlets) {
            this.servlets.addAll(Arrays.asList(servlets));
            return this;
        }

        public DeploymentInfoBuilder addServlets(final Collection<ServletInfo.ServletInfoBuilder> servlets) {
            this.servlets.addAll(servlets);
            return this;
        }

        public List<ServletInfo.ServletInfoBuilder> getServlets() {
            return servlets;
        }


        public DeploymentInfoBuilder addFilter(final FilterInfo.FilterInfoBuilder filter) {
            filters.add(filter);
            return this;
        }

        public DeploymentInfoBuilder addFilters(final FilterInfo.FilterInfoBuilder ... filters) {
            this.filters.addAll(Arrays.asList(filters));
            return this;
        }

        public DeploymentInfoBuilder addFilters(final Collection<FilterInfo.FilterInfoBuilder> filters) {
            this.filters.addAll(filters);
            return this;
        }

        public List<FilterInfo.FilterInfoBuilder> getFilters() {
            return filters;
        }

        public DeploymentInfoBuilder addListener(final Class<?> listener) {
            listeners.add(listener);
            return this;
        }

        public DeploymentInfoBuilder addListeners(final Class<?> ... listeners) {
            this.listeners.addAll(Arrays.asList(listeners));
            return this;
        }

        public DeploymentInfoBuilder addListeners(final Collection<Class<?>> listeners) {
            this.listeners.addAll(listeners);
            return this;
        }

        public List<Class<?>> getListeners() {
            return listeners;
        }

        public int getMajorVersion() {
            return majorVersion;
        }

        public DeploymentInfoBuilder setMajorVersion(final int majorVersion) {
            this.majorVersion = majorVersion;
            return this;
        }

        public int getMinorVersion() {
            return minorVersion;
        }

        public DeploymentInfoBuilder setMinorVersion(final int minorVersion) {
            this.minorVersion = minorVersion;
            return this;
        }
    }

}
