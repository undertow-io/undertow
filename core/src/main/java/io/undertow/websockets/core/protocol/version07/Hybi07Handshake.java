/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.websockets.core.protocol.version07;


import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.extensions.CompositeExtensionFunction;
import io.undertow.websockets.extensions.ExtensionFunction;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.IoUtils;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The handshaking protocol implementation for Hybi-07.
 *
 * @author Mike Brock
 */
public class Hybi07Handshake extends Handshake {

    public static final String MAGIC_NUMBER = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    protected Hybi07Handshake(final WebSocketVersion version, final Set<String> subprotocols, boolean allowExtensions) {
        super(version, "SHA1", MAGIC_NUMBER, subprotocols);
        this.allowExtensions = allowExtensions;
    }

    public Hybi07Handshake(final Set<String> subprotocols, boolean allowExtensions) {
        this(WebSocketVersion.V07, subprotocols, allowExtensions);
    }

    public Hybi07Handshake() {
        this(WebSocketVersion.V07, Collections.<String>emptySet(), false);
    }

    @Override
    public boolean matches(final WebSocketHttpExchange exchange) {
        if (exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY_STRING) != null &&
                exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_VERSION_STRING) != null) {
            return exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_VERSION_STRING)
                    .equals(getVersion().toHttpHeaderValue());
        }
        return false;
    }

    protected void handshakeInternal(final WebSocketHttpExchange exchange) {

        String origin = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_ORIGIN_STRING);
        if (origin != null) {
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_ORIGIN_STRING, origin);
        }
        selectSubprotocol(exchange);
        selectExtensions(exchange);
        exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_LOCATION_STRING, getWebSocketLocation(exchange));

        final String key = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY_STRING);
        try {
            final String solution = solve(key);
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_ACCEPT_STRING, solution);
            performUpgrade(exchange);
        } catch (NoSuchAlgorithmException e) {
            IoUtils.safeClose(exchange);
            exchange.endExchange();
            return;
        }

    }

    protected final String solve(final String nonceBase64) throws NoSuchAlgorithmException {
        final String concat = nonceBase64.trim() + getMagicNumber();
        final MessageDigest digest = MessageDigest.getInstance(getHashAlgorithm());
        digest.update(concat.getBytes(StandardCharsets.UTF_8));
        return  Base64.encodeBytes(digest.digest()).trim();
    }

    @Override
    public WebSocketChannel createChannel(WebSocketHttpExchange exchange, final StreamConnection channel, final ByteBufferPool pool) {
        List<ExtensionFunction> extensionFunctions = initExtensions(exchange);
        return new WebSocket07Channel(channel, pool, getWebSocketLocation(exchange), exchange.getResponseHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING), false, !extensionFunctions.isEmpty(), CompositeExtensionFunction.compose(extensionFunctions), exchange.getPeerConnections(), exchange.getOptions());
    }
}
