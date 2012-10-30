package io.undertow.websockets.version00;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import io.undertow.server.HttpServerExchange;
import io.undertow.test.utils.DefaultServer;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.server.WebSocketConnectionCallback;
import io.undertow.websockets.server.WebSocketProtocolHandshakeHandler;
import junit.framework.Assert;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.client.WebSocketClientFactory;
import org.eclipse.jetty.websocket.core.api.UpgradeResponse;
import org.eclipse.jetty.websocket.core.api.WebSocketConnection;
import org.eclipse.jetty.websocket.core.api.WebSocketException;
import org.eclipse.jetty.websocket.core.api.WebSocketListener;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleWebSocket00TestCase {




    @org.junit.Test
    public void testBasicConnect() throws Exception {
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler("", "", new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final HttpServerExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
            }
        }));

        WebSocketClientFactory factory = new WebSocketClientFactory();
        factory.start();
        WebSocketClient client = factory.newWebSocketClient(new WebSocketListener() {
            @Override
            public void onWebSocketBinary(final byte[] payload, final int offset, final int len) {

            }

            @Override
            public void onWebSocketClose(final int statusCode, final String reason) {

            }

            @Override
            public void onWebSocketConnect(final WebSocketConnection connection) {

            }

            @Override
            public void onWebSocketException(final WebSocketException error) {

            }

            @Override
            public void onWebSocketText(final String message) {

            }
        });
        FutureCallback <UpgradeResponse> future = client.connect(new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default")));
        future.get();
        Assert.assertTrue(connected.get());
    }

}
