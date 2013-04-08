package io.undertow.websockets.jsr.test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfigurationBuilder;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.websockets.jsr.ConfiguredServerEndpoint;
import io.undertow.websockets.jsr.JsrWebSocketFilter;
import io.undertow.websockets.jsr.ServletWebSocketContainer;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.junit.Assert;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class JsrWebSocketServletTest {

    @org.junit.Test
    public void testBinaryWithByteBuffer() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<Throwable>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final ConcreteIoFuture latch = new ConcreteIoFuture();

        final InstanceFactory<Endpoint> factory = new InstanceFactory<Endpoint>() {
            @Override
            public InstanceHandle<Endpoint> createInstance() throws InstantiationException {
                return new ImmediateInstanceHandle<Endpoint>(new Endpoint() {
                    @Override
                    public void onOpen(final Session session, EndpointConfiguration config) {
                        connected.set(true);
                        session.addMessageHandler(new MessageHandler.Basic<byte[]>() {
                            @Override
                            public void onMessage(byte[] message) {
                                try {
                                    OutputStream out = session.getBasicRemote().getSendStream();
                                    out.write(message);
                                    out.flush();
                                    out.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    cause.set(e);
                                    latch.setException(e);
                                }
                            }
                        });
                    }
                });
            }
        };

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

        WebSocketTestClient client = new WebSocketTestClient(WebSocketVersion.V13, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        latch.get();
        Assert.assertNull(cause.get());
        client.destroy();
    }

    private static ConfiguredServerEndpoint getConfiguredServerEndpoint(final InstanceFactory<Endpoint> factory) {
        return new ConfiguredServerEndpoint(ServerEndpointConfigurationBuilder.create(MyEndpoint.class, "/").build(), factory);
    }

    private static final class MyEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfiguration config) {
            throw new UnsupportedOperationException();
        }
    }
}
