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

package io.undertow.websockets.jsr.test.reconnect;

import io.undertow.Handlers;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import io.undertow.websockets.jsr.WebSocketReconnectHandler;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import java.io.IOException;
import java.net.URI;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class ClientEndpointReconnectTestCase {

    private static ServerWebSocketContainer deployment;
    private static DeploymentManager deploymentManager;
    private static volatile boolean failed = false;

    @BeforeClass
    public static void setup() throws Exception {

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(ClientEndpointReconnectTestCase.class.getClassLoader())
                .setContextPath("/ws")
                .setResourceManager(new TestResourceLoader(ClientEndpointReconnectTestCase.class))
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(new DefaultByteBufferPool(true, 8192))
                                .setWorker(DefaultServer.getWorkerSupplier())
                                .addEndpoint(DisconnectServerEndpoint.class)
                                .addEndpoint(AnnotatedClientReconnectEndpoint.class)
                                .addListener(containerReady -> deployment = containerReady).setReconnectHandler(new WebSocketReconnectHandler() {
                            @Override
                            public long disconnected(CloseReason closeReason, URI connectionUri, Session session, int disconnectCount) {
                                if (disconnectCount < 3) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            }

                            @Override
                            public long reconnectFailed(IOException exception, URI connectionUri, Session session, int failedCount) {
                                failed = true;
                                return -1;
                            }
                        })
                )
                .setDeploymentName("servletContext.war");
        deploymentManager = container.addDeployment(builder);
        deploymentManager.deploy();

        DefaultServer.setRootHandler(Handlers.path().addPrefixPath("/ws", deploymentManager.start()));
    }

    @AfterClass
    public static void after() throws ServletException {
        deployment = null;
        if (deploymentManager != null) {
            deploymentManager.stop();
            deploymentManager.undeploy();
        }
    }

    @Test
    public void testAnnotatedClientEndpoint() throws Exception {
        AnnotatedClientReconnectEndpoint endpoint = new AnnotatedClientReconnectEndpoint();
        Session session = deployment.connectToServer(endpoint, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/ws/"));

        Assert.assertEquals("OPEN", endpoint.message());
        session.getBasicRemote().sendText("hi");
        Assert.assertEquals("MESSAGE-ECHO-hi", endpoint.message());
        session.getBasicRemote().sendText("close");
        Assert.assertEquals("CLOSE", endpoint.message());
        Assert.assertEquals("OPEN", endpoint.message());
        session.getBasicRemote().sendText("hi");
        Assert.assertEquals("MESSAGE-ECHO-hi", endpoint.message());
        session.getBasicRemote().sendText("close");
        Assert.assertEquals("CLOSE", endpoint.message());
        Assert.assertEquals("OPEN", endpoint.message());
        session.getBasicRemote().sendText("hi");
        Assert.assertEquals("MESSAGE-ECHO-hi", endpoint.message());
        session.getBasicRemote().sendText("close");
        Assert.assertEquals("CLOSE", endpoint.message());
        Assert.assertNull(endpoint.quickMessage());
        Assert.assertFalse(failed);
    }
}
