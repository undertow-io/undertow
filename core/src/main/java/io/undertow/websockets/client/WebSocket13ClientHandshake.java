package io.undertow.websockets.client;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;

import io.undertow.client.HttpClientConnection;
import io.undertow.client.HttpClientRequest;
import io.undertow.client.HttpClientResponse;
import io.undertow.util.AttachmentKey;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.core.WebSocketUtils;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.version13.WebSocket13Channel;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author Stuart Douglas
 */
public class WebSocket13ClientHandshake extends WebSocketClientHandshake {

    public static final String MAGIC_NUMBER = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final AttachmentKey<String> KEY = AttachmentKey.create(String.class);

    public WebSocket13ClientHandshake(final URI url) {
        super(url);
    }

    @Override
    public WebSocketChannel createChannel(final ConnectedStreamChannel channel, final String wsUri, final Pool<ByteBuffer> bufferPool) {
        return new WebSocket13Channel(channel, bufferPool, wsUri, Collections.<String>emptySet(), true, false);
    }


    @Override
    public HttpClientRequest createRequest(final HttpClientConnection connection) {
        HttpClientRequest result = connection.createRequest(Methods.GET, url);
        result.getRequestHeaders().put(Headers.UPGRADE, "websocket");
        result.getRequestHeaders().put(Headers.CONNECTION, "upgrade");
        String key = createSecKey();
        connection.putAttachment(KEY, key);
        result.getRequestHeaders().put(Headers.SEC_WEB_SOCKET_KEY, key);
        result.getRequestHeaders().put(Headers.SEC_WEB_SOCKET_VERSION, getVersion().toHttpHeaderValue());

        return result;
    }

    protected String createSecKey() {
        SecureRandom random = new SecureRandom();
        byte[] data = new byte[16];
        for (int i = 0; i < 4; ++i) {
            int val = random.nextInt();
            data[i * 4] = (byte) val;
            data[i * 4 + 1] = (byte) ((val >> 8) & 0xFF);
            data[i * 4 + 2] = (byte) ((val >> 16) & 0xFF);
            data[i * 4 + 3] = (byte) ((val >> 24) & 0xFF);
        }
        return FlexBase64.encodeString(data, false);
    }

    @Override
    public void validateResponse(final HttpClientConnection connection, final HttpClientResponse response) throws IOException {
        String upgrade = response.getResponseHeaders().getFirst(Headers.UPGRADE);
        if (upgrade == null || !upgrade.toLowerCase().trim().equals("websocket")) {
            throw WebSocketMessages.MESSAGES.noWebSocketUpgradeHeader();
        }
        String connHeader = response.getResponseHeaders().getFirst(Headers.CONNECTION);
        if (connection == null || !connHeader.toLowerCase().trim().equals("upgrade")) {
            throw WebSocketMessages.MESSAGES.noWebSocketConnectionHeader();
        }
        String acceptKey = response.getResponseHeaders().getFirst(Headers.SEC_WEB_SOCKET_ACCEPT);
        String sentKey = connection.getAttachment(KEY);
        final String dKey = solve(sentKey);
        if (!dKey.equals(acceptKey)) {
            throw WebSocketMessages.MESSAGES.webSocketAcceptKeyMismatch(dKey, acceptKey);
        }

    }

    protected final String solve(final String nonceBase64) {
        try {
            final String concat = nonceBase64 + MAGIC_NUMBER;
            final MessageDigest digest = MessageDigest.getInstance("SHA1");

            digest.update(concat.getBytes(WebSocketUtils.UTF_8));
            final byte[] bytes = digest.digest();
            return FlexBase64.encodeString(bytes, false);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public WebSocketVersion getVersion() {
        return WebSocketVersion.V13;
    }
}
