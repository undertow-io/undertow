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
import javax.servlet.Filter;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerEndpointConfigurationBuilder;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.servlet.util.ConstructorInstanceFactory;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.websockets.jsr.ConfiguredServerEndpoint;
import io.undertow.websockets.jsr.JsrWebSocketFilter;
import io.undertow.websockets.jsr.ServletWebSocketContainer;
import io.undertow.websockets.jsr.annotated.AnnotatedEndpointFactory;
import io.undertow.websockets.jsr.test.JsrWebSocketServletTest;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
public class AnnotatedEndpointTest {

    @org.junit.Test
    public void testStringOnMessage() throws Exception {
        final byte[] payload = "hello".getBytes();
        final ConcreteIoFuture latch = new ConcreteIoFuture();


        final InstanceFactory<Endpoint> factory = AnnotatedEndpointFactory.create(AnnotatedTestEndpoint.class, new ConstructorInstanceFactory<>(AnnotatedTestEndpoint.class.getDeclaredConstructor()));

        final ServletWebSocketContainer webSocketContainer = new ServletWebSocketContainer(getConfiguredServerEndpoint(factory));

        final ServletContainer container = ServletContainer.Factory.newInstance();

        FilterInfo f = new FilterInfo("filter", JsrWebSocketFilter.class, new ImmediateInstanceFactory<Filter>(webSocketContainer.getFilter()));

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(JsrWebSocketServletTest.class.getClassLoader())
                .setContextPath("/")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .addFilter(f)
                .addFilterUrlMapping("filter", "/*", DispatcherType.REQUEST);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();

        DefaultServer.setRootHandler(manager.start());

        WebSocketTestClient client = new WebSocketTestClient(WebSocketVersion.V13, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/chat/Stuart"));
        client.connect();
        client.send(new TextWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, "hello Stuart".getBytes(), latch));
        latch.get();
        client.destroy();
    }


    private static ConfiguredServerEndpoint getConfiguredServerEndpoint(final InstanceFactory<Endpoint> factory) {
        return new ConfiguredServerEndpoint(ServerEndpointConfigurationBuilder.create(AnnotatedTestEndpoint.class, "/chat/{user}").build(), factory);
    }
}
