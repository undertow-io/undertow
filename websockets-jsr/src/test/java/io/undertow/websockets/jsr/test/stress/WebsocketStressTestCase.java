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

package io.undertow.websockets.jsr.test.stress;

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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class WebsocketStressTestCase {

    public static final int NUM_THREADS = 100;
    public static final int NUM_REQUESTS = 1000;
    private static ServerWebSocketContainer deployment;

    private static WebSocketContainer defaultContainer = ContainerProvider.getWebSocketContainer();

    @BeforeClass
    public static void setup() throws Exception {

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(WebsocketStressTestCase.class.getClassLoader())
                .setContextPath("/ws")
                .setResourceManager(new TestResourceLoader(WebsocketStressTestCase.class))
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorker())
                                .addEndpoint(StressEndpoint.class)
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

        DefaultServer.setRootHandler(Handlers.path().addPrefixPath("/ws", manager.start()));
    }

    @AfterClass
    public static void after() {
        StressEndpoint.MESSAGES.clear();
        deployment = null;
    }

    @Test
    public void webSocketStringStressTestCase() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        try {
            List<CountDownLatch> latches = new ArrayList<>();
            for (int i = 0; i < NUM_THREADS; ++i) {
                final CountDownLatch latch = new CountDownLatch(1);
                latches.add(latch);
                final Session session = deployment.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig config) {
                    }

                    @Override
                    public void onClose(Session session, CloseReason closeReason) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Session session, Throwable thr) {
                        latch.countDown();
                    }
                }, null, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/ws/stress"));
                final int thread = i;
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            executor.submit(new SendRunnable(session, thread, executor));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

            }
            for (CountDownLatch future : latches) {
                future.await();
            }
        } finally {
            executor.shutdown();
        }
        for (int t = 0; t < NUM_THREADS; ++t) {
            for (int i = 0; i < NUM_REQUESTS; ++i) {
                String msg = "t-" + t + "-m-" + i;
                Assert.assertTrue(msg, StressEndpoint.MESSAGES.remove(msg));
            }
        }
        Assert.assertEquals(0, StressEndpoint.MESSAGES.size());
    }

    @Test
    public void websocketFragmentationStressTestCase() throws Exception {

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final CountDownLatch done = new CountDownLatch(1);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; ++i) {
            sb.append("message ");
            sb.append(i);
        }
        String toSend = sb.toString();

        final Session session = defaultContainer.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Partial<byte[]>() {
                    @Override
                    public void onMessage(byte[] bytes, boolean b) {
                        try {
                            out.write(bytes);
                        } catch (IOException e) {
                            e.printStackTrace();
                            done.countDown();
                        }
                        if (b) {
                            done.countDown();
                        }
                    }
                });
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                done.countDown();
            }

            @Override
            public void onError(Session session, Throwable thr) {
                thr.printStackTrace();
                done.countDown();
            }
        }, null, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/ws/stress"));

        OutputStream stream = session.getBasicRemote().getSendStream();
        for(int i = 0; i < toSend.length(); ++i) {
            stream.write(toSend.charAt(i));
            stream.flush();
        }
        stream.close();
        done.await(40, TimeUnit.SECONDS);
        Assert.assertEquals(toSend, new String(out.toByteArray()));

    }


    @ClientEndpoint
    private static class ClientEndpointImpl {
    }

    private static class SendRunnable implements Runnable {
        private final Session session;
        private final int thread;
        private final AtomicInteger count = new AtomicInteger();
        private final ExecutorService executor;
        final CountDownLatch latch = new CountDownLatch(1);

        SendRunnable(Session session, int thread, ExecutorService executor) {
            this.session = session;
            this.thread = thread;
            this.executor = executor;
        }

        @Override
        public void run() {
            session.getAsyncRemote().sendText("t-" + thread + "-m-" + count.get(), new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    if (!result.isOK()) {
                        try {
                            result.getException().printStackTrace();
                            session.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    if (count.incrementAndGet() != NUM_REQUESTS) {
                        executor.submit(SendRunnable.this);
                    } else {
                        executor.submit(new Runnable() {
                            @Override
                            public void run() {

                                session.getAsyncRemote().sendText("close");
                                try {
                                    latch.await();
                                } catch (InterruptedException e) {

                                }
                                try {
                                    session.close();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                }
            });
        }
    }
}
