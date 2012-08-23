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

import javax.servlet.ServletContext;

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
     * Starts the container. Any servlets with init on startup will be created here
     */
    void start();

    void stop();

    void undeploy();

    State getState();

    /**
     *
     * @return This deployments ServletContext
     */
    ServletContext getServletContext();

    public static enum State {
        UNDEPLOYED,
        DEPLOYED,
        STARTED;
    }
}
