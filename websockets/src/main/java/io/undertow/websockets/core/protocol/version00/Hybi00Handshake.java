/*
 * Copyright 2012 JBoss, by Red Hat, Inc
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

package io.undertow.websockets.core.protocol.version00;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author Mike Brock
 */
public class Hybi00Handshake extends Handshake {
    private static final Pattern PATTERN = Pattern.compile("[^0-9]");

    public Hybi00Handshake() {
        super(WebSocketVersion.V00, "MD5", null, Collections.<String>emptySet());
    }

    public Hybi00Handshake(final Set<String> subprotocols) {
        super(WebSocketVersion.V00, "MD5", null, subprotocols);
    }

    @Override
    protected void handshakeInternal(final WebSocketHttpExchange exchange) {

        String origin = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_ORIGIN_STRING);
        if (origin != null) {
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_ORIGIN_STRING, origin);
        }

        exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_LOCATION_STRING, getWebSocketLocation(exchange));

        String protocol = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING);
        if (protocol != null) {
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING, protocol);
        }

        // Calculate the answer of the challenge.
        final String key1 = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY1_STRING);
        final String key2 = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY2_STRING);

        exchange.readRequestData(new WebSocketHttpExchange.ReadCallback() {
            @Override
            public void onRead(final WebSocketHttpExchange exchange, final byte[] data) {
                byte[] key3 = data;

                final byte[] solution = solve(getHashAlgorithm(), key1, key2, key3);
                performUpgrade(exchange, solution);
            }

            @Override
            public void error(final WebSocketHttpExchange exchange, final IOException exception) {
                exchange.close();
            }
        });

    }

    @Override
    public boolean matches(final WebSocketHttpExchange exchange) {
        return exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY1_STRING) != null &&
                exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY2_STRING) != null;
    }

    @Override
    public WebSocketChannel createChannel(WebSocketHttpExchange exchange, final ConnectedStreamChannel channel, final Pool<ByteBuffer> pool) {
        return new WebSocket00Channel(channel, pool, getWebSocketLocation(exchange), subprotocols);
    }

    protected static byte[] solve(final String hashAlgorithm, String encodedKey1, String encodedKey2, byte[] key3) {
        return solve(hashAlgorithm, decodeKey(encodedKey1), decodeKey(encodedKey2), key3);
    }

    protected static byte[] solve(final String hashAlgorithm, long key1, long key2, byte[] key3) {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);

        buffer.putInt((int) key1);
        buffer.putInt((int) key2);
        buffer.put(key3);
        buffer.rewind();

        try {
            final MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            digest.update(buffer);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("error generating hash", e);
        }
    }

    protected static long decodeKey(final String encoded) {
        final int len = encoded.length();
        int numSpaces = 0;

        for (int i = 0; i < len; ++i) {
            if (encoded.charAt(i) == ' ') {
                ++numSpaces;
            }
        }
        final String digits = PATTERN.matcher(encoded).replaceAll("");
        final long product = Long.parseLong(digits);
        return product / numSpaces;
    }
}
