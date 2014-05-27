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
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.websockets.jsr.DefaultWebSocketClientSslProvider;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.xnio.ByteBufferSlicePool;

/**
 * @author Andrej Golovnin
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
@AjpIgnore
public class BinaryEndpointTest {

    private static ServerWebSocketContainer deployment;

    private static byte[] bytes;

    @BeforeClass
    public static void setup() throws Exception {

        bytes = new byte[256 * 1024];
        new Random().nextBytes(bytes);

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(BinaryEndpointTest.class.getClassLoader())
                .setContextPath("/")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addServlet(Servlets.servlet("bin", BinaryEndpointServlet.class).setLoadOnStartup(100))
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(new ByteBufferSlicePool(16 * 1024, 16 * 1024))
                                .setWorker(DefaultServer.getWorker())
                                .addListener(new WebSocketDeploymentInfo.ContainerReadyListener() {
                                    @Override
                                    public void ready(ServerWebSocketContainer container) {
                                        deployment = container;
                                    }
                                })
                )
                .setDeploymentName("servletContext.war");


        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();


        DefaultServer.setRootHandler(manager.start());
        DefaultServer.startSSLServer();
    }

    @AfterClass
    public static void after() throws IOException {
        deployment = null;
        DefaultServer.stopSSLServer();
    }

    @org.junit.Test
    public void testBytesOnMessage() throws Exception {
        SSLContext context = DefaultServer.getClientSSLContext();
        ProgramaticClientEndpoint endpoint = new ProgramaticClientEndpoint();

        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        clientEndpointConfig.getUserProperties().put(DefaultWebSocketClientSslProvider.SSL_CONTEXT, context);
        ContainerProvider.getWebSocketContainer().connectToServer(endpoint, clientEndpointConfig, new URI("wss://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostSSLPort("default") + "/partial"));
        Assert.assertArrayEquals(bytes, endpoint.getResponses().poll(15, TimeUnit.SECONDS));
    }

    public static class ProgramaticClientEndpoint extends Endpoint {

        private final LinkedBlockingDeque<byte[]> responses = new LinkedBlockingDeque<byte[]>();

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.getAsyncRemote().sendBinary(ByteBuffer.wrap(bytes));
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {

                @Override
                public void onMessage(byte[] message) {
                    responses.add(message);
                }
            });
        }

        public LinkedBlockingDeque<byte[]> getResponses() {
            return responses;
        }
    }
}
