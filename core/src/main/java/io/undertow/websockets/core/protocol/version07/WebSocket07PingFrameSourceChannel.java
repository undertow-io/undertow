/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package io.undertow.websockets.core.protocol.version07;

import io.undertow.websockets.core.FixedPayloadFrameSourceChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import org.xnio.Pooled;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket07PingFrameSourceChannel extends FixedPayloadFrameSourceChannel {
    WebSocket07PingFrameSourceChannel(WebSocketChannel wsChannel, long payloadSize, int rsv, Masker masker, Pooled<ByteBuffer> pooled, long frameLength) {
        // can not be fragmented
        super(wsChannel, WebSocketFrameType.PING, payloadSize, rsv, true, pooled, frameLength, masker);
    }

    WebSocket07PingFrameSourceChannel(WebSocketChannel wsChannel, long payloadSize, int rsv, Pooled<ByteBuffer> pooled, long frameLength) {
        // can not be fragmented
        super(wsChannel, WebSocketFrameType.PING, payloadSize, rsv, true, pooled, frameLength);
    }
}
