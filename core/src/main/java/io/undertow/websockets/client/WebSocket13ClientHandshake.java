package io.undertow.websockets.client;

import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.core.WebSocketUtils;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.version13.WebSocket13Channel;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.http.HandshakeChecker;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public class WebSocket13ClientHandshake extends WebSocketClientHandshake {

    public static final String MAGIC_NUMBER = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public WebSocket13ClientHandshake(final URI url) {
        super(url);
    }

    @Override
    public WebSocketChannel createChannel(final StreamConnection channel, final String wsUri, final Pool<ByteBuffer> bufferPool) {
        return new WebSocket13Channel(channel, bufferPool, wsUri, Collections.<String>emptySet(), true, false);
    }


    public Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(Headers.UPGRADE_STRING, "websocket");
        headers.put(Headers.CONNECTION_STRING, "upgrade");
        String key = createSecKey();
        headers.put(Headers.SEC_WEB_SOCKET_KEY_STRING, key);
        headers.put(Headers.SEC_WEB_SOCKET_VERSION_STRING, getVersion().toHttpHeaderValue());
        return headers;

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
    public HandshakeChecker handshakeChecker(final URI uri, final Map<String, String> requestHeaders) {
        final String sentKey = requestHeaders.get(Headers.SEC_WEB_SOCKET_KEY_STRING);
        return new HandshakeChecker() {
            @Override
            public void checkHandshake(Map<String, String> headers) throws IOException {
                String upgrade = headers.get(Headers.UPGRADE_STRING.toLowerCase());
                if (upgrade == null || !upgrade.toLowerCase().trim().equals("websocket")) {
                    throw WebSocketMessages.MESSAGES.noWebSocketUpgradeHeader();
                }
                String connHeader = headers.get(Headers.CONNECTION_STRING.toLowerCase());
                if (connHeader == null || !connHeader.toLowerCase().trim().equals("upgrade")) {
                    throw WebSocketMessages.MESSAGES.noWebSocketConnectionHeader();
                }
                String acceptKey = headers.get(Headers.SEC_WEB_SOCKET_ACCEPT_STRING.toLowerCase());
                final String dKey = solve(sentKey);
                if (!dKey.equals(acceptKey)) {
                    throw WebSocketMessages.MESSAGES.webSocketAcceptKeyMismatch(dKey, acceptKey);
                }
            }
        };
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
