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

package io.undertow.websockets.jsr.test.extension;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.StringWriteChannelListener;
import io.undertow.websockets.WebSocketExtension;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.client.WebSocketClientNegotiation;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.extensions.DebugExtensionsHeaderHandler;
import io.undertow.websockets.extensions.ExtensionHandshake;
import io.undertow.websockets.extensions.PerMessageDeflateHandshake;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import io.undertow.websockets.jsr.test.BinaryEndpointTest;
import io.undertow.websockets.jsr.test.autobahn.AutobahnAnnotatedEndpoint;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * A test class for WebSocket client scenarios with extensions.
 *
 * @author Lucas Ponce
 */
@HttpOneOnly
@RunWith(DefaultServer.class)
public class JsrWebsocketExtensionTestCase {

    public static final int MSG_COUNT = 1000;
    private static volatile DebugExtensionsHeaderHandler debug;
    private static DeploymentManager deploymentManager;

    @BeforeClass
    public static void setup() throws Exception {

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(BinaryEndpointTest.class.getClassLoader())
                .setContextPath("/")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setDispatchToWorkerThread(true)
                                .setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorkerSupplier())
                                .addExtension(new PerMessageDeflateHandshake())
                        .addEndpoint(AutobahnAnnotatedEndpoint.class)
                )
                .setDeploymentName("servletContext.war");


        deploymentManager = container.addDeployment(builder);
        deploymentManager.deploy();

        debug = new DebugExtensionsHeaderHandler(deploymentManager.start());
        DefaultServer.setRootHandler(debug);
    }

    @AfterClass
    public static void cleanup() throws ServletException {
        if (deploymentManager != null) {
            deploymentManager.stop();
            deploymentManager.undeploy();
        }
    }

    @Test
    public void testLongTextMessage() throws Exception {

        final String SEC_WEBSOCKET_EXTENSIONS = "permessage-deflate; client_no_context_takeover; client_max_window_bits";
        List<WebSocketExtension> extensionsList = WebSocketExtension.parse(SEC_WEBSOCKET_EXTENSIONS);

        final WebSocketClientNegotiation negotiation = new WebSocketClientNegotiation(null, extensionsList);

        Set<ExtensionHandshake> extensionHandshakes = new HashSet<>();
        extensionHandshakes.add(new PerMessageDeflateHandshake(true));

        final WebSocketChannel clientChannel = WebSocketClient.connect(DefaultServer.getWorker(), null, DefaultServer.getBufferPool(), OptionMap.EMPTY, new URI(DefaultServer.getDefaultServerURL()), WebSocketVersion.V13, negotiation, extensionHandshakes).get();

        final LinkedBlockingDeque<String> resultQueue  = new LinkedBlockingDeque<>();

        clientChannel.getReceiveSetter().set(new ClientTextReceiveListener(resultQueue));
        clientChannel.resumeReceives();

        int LONG_MSG = 125 * 1024;
        StringBuilder longMsg = new StringBuilder(LONG_MSG);

        for (int i = 0; i < LONG_MSG; i++) {
            longMsg.append(Integer.toString(i).charAt(0));
        }

        String message = longMsg.toString();
        for(int j = 0; j < MSG_COUNT; ++ j) {

            WebSockets.sendTextBlocking(message, clientChannel);
            String res = resultQueue.poll(10, TimeUnit.SECONDS);
            assertEquals(message, res);
        }

        clientChannel.sendClose();

    }

    @Test
    public void testExtensionsHeaders() throws Exception {


        final String SEC_WEBSOCKET_EXTENSIONS = "permessage-deflate; client_no_context_takeover; client_max_window_bits";
        final String SEC_WEBSOCKET_EXTENSIONS_EXPECTED = "[permessage-deflate; client_no_context_takeover]";  // List format
        List<WebSocketExtension> extensions = WebSocketExtension.parse(SEC_WEBSOCKET_EXTENSIONS);

        final WebSocketClientNegotiation negotiation = new WebSocketClientNegotiation(null, extensions);

        Set<ExtensionHandshake> extensionHandshakes = new HashSet<>();
        extensionHandshakes.add(new PerMessageDeflateHandshake(true));

        final WebSocketChannel clientChannel = WebSocketClient.connect(DefaultServer.getWorker(), null, DefaultServer.getBufferPool(), OptionMap.EMPTY, new URI(DefaultServer.getDefaultServerURL()), WebSocketVersion.V13, negotiation, extensionHandshakes).get();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();

        clientChannel.getReceiveSetter().set(new ClientBinaryReceiveListener(result, latch));
        clientChannel.resumeReceives();

        StreamSinkFrameChannel sendChannel = clientChannel.send(WebSocketFrameType.TEXT);
        new StringWriteChannelListener("Hello, World!").setup(sendChannel);

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals("Hello, World!", result.get());
        clientChannel.sendClose();

        assertEquals(SEC_WEBSOCKET_EXTENSIONS_EXPECTED, debug.getResponseExtensions().toString());
    }

    private static class ClientTextReceiveListener extends AbstractReceiveListener {
        private final LinkedBlockingDeque<String> resultQueue;

        ClientTextReceiveListener(LinkedBlockingDeque<String> resultQueue) {
            this.resultQueue = resultQueue;
        }

        @Override
        protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message)  {
            String data = message.getData();
            // WebSocketLogger.ROOT_LOGGER.info("onFullTextMessage() - Client - Received: " + data.getBytes().length + " bytes.");
            resultQueue.addLast(data);
        }

        @Override
        protected void onFullCloseMessage(WebSocketChannel channel, BufferedBinaryMessage message) {
            message.getData().close();
            WebSocketLogger.ROOT_LOGGER.info("onFullCloseMessage");
        }

        @Override
        protected void onError(WebSocketChannel channel, Throwable error) {
            WebSocketLogger.ROOT_LOGGER.info("onError");
            super.onError(channel, error);
            error.printStackTrace();
            resultQueue.add("FAILED " + error);
        }

    }

    private static class ClientBinaryReceiveListener extends AbstractReceiveListener {
        private final AtomicReference<String> result;
        private final CountDownLatch latch;

        ClientBinaryReceiveListener(AtomicReference<String> result, CountDownLatch latch) {
            this.result = result;
            this.latch = latch;
        }

        @Override
        protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
            String data = message.getData();
            WebSocketLogger.ROOT_LOGGER.info("onFullTextMessage - Client - Received: " + data.getBytes().length + " bytes . Data: " + data);
            result.set(data);
            latch.countDown();
        }

        @Override
        protected void onFullCloseMessage(WebSocketChannel channel, BufferedBinaryMessage message) {
            message.getData().close();
            WebSocketLogger.ROOT_LOGGER.info("onFullCloseMessage");
        }

        @Override
        protected void onError(WebSocketChannel channel, Throwable error) {
            WebSocketLogger.ROOT_LOGGER.info("onError");
            super.onError(channel, error);
            error.printStackTrace();
            latch.countDown();
        }

    }
}
