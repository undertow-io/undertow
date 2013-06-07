package io.undertow.websockets.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;

import io.undertow.client.HttpClient;
import io.undertow.client.HttpClientCallback;
import io.undertow.client.HttpClientConnection;
import io.undertow.client.HttpClientRequest;
import io.undertow.client.HttpClientResponse;
import io.undertow.util.Methods;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;

/**
 * The Web socket client.
 *
 * @author Stuart Douglas
 */
public class WebSocketClient {


    public static IoFuture<WebSocketChannel> connect(HttpClient client, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version) {
        final FutureResult<WebSocketChannel> ioFuture = new FutureResult<WebSocketChannel>();
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
        return ioFuture.getIoFuture();
    }

    public static void connect(HttpClient client, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, final HttpClientCallback<WebSocketChannel> callback) {
        InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort());
        client.connect(address, optionMap, new HttpClientCallback<HttpClientConnection>() {
            @Override
            public void completed(final HttpClientConnection connection) {
                final WebSocketClientHandshake handshake = WebSocketClientHandshake.create(WebSocketVersion.V13, uri);
                HttpClientRequest request = connection.createRequest(Methods.GET, uri);
                handshake.setupRequest(request);
                request.writeRequest(new HttpClientCallback<HttpClientResponse>() {
                    @Override
                    public void completed(final HttpClientResponse result) {
                        try {
                            handshake.verifyResponse(uri, result, connection, callback);
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

            @Override
            public void failed(final IOException e) {
                callback.failed(e);
            }
        });

    }

    private WebSocketClient() {

    }
}
