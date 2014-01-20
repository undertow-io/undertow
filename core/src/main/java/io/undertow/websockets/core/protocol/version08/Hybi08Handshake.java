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

package io.undertow.websockets.core.protocol.version08;

import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.protocol.version07.Hybi07Handshake;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.Pool;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;

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
    public WebSocketChannel createChannel(final WebSocketHttpExchange exchange, final StreamConnection channel, final Pool<ByteBuffer> pool) {
        return new WebSocket08Channel(channel, pool, getWebSocketLocation(exchange), subprotocols, false, allowExtensions);

    }
}
