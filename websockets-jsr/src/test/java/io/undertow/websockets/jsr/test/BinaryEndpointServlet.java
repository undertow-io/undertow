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

package io.undertow.websockets.jsr.test;

import java.io.IOException;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * @author Andrej Golovnin
 * @author Stuart Douglas
 */
public class BinaryEndpointServlet implements Servlet {
    @Override
    public void init(ServletConfig c) throws ServletException {
        String websocketPath = "/partial";
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(BinaryPartialEndpoint.class, websocketPath).build();
        ServerContainer serverContainer = (ServerContainer) c.getServletContext().getAttribute("jakarta.websocket.server.ServerContainer");
        try {
            serverContainer.addEndpoint(config);
        } catch (DeploymentException ex) {
            throw new ServletException("Error deploying websocket endpoint:", ex);
        }
    }

    @Override
    public ServletConfig getServletConfig() {
        return null;
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
    }

    @Override
    public String getServletInfo() {
        return null;
    }

    @Override
    public void destroy() {
    }
}
