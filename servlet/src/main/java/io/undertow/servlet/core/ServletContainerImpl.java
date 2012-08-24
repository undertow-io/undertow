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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;

/**
 *
 * The manager for all servlet deployments.
 *
 * @author Stuart Douglas
 */
public class ServletContainerImpl implements ServletContainer {

    private final PathHandler rootContext;

    private final Map<String, DeploymentManager> deployments = Collections.synchronizedMap(new HashMap<String, DeploymentManager>());
    private final Map<String, DeploymentManager> deploymentsByPath = Collections.synchronizedMap(new HashMap<String, DeploymentManager>());

    public ServletContainerImpl(final PathHandler rootContext) {
        this.rootContext = rootContext;
    }

    @Override
    public Collection<String> listDeployments() {
        return new HashSet<String>(deployments.keySet());
    }

    @Override
    public DeploymentManager addDeployment(final DeploymentInfo deployment) {
        final DeploymentInfo dep = deployment.clone();
        DeploymentManager deploymentManager = new DeploymentManagerImpl(dep, rootContext, this);
        deployments.put(dep.getDeploymentName(), deploymentManager);
        deploymentsByPath.put(dep.getContextPath(), deploymentManager);
        return deploymentManager;
    }

    @Override
    public DeploymentManager getDeployment(final String deploymentName) {
        return deployments.get(deploymentName);
    }

    @Override
    public void removeDeployment(final String deploymentName) {
        final DeploymentManager deploymentManager = deployments.get(deploymentName);
        if(deploymentManager.getState() != DeploymentManager.State.UNDEPLOYED) {
            throw UndertowServletMessages.MESSAGES.canOnlyRemoveDeploymentsWhenUndeployed(deploymentManager.getState());
        }
        deployments.remove(deploymentName);
    }

    @Override
    public DeploymentManager getDeploymentByPath(final String uripath) {
        return deploymentsByPath.get(uripath);
    }
}
