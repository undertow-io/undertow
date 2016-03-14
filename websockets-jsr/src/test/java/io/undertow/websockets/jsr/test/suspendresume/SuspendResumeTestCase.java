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

package io.undertow.websockets.jsr.test.suspendresume;

import io.undertow.Handlers;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import io.undertow.websockets.jsr.test.TestMessagesReceivedInOrder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.http.UpgradeFailedException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
                                .setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorker())
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
        WebSocketChannel channel = WebSocketClient.connectionBuilder(DefaultServer.getWorker(), DefaultServer.getBufferPool(), new URI(DefaultServer.getDefaultServerURL() + "/"))
                .connect().get();
        channel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage msg) throws IOException {
                message.set(msg.getData());
                done.countDown();
            }

            @Override
            protected void onError(WebSocketChannel channel, Throwable error) {
                error.printStackTrace();
                message.set("error");
                done.countDown();
            }

            @Override
            protected void onFullCloseMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
                message.getData().free();
                done.countDown();
            }
        });
        channel.resumeReceives();
        Assert.assertTrue(channel.isOpen());
        WebSockets.sendText("Hello World", channel, null);
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
        WebSocketChannel channel = WebSocketClient.connectionBuilder(DefaultServer.getWorker(), DefaultServer.getBufferPool(), new URI(DefaultServer.getDefaultServerURL() + "/"))
                .connect().get();
        channel.getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
            @Override
            public void handleEvent(WebSocketChannel channel) {
                try {
                    StreamSourceFrameChannel res = channel.receive();
                    if(res == null) {
                        return;
                    }
                    if (res.getType() == WebSocketFrameType.CLOSE) {
                        message.set("closed");
                        done.countDown();
                    }
                    Channels.drain(res, Long.MAX_VALUE);
                } catch (IOException e) {
                    if(message.get() == null) {
                        e.printStackTrace();
                        message.set("error");
                        done.countDown();
                    }
                }
            }
        });
        channel.resumeReceives();
        Assert.assertTrue(channel.isOpen());
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
            WebSocketChannel channel = WebSocketClient.connectionBuilder(DefaultServer.getWorker(), DefaultServer.getBufferPool(), new URI(DefaultServer.getDefaultServerURL() + "/"))
                    .connect().get();
            IoUtils.safeClose(channel);
            Assert.fail();
        } catch (UpgradeFailedException e) {
            //expected
        } finally {
            serverContainer.resume();
        }

    }
}
