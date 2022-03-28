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

package io.undertow.servlet;

import io.undertow.servlet.api.DeploymentInfo;

import jakarta.servlet.ServletContext;

/**
 *
 * Interface that allows the servlet deployment to be modified before it is deployed.
 *
 * These extensions are loaded using a {@link java.util.ServiceLoader} from the deployment
 * class loader, and are the first things run after the servlet context is created.
 *
 * There are many possible use cases for these extensions. Some obvious ones are:
 *
 * - Adding additional handlers
 * - Adding new authentication mechanisms
 * - Adding and removing servlets
 *
 *
 * @author Stuart Douglas
 */
public interface ServletExtension {

    void handleDeployment(final DeploymentInfo deploymentInfo, final ServletContext servletContext);

}
