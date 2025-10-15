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

import io.undertow.Handlers;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.FlexBase64;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.FutureResult;

import jakarta.servlet.ServletException;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertTrue;

@RunWith(DefaultServer.class)
@HttpOneOnly
public class TestMessagesReceivedInOrder {

    private static final int MESSAGES = 1000;

    private static final List<Throwable> stacks = new CopyOnWriteArrayList<>();
    private static DeploymentManager deploymentManager;

    @BeforeClass
    public static void setup() throws ServletException {

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(TestMessagesReceivedInOrder.class.getClassLoader())
                .setContextPath("/")
                .setResourceManager(new TestResourceLoader(TestMessagesReceivedInOrder.class))
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorkerSupplier())
                                .addEndpoint(EchoSocket.class)
                )
                .setDeploymentName("servletContext.war");


        deploymentManager = container.addDeployment(builder);
        deploymentManager.deploy();

        DefaultServer.setRootHandler(Handlers.path().addPrefixPath("/", deploymentManager.start()));
    }

    @AfterClass
    public static void cleanup() throws ServletException {
        if (deploymentManager != null) {
            deploymentManager.stop();
            deploymentManager.undeploy();
        }
    }

    @Test
    public void testMessagesReceivedInOrder() throws Exception {
        stacks.clear();
        EchoSocket.receivedEchos = new FutureResult<>();
        final ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> error = new AtomicReference<>();
        ContainerProvider.getWebSocketContainer()
                .connectToServer(new Endpoint() {
                                     @Override
                                     public void onOpen(final Session session, EndpointConfig endpointConfig) {

                                         try {
                                             RemoteEndpoint.Basic rem = session.getBasicRemote();
                                             List<String> messages = new ArrayList<>();
                                             for (int i = 0; i < MESSAGES; i++) {
                                                 byte[] data = new byte[2048];
                                                 (new Random()).nextBytes(data);
                                                 String crc = md5(data);
                                                 rem.sendBinary(ByteBuffer.wrap(data));
                                                 messages.add(crc);
                                             }

                                             List<String> received = EchoSocket.receivedEchos.getIoFuture().get();
                                             StringBuilder sb = new StringBuilder();
                                             boolean fail = false;
                                             for (int i = 0; i < messages.size(); i++) {
                                                 if (received.size() <= i) {
                                                     fail = true;
                                                     sb.append(i + ": should be " + messages.get(i) + " but is empty.");
                                                 } else {
                                                     if (!messages.get(i).equals(received.get(i))) {
                                                         fail = true;
                                                         sb.append(i + ": should be " + messages.get(i) + " but is " + received.get(i) + " (but found at " + received.indexOf(messages.get(i)) + ").");
                                                     }
                                                 }
                                             }
                                             if(fail) {
                                                 error.set(sb.toString());
                                             }
                                             done.countDown();

                                         } catch (Throwable t) {
                                             t.printStackTrace();
                                         }
                                     }
                                 }, clientEndpointConfig, new URI(DefaultServer.getDefaultServerURL() + "/webSocket")
                );
        assertTrue(done.await(30, TimeUnit.SECONDS));
        if(error.get() != null) {
            Assert.fail(error.get());
        }
    }

    @ServerEndpoint("/webSocket")
    public static class EchoSocket {
        private final List<String> echos = new CopyOnWriteArrayList<>();
        public static volatile FutureResult<List<String>> receivedEchos = new FutureResult<>();

        @OnMessage
        public void onMessage(ByteBuffer dataBuffer, Session session) throws IOException {
            byte[] hd = new byte[dataBuffer.remaining()];
            dataBuffer.get(hd);
            String hash = md5(hd);
            echos.add(hash);
            stacks.add(new RuntimeException());
            if (echos.size() == MESSAGES) {
                receivedEchos.setResult(echos);
            }
            session.getBasicRemote().sendBinary(dataBuffer);
        }

    }

    private static String md5(byte[] buffer) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(buffer);
            byte[] digest = md.digest();
            return new String(FlexBase64.encodeBytes(digest, 0, digest.length, false));
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            throw new InternalError("MD5 not supported on this platform");
        }
    }
}
