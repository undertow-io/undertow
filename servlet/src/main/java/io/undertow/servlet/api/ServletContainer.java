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

package io.undertow.servlet.api;

import java.util.Collection;

import io.undertow.servlet.core.ServletContainerImpl;

/**
 * @author Stuart Douglas
 */
public interface ServletContainer {

    /**
     *
     * @return The names of the deployments in this container
     */
    Collection<String> listDeployments();

    DeploymentManager addDeployment(DeploymentInfo deployment);

    DeploymentManager getDeployment(String deploymentName);

    void removeDeployment(DeploymentInfo deploymentInfo);

    DeploymentManager getDeploymentByPath(String uripath);

    class Factory {

        public static ServletContainer newInstance() {
            return new ServletContainerImpl();
        }
    }

}
