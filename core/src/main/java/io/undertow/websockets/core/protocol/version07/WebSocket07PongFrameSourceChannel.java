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

import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.connector.PooledByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket07PongFrameSourceChannel extends StreamSourceFrameChannel {
    WebSocket07PongFrameSourceChannel(WebSocketChannel wsChannel, int rsv, final Masker masker, PooledByteBuffer pooled, long frameLength) {
        // can not be fragmented
        super(wsChannel, WebSocketFrameType.PONG, rsv, true, pooled, frameLength, masker);
    }

    WebSocket07PongFrameSourceChannel(WebSocketChannel wsChannel, int rsv, PooledByteBuffer pooled, long frameLength) {
        // can not be fragmented
        super(wsChannel, WebSocketFrameType.PONG, rsv, true, pooled, frameLength, null);
    }
}
