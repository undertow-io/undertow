package io.undertow.servlet.test.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.Servlet;

import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.servlet.websockets.WebSocketServlet;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.NetworkUtils;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.StringWriteChannelListener;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.apache.james.mime4j.util.CharsetUtil;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;

/**
 * @author Stuart Douglas
 */
@AjpIgnore
@RunWith(DefaultServer.class)
public class WebSocketServletTest {


    @Test
    public void testText() throws Exception {


        final AtomicBoolean connected = new AtomicBoolean(false);

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentUtils.setupServlet(new ServletInfo("websocket", WebSocketServlet.class,
                new ImmediateInstanceFactory<Servlet>(new WebSocketServlet(new WebSocketConnectionCallback() {
                    @Override
                    public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                        connected.set(true);
                        channel.getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
                            @Override
                            public void handleEvent(final WebSocketChannel channel) {
                                try {
                                    final StreamSourceFrameChannel ws = channel.receive();
                                    if (ws == null) {
                                        return;
                                    }
                                    new StringReadChannelListener(exchange.getBufferPool()) {
                                        @Override
                                        protected void stringDone(final String string) {
                                            try {
                                                if (string.equals("hello")) {
                                                    new StringWriteChannelListener("world")
                                                            .setup(channel.send(WebSocketFrameType.TEXT, "world".length()));
                                                } else {
                                                    new StringWriteChannelListener(string)
                                                            .setup(channel.send(WebSocketFrameType.TEXT, string.length()));
                                                }
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                                throw new RuntimeException(e);
                                            }
                                        }

                                        @Override
                                        protected void error(final IOException e) {
                                            try {
                                                e.printStackTrace();
                                                new StringWriteChannelListener("ERROR")
                                                        .setup(channel.send(WebSocketFrameType.TEXT, "ERROR".length()));
                                            } catch (IOException ex) {
                                                ex.printStackTrace();
                                                throw new RuntimeException(ex);
                                            }
                                        }
                                    }.setup(ws);
                                    channel.getReceiveSetter().set(null);

                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                        channel.resumeReceives();
                    }
                })))
                .addMapping("/*"));

        final FutureResult latch = new FutureResult<>();
        WebSocketTestClient client = new WebSocketTestClient(org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion.V13, new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ":" + DefaultServer.getHostPort("default") + "/servletContext"));
        client.connect();
        client.send(new TextWebSocketFrame(ChannelBuffers.copiedBuffer("hello", CharsetUtil.US_ASCII)), new FrameChecker(TextWebSocketFrame.class, "world".getBytes(CharsetUtil.US_ASCII), latch));
        latch.getIoFuture().get();
        client.destroy();
    }
}
