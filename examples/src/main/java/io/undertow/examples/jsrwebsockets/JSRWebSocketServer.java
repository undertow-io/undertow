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

package io.undertow.examples.jsrwebsockets;

import jakarta.servlet.ServletException;

import io.undertow.server.DefaultByteBufferPool;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;

/**
 * @author Stuart Douglas
 */
@UndertowExample("JSR Web Sockets")
public class JSRWebSocketServer {

    public static void main(final String[] args)  {
        PathHandler path = Handlers.path();


        Undertow server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(path)
                .build();
        server.start();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(JSRWebSocketServer.class.getClassLoader())
                .setContextPath("/")
                .addWelcomePage("index.html")
                .setResourceManager(new ClassPathResourceManager(JSRWebSocketServer.class.getClassLoader(), JSRWebSocketServer.class.getPackage()))
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(new DefaultByteBufferPool(true, 100))
                                .addEndpoint(JsrChatWebSocketEndpoint.class)
                )
                .setDeploymentName("chat.war");


        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        try {
            path.addPrefixPath("/", manager.start());
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }


    }


}
