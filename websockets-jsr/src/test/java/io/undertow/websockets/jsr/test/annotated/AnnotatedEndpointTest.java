/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.jsr.test.annotated;

import java.net.URI;

import javax.servlet.DispatcherType;
import javax.websocket.Session;

import io.undertow.client.HttpClient;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.websockets.jsr.JsrWebSocketFilter;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.xnio.ByteBufferSlicePool;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
public class AnnotatedEndpointTest {

    private static ServerWebSocketContainer deployment;

    @BeforeClass
    public static void setup() throws Exception {

        final ServletContainer container = ServletContainer.Factory.newInstance();

        deployment = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE);
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(AnnotatedEndpointTest.class.getClassLoader())
                .setContextPath("/")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .addServletContextAttribute(javax.websocket.server.ServerContainer.class.getName(), deployment)
                .addFilter(new FilterInfo("filter", JsrWebSocketFilter.class))
                .addFilterUrlMapping("filter", "/*", DispatcherType.REQUEST);

        deployment.start(HttpClient.create(DefaultServer.getWorker(), OptionMap.EMPTY), new ByteBufferSlicePool(100, 1000));
        deployment.addEndpoint(MessageEndpoint.class);
        deployment.addEndpoint(AnnotatedClientEndpoint.class);
        deployment.addEndpoint(IncrementEndpoint.class);
        deployment.addEndpoint(EncodingEndpoint.class);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();


        DefaultServer.setRootHandler(manager.start());
    }

    @AfterClass
    public static void after() {
        deployment = null;
    }

    @org.junit.Test
    public void testStringOnMessage() throws Exception {
        final byte[] payload = "hello".getBytes();
        final ConcreteIoFuture latch = new ConcreteIoFuture();

        WebSocketTestClient client = new WebSocketTestClient(WebSocketVersion.V13, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/chat/Stuart"));
        client.connect();
        client.send(new TextWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, "hello Stuart".getBytes(), latch));
        latch.get();
        client.destroy();
    }

    @org.junit.Test
    public void testAnnotatedClientEndpoint() throws Exception {


        Session session = deployment.connectToServer(AnnotatedClientEndpoint.class, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/chat/Bob"));

        Assert.assertEquals("hi Bob", AnnotatedClientEndpoint.message());

        session.close();
        Assert.assertEquals("CLOSED", AnnotatedClientEndpoint.message());
    }


    @org.junit.Test
    public void testImplicitIntegerConversion() throws Exception {
        final byte[] payload = "12".getBytes();
        final ConcreteIoFuture latch = new ConcreteIoFuture();

        WebSocketTestClient client = new WebSocketTestClient(WebSocketVersion.V13, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/increment"));
        client.connect();
        client.send(new TextWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, "13".getBytes(), latch));
        latch.get();
        client.destroy();
    }


    @org.junit.Test
    public void testEncodingAndDecoding() throws Exception {
        final byte[] payload = "hello".getBytes();
        final ConcreteIoFuture latch = new ConcreteIoFuture();

        WebSocketTestClient client = new WebSocketTestClient(WebSocketVersion.V13, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/encoding/Stuart"));
        client.connect();
        client.send(new TextWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, "hello Stuart".getBytes(), latch));
        latch.get();
        client.destroy();
    }
}
