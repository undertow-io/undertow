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

package io.undertow.websockets.protocol.version08;

import java.util.Collections;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketVersion;
import io.undertow.websockets.protocol.version07.Hybi07Handshake;

/**
 * The handshaking protocol impelemtation for Hybi-07, which is identical to Hybi-08, and thus is just a thin
 * subclass of {@link Hybi07Handshake} that sets a different version number.
 *
 * @author Mike Brock
 */
public class Hybi08Handshake extends Hybi07Handshake {
    public Hybi08Handshake() {
        super(WebSocketVersion.V08, Collections.<String>emptyList(), false);
    }

    public Hybi08Handshake(List<String> subprotocols, boolean allowExtensions) {
        super(WebSocketVersion.V08, subprotocols, allowExtensions);
    }

    @Override
    protected WebSocketChannel createChannel(final HttpServerExchange exchange) {
        return new WebSocket08Channel(exchange.getConnection().getChannel(), exchange.getConnection().getBufferPool(), getWebSocketLocation(exchange), allowExtensions);
    }
}
