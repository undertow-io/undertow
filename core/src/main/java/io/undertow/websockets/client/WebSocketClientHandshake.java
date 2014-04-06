package io.undertow.websockets.client;

import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.http.HandshakeChecker;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public abstract class WebSocketClientHandshake {

    protected final URI url;

    public static WebSocketClientHandshake create(final WebSocketVersion version, final URI uri) {
        return create(version, uri, null);
    }

    public static WebSocketClientHandshake create(final WebSocketVersion version, final URI uri, WebSocketClientNegotiation clientNegotiation) {
        switch (version) {
            case V13:
                return new WebSocket13ClientHandshake(uri, clientNegotiation);
        }
        throw new IllegalArgumentException();
    }

    public WebSocketClientHandshake(final URI url) {
        this.url = url;
    }

    public abstract WebSocketChannel createChannel(final StreamConnection channel, final String wsUri, final Pool<ByteBuffer> bufferPool);

    public abstract Map<String, String> createHeaders();

    public abstract HandshakeChecker handshakeChecker(final URI uri, final Map<String, String> requestHeaders);


}
