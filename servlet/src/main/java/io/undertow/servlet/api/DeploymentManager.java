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

import javax.servlet.ServletException;

import io.undertow.server.HttpHandler;

/**
 * Manager that can be used to deploy and undeploy a servlet deployment.
 *
 * @author Stuart Douglas
 */
public interface DeploymentManager {

    /**
     * Perform the initial deployment.
     *
     * The builds all the internal metadata needed to support the servlet deployment, but will not actually start
     * any servlets
     *
     */
    void deploy();

    /**
     * Starts the container. Any Servlets with init on startup will be created here. This method returns the servlet
     * path handler, which must then be added into the appropriate place in the path handler tree.
     *
     */
    HttpHandler start() throws ServletException;

    void stop() throws ServletException;

    void undeploy();

    State getState();

    /**
     *
     */
    Deployment getDeployment();

    enum State {
        UNDEPLOYED,
        DEPLOYED,
        STARTED;
    }
}
