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
import java.util.Collections;
import java.util.HashMap;
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
    private final String contextName;
    private final ClassLoader classLoader;
    private final ResourceLoader resourceLoader;
    private final Map<String, ServletInfo> servlets;

    DeploymentInfo(final String deploymentName, final String contextName, final ClassLoader classLoader, final ResourceLoader resourceLoader, final Map<String, ServletInfo> servlets) {
        this.deploymentName = deploymentName;
        this.contextName = contextName;
        this.classLoader = classLoader;
        this.resourceLoader = resourceLoader;
        this.servlets = Collections.unmodifiableMap(new HashMap<String, ServletInfo>(servlets));
    }

    public String getDeploymentName() {
        return deploymentName;
    }

    public String getContextName() {
        return contextName;
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

    public static DeploymentInfoBuilder builder() {
        return new DeploymentInfoBuilder();
    }

    public static class DeploymentInfoBuilder {

        private String deploymentName;
        private String contextName;
        private ClassLoader classLoader;
        private ResourceLoader resourceLoader;
        private final List<ServletInfo.ServletInfoBuilder> servlets = new ArrayList<ServletInfo.ServletInfoBuilder>();

        DeploymentInfoBuilder() {

        }

        public DeploymentInfo build() {

            if (deploymentName == null) {
                throw UndertowServletMessages.MESSAGES.paramCannotBeNull("deploymentName");
            }
            if (contextName == null) {
                throw UndertowServletMessages.MESSAGES.paramCannotBeNull("contextName");
            }
            if (classLoader == null) {
                throw UndertowServletMessages.MESSAGES.paramCannotBeNull("classLoader");
            }
            if (resourceLoader == null) {
                throw UndertowServletMessages.MESSAGES.paramCannotBeNull("resourceLoader");
            }

            final Map<String, ServletInfo> servlets = new HashMap<String, ServletInfo>();
            for (final ServletInfo.ServletInfoBuilder servlet : this.servlets) {
                if (servlets.containsKey(servlet.getName())) {
                    throw UndertowServletMessages.MESSAGES.twoServletsWithSameName();
                }
                servlets.put(servlet.getName(), servlet.build());
            }
            return new DeploymentInfo(deploymentName, contextName, classLoader, resourceLoader, servlets);
        }

        public String getDeploymentName() {
            return deploymentName;
        }

        public DeploymentInfoBuilder setDeploymentName(final String deploymentName) {
            this.deploymentName = deploymentName;
            return this;
        }

        public String getContextName() {
            return contextName;
        }

        public DeploymentInfoBuilder setContextName(final String contextName) {
            this.contextName = contextName;
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

        public List<ServletInfo.ServletInfoBuilder> getServlets() {
            return servlets;
        }
    }

}
