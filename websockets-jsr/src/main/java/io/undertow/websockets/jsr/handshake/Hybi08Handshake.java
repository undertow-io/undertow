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

import java.util.Collections;
import java.util.Set;

import io.netty.handler.codec.http.websocketx.WebSocket08FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.undertow.websockets.jsr.WebSocketVersion;

/**
 * The handshaking protocol implementation for Hybi-07, which is identical to Hybi-08, and thus is just a thin
 * subclass of {@link Hybi07Handshake} that sets a different version number.
 *
 * @author Mike Brock
 */
public class Hybi08Handshake extends Hybi07Handshake {
    public Hybi08Handshake() {
        super(WebSocketVersion.V08, Collections.<String>emptySet(), false);
    }

    public Hybi08Handshake(Set<String> subprotocols, boolean allowExtensions) {
        super(WebSocketVersion.V08, subprotocols, allowExtensions);
    }

    @Override
    protected WebSocketFrameDecoder newWebsocketDecoder() {
        return new WebSocket08FrameDecoder(true, true, -1);
    }

    @Override
    protected WebSocketFrameEncoder newWebSocketEncoder() {
        return new WebSocket08FrameEncoder(false);
    }
}
