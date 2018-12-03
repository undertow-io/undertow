/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.websockets.jsr.handshake;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.netty.handler.codec.http.websocketx.WebSocket07FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket07FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.undertow.util.Headers;
import io.undertow.websockets.jsr.WebSocketVersion;

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
    public boolean matches(HttpServletRequest request, HttpServletResponse response) {
        if (request.getHeader(Headers.SEC_WEB_SOCKET_KEY_STRING) != null &&
                request.getHeader(Headers.SEC_WEB_SOCKET_VERSION_STRING) != null) {
            return request.getHeader(Headers.SEC_WEB_SOCKET_VERSION_STRING)
                    .equals(getVersion().toHttpHeaderValue());
        }
        return false;
    }

    @Override
    protected WebSocketFrameDecoder newWebsocketDecoder() {
        return new WebSocket07FrameDecoder(true, true, -1);
    }

    @Override
    protected WebSocketFrameEncoder newWebSocketEncoder() {
        return new WebSocket07FrameEncoder(false);
    }

    protected void handshakeInternal(final HttpServletRequest request, HttpServletResponse response) {

        String origin = request.getHeader(Headers.SEC_WEB_SOCKET_ORIGIN_STRING);
        if (origin != null) {
            response.setHeader(Headers.SEC_WEB_SOCKET_ORIGIN_STRING, origin);
        }
        selectSubprotocol(request, response);
        selectExtensions(request, response);
        response.setHeader(Headers.SEC_WEB_SOCKET_LOCATION_STRING, getWebSocketLocation(request, response));

        final String key = request.getHeader(Headers.SEC_WEB_SOCKET_KEY_STRING);
        try {
            final String solution = solve(key);
            response.setHeader(Headers.SEC_WEB_SOCKET_ACCEPT_STRING, solution);
            performUpgrade(request, response);
        } catch (NoSuchAlgorithmException e) {
            return;
        }

    }

    protected final String solve(final String nonceBase64) throws NoSuchAlgorithmException {
        final String concat = nonceBase64.trim() + getMagicNumber();
        final MessageDigest digest = MessageDigest.getInstance(getHashAlgorithm());
        digest.update(concat.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeBytes(digest.digest()).trim();
    }

}
