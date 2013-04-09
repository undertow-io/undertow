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

package io.undertow.websockets.jsr.bootstrap;

import io.undertow.websockets.jsr.ServerWebSocketContainer;

/**
 * Represents a web socket deployment. This class does not manage the deployment
 * lifecycle, but rather just manages adding web socket servlets etc into the servlet
 * deployment.
 *
 * @author Stuart Douglas
 */
public class WebSocketDeployment {

    private final WebSocketDeploymentInfo deploymentInfo;
    private final ServerWebSocketContainer container;

    private WebSocketDeployment(final WebSocketDeploymentInfo deploymentInfo) {
        this.deploymentInfo = deploymentInfo;
        this.container = new ServerWebSocketContainer(deploymentInfo);
    }

    public static WebSocketDeployment create(final WebSocketDeploymentInfo deploymentInfo) {
        return new WebSocketDeployment(deploymentInfo);
    }


    public WebSocketDeploymentInfo getDeploymentInfo() {
        return deploymentInfo;
    }

    public ServerWebSocketContainer getContainer() {
        return container;
    }
}
