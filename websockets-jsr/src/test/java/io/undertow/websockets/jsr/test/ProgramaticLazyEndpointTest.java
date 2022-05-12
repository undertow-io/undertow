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

import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.websockets.jsr.DefaultWebSocketClientSslProvider;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import javax.net.ssl.SSLContext;
import jakarta.servlet.ServletException;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class ProgramaticLazyEndpointTest {

    private static ServerWebSocketContainer deployment;
    private static DeploymentManager deploymentManager;

    @BeforeClass
    public static void setup() throws Exception {

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(ProgramaticLazyEndpointTest.class.getClassLoader())
                .setContextPath("/")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addServlet(Servlets.servlet("add", AddEndpointServlet.class).setLoadOnStartup(100))
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorker())
                                .addListener(container1 -> deployment = container1)
                )
                .setDeploymentName("servletContext.war");


        deploymentManager = container.addDeployment(builder);
        deploymentManager.deploy();

        DefaultServer.setRootHandler(deploymentManager.start());
        DefaultServer.startSSLServer();
    }

    @AfterClass
    public static void after() throws IOException, ServletException {
        if (deployment != null) {
            deployment.close();
            deployment = null;
        }
        if (deploymentManager != null) {
            deploymentManager.stop();
            deploymentManager.undeploy();
        }
        DefaultServer.stopSSLServer();
    }

    @org.junit.Test
    public void testStringOnMessage() throws Exception {
        SSLContext context = DefaultServer.getClientSSLContext();
        ProgramaticClientEndpoint endpoint = new ProgramaticClientEndpoint();

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        clientEndpointConfig.getUserProperties().put(DefaultWebSocketClientSslProvider.SSL_CONTEXT, context);
        ContainerProvider.getWebSocketContainer().connectToServer(endpoint, clientEndpointConfig, new URI("wss://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostSSLPort("default") + "/foo"));
        Assert.assertEquals("Hello Stuart", endpoint.getResponses().poll(15, TimeUnit.SECONDS));
        endpoint.session.close();
        assertTrue(endpoint.closeLatch.await(10, TimeUnit.SECONDS));
    }

    public static class ProgramaticClientEndpoint extends Endpoint {

        private final LinkedBlockingDeque<String> responses = new LinkedBlockingDeque<>();

        final CountDownLatch closeLatch = new CountDownLatch(1);
        volatile Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.session = session;
            session.getAsyncRemote().sendText("Stuart");
            session.addMessageHandler(new MessageHandler.Whole<String>() {

                @Override
                public void onMessage(String message) {
                    responses.add(message);
                }
            });
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            closeLatch.countDown();
        }

        public LinkedBlockingDeque<String> getResponses() {
            return responses;
        }
    }
}
