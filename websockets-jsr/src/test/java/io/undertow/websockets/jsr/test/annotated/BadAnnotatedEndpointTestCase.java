/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.jsr.test.annotated;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.servlet.ServletException;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerEndpointConfig;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
/**
 * @author baranowb
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class BadAnnotatedEndpointTestCase {

    private static ServerWebSocketContainer deployment;
    private static DeploymentManager deploymentManager;

    @BeforeClass
    public static void setup() throws Exception {

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(BadAnnotatedEndpointTestCase.class.getClassLoader()).setContextPath("/ws")
                .setResourceManager(new TestResourceLoader(BadAnnotatedEndpointTestCase.class))
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorkerSupplier())
                                .addEndpoint(BadOnMessageEndpoint.class)
                                .addListener(readyContainer -> deployment = readyContainer)
                                .addEndpoint(ServerEndpointConfig.Builder.create(
                                        AnnotatedAddedProgrammaticallyEndpoint.class,
                                        AnnotatedAddedProgrammaticallyEndpoint.PATH)
                                        .build())
                )
                .setDeploymentName("servletContext.war");


        deploymentManager = container.addDeployment(builder);

    }

    @AfterClass
    public static void after() throws ServletException {
        if (deployment != null) {
            deployment.close();
            deployment = null;
        }
        if (deploymentManager != null) {
            deploymentManager.stop();
            deploymentManager.undeploy();
        }
    }

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void testStringOnMessage() throws Exception {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("jakarta.websocket.DeploymentException: UT003012: Method public int io.undertow.websockets.jsr.test.annotated.BadOnMessageEndpoint.handleMessage(int,jakarta.websocket.EndpointConfig) has invalid parameters at locations [1]");
        thrown.expectCause(instanceOf(DeploymentException.class));
        deploymentManager.deploy();
    }
}