package io.undertow.websockets.client;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;

import io.undertow.client.HttpClientCallback;
import io.undertow.client.HttpClientConnection;
import io.undertow.client.HttpClientRequest;
import io.undertow.client.HttpClientResponse;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author Stuart Douglas
 */
public abstract class WebSocketClientHandshake{

    protected final URI url;

    public static WebSocketClientHandshake create(final WebSocketVersion version, final URI uri) {
        switch (version) {
            case V13:
                return new WebSocket13ClientHandshake(uri);
        }
        throw new IllegalArgumentException();
    }

    public WebSocketClientHandshake(final URI url) {
        this.url = url;
    }

    public abstract WebSocketChannel createChannel(final ConnectedStreamChannel channel, final String wsUri, final Pool<ByteBuffer> bufferPool);

    public abstract void setupRequest(final HttpClientRequest request);

    public abstract void verifyResponse(final URI uri, final HttpClientResponse response, final HttpClientConnection connection, final HttpClientCallback<WebSocketChannel> callback) throws IOException;


}
