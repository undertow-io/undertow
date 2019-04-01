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

package io.undertow.websockets.suspendresume;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.Handlers;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import io.undertow.websockets.jsr.test.TestMessagesReceivedInOrder;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class SuspendResumeTestCase {

    private static volatile ServerWebSocketContainer serverContainer;

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
                                .addListener(new WebSocketDeploymentInfo.ContainerReadyListener() {
                                    @Override
                                    public void ready(ServerWebSocketContainer c) {
                                        serverContainer = c;
                                    }
                                })
                                .addEndpoint(SuspendResumeEndpoint.class)
                )
                .setDeploymentName("servletContext.war");


        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();


        DefaultServer.setRootHandler(Handlers.path().addPrefixPath("/", manager.start()));
    }


    @Test
    public void testConnectionWaitsForMessageEnd() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> message = new AtomicReference<>();

        Session session = ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String m) {
                        message.set(m);
                        done.countDown();
                    }
                });
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                done.countDown();
            }

            @Override
            public void onError(Session session, Throwable thr) {
                if (message.get() == null) {
                    thr.printStackTrace();
                    message.set("error");
                    done.countDown();
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), new URI(DefaultServer.getDefaultServerURL() + "/"));
        Assert.assertTrue(session.isOpen());
        session.getBasicRemote().sendText("Hello World");
        Thread.sleep(500);
        serverContainer.pause(null);
        try {
            Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
            Assert.assertEquals("Hello World", message.get());
        } finally {
            serverContainer.resume();
        }
    }

    @Test
    public void testConnectionClosedOnPause() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> message = new AtomicReference<>();

        Session session = ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig config) {

            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                message.set("closed");
                done.countDown();
            }

            @Override
            public void onError(Session session, Throwable thr) {
                if (message.get() == null) {
                    thr.printStackTrace();
                    message.set("error");
                    done.countDown();
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), new URI(DefaultServer.getDefaultServerURL() + "/"));

        Assert.assertTrue(session.isOpen());
        Thread.sleep(500);
        serverContainer.pause(null);
        try {
            Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
            Assert.assertEquals("closed", message.get());
        } finally {
            serverContainer.resume();
        }
    }


    @Test
    public void testRejectWhenSuspended() throws Exception {
        try {
            serverContainer.pause(null);
            ContainerProvider.getWebSocketContainer().connectToServer(new Endpoint() {
                @Override
                public void onOpen(Session session, EndpointConfig config) {

                }
            }, new URI(DefaultServer.getDefaultServerURL() + "/"));
        } catch (IOException e) {
            //expected
        } finally {
            serverContainer.resume();
        }

    }
}
