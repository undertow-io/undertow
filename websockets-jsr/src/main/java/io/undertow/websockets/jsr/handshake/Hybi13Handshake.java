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

import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.undertow.util.Headers;
import io.undertow.websockets.jsr.WebSocketVersion;

/**
 * The handshaking protocol implementation for Hybi-13.
 *
 * @author Mike Brock
 * @author Stuart Douglas
 */
public class Hybi13Handshake extends Hybi07Handshake {
    public Hybi13Handshake() {
        super(WebSocketVersion.V13, Collections.<String>emptySet(), false);
    }

    public Hybi13Handshake(Set<String> subprotocols, boolean allowExtensions) {
        super(WebSocketVersion.V13, subprotocols, allowExtensions);
    }

    @Override
    protected void handshakeInternal(final HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader(Headers.ORIGIN_STRING);
        if (origin != null) {
            response.setHeader(Headers.ORIGIN_STRING, origin);
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

    @Override
    protected WebSocketFrameDecoder newWebsocketDecoder() {
        return new WebSocket13FrameDecoder(true, allowExtensions, -1, false);
    }

    @Override
    protected WebSocketFrameEncoder newWebSocketEncoder() {
        return new WebSocket13FrameEncoder(false);
    }
}
