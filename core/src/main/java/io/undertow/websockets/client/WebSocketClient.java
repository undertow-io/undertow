package io.undertow.websockets.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;

import io.undertow.client.HttpClient;
import io.undertow.client.HttpClientCallback;
import io.undertow.client.HttpClientConnection;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * The Web socket client.
 *
 * @author Stuart Douglas
 */
public class WebSocketClient {


    public static IoFuture<WebSocketChannel> connect(HttpClient client, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version) {
        final ConcreteIoFuture<WebSocketChannel> ioFuture = new ConcreteIoFuture<>();
        connect(client, bufferPool, optionMap, uri, version, new HttpClientCallback<WebSocketChannel>() {
            @Override
            public void completed(final WebSocketChannel result) {
                ioFuture.setResult(result);
            }

            @Override
            public void failed(final IOException e) {
                ioFuture.setException(e);
            }
        });
        return ioFuture;
    }

    public static void connect(HttpClient client, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, final HttpClientCallback<WebSocketChannel> callback) {
        InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort());
        client.connect(address, optionMap, new HttpClientCallback<HttpClientConnection>() {
            @Override
            public void completed(final HttpClientConnection connection) {
                try {
                    final WebSocketClientHandshake handshake = WebSocketClientHandshake.create(WebSocketVersion.V13, uri);
                    connection.performUpgrade(handshake, OptionMap.EMPTY, new HttpClientCallback<ConnectedStreamChannel>() {
                        @Override
                        public void completed(final ConnectedStreamChannel result) {
                            WebSocketChannel webSocketChannel = handshake.createChannel(result, uri.toString(), bufferPool);
                            callback.completed(webSocketChannel);
                        }

                        @Override
                        public void failed(final IOException e) {
                            callback.failed(e);
                        }
                    });
                } catch (IOException e) {
                    callback.failed(e);
                }
            }

            @Override
            public void failed(final IOException e) {
                callback.failed(e);
            }
        });

    }
}
