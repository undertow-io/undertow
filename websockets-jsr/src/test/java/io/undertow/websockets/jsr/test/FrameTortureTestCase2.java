/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferTortureTestBase;
import io.undertow.websockets.extensions.PerMessageDeflateHandshake;
import io.undertow.websockets.jsr.JsrWebSocketFilter;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Testcases for various torture scenarios. This is CC of BufferTorture test which just base test on frames.
 *
 * @author Avishek Sarkar
 * @author baranowb
 *
 */
public class FrameTortureTestCase2 extends BufferTortureTestBase {
    //this is sort of CC of BufferTortureTestCase, because we cant override statics.
    //differences is listener vs in OnMessage buffer size.
    private static DeploymentManager deploymentManager;

    @DefaultServer.BeforeServerStarts
    public static void beforeTests() throws DeploymentException, ServletException {
        assertNull("Can only be invoked once per method test", deploymentManager);
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(BinaryEndpointTest.class.getClassLoader())
                .setContextPath("/ws")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addFilter(new FilterInfo("filter", JsrWebSocketFilter.class))
                .addFilterUrlMapping("filter", "/*", DispatcherType.REQUEST)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setDispatchToWorkerThread(true)
                                .setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorkerSupplier())
                                .addExtension(new PerMessageDeflateHandshake(false, PerMessageDeflateHandshake.DEFAULT_DEFLATER, true, true,
                                        PerMessageDeflateHandshake.DEFAULT_MAX_BUFFER_SIZE * 3))
                        .addEndpoint(BufferTortureEndpoint2.class)
                )
                .setDeploymentName("ws.war");


        deploymentManager = container.addDeployment(builder);
        deploymentManager.deploy();

        System.setProperty(AbstractReceiveListener.WEB_SOCKETS_MAX_READ_FRAMES_PROPERTY, "30");

        DefaultServer.setRootHandler(deploymentManager.start());
    }

    @DefaultServer.AfterServerStops
    public static void afterTest() {
        //DefaultServer.setServerOptions(OptionMap.EMPTY);
        System.clearProperty(AbstractReceiveListener.WEB_SOCKETS_MAX_READ_FRAMES_PROPERTY);
    }

    @ServerEndpoint(value ="/ws", configurator = CustomConfigurator.class)
    public static class BufferTortureEndpoint2 {

        Writer writer;
        OutputStream stream;

        @OnMessage(maxMessageSize = -1)
        public void handleMessage(final String message, Session session) throws IOException {
            //having 'boolean last' attrib would mean its partial and we would get deliveries.
            messagesReceived.incrementAndGet();
            BufferTortureTestBase.lastMessage = message;
            BufferTortureTestBase.messageLatch.countDown();
        }

        @OnOpen
        public void onOpen(final Session session, final EndpointConfig config) {
            upgradesAccepted.incrementAndGet();
        }
    }

    public static class CustomConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public boolean checkOrigin(String originHeaderValue) {
            BufferTortureTestBase.originCheckCalled.set(true);
            if(originHeaderValue.equals(BufferTortureTestBase.VALID_ORIGIN)) {
                return true;
            } else {
                return false;
            }
        }
    }
}
