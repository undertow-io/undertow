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
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.connector.PooledByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket07TextFrameSourceChannel extends StreamSourceFrameChannel {

    @Deprecated
    WebSocket07TextFrameSourceChannel(WebSocket07Channel wsChannel, int rsv, boolean finalFragment, Masker masker, UTF8Checker checker, PooledByteBuffer pooled, long frameLength) {
        super(wsChannel, WebSocketFrameType.TEXT, rsv, finalFragment, pooled, frameLength, masker, checker);
    }

    @Deprecated
    WebSocket07TextFrameSourceChannel(WebSocket07Channel wsChannel, int rsv, boolean finalFragment, UTF8Checker checker, PooledByteBuffer pooled, long frameLength) {
        super(wsChannel, WebSocketFrameType.TEXT, rsv, finalFragment, pooled, frameLength, null, checker);
    }

    WebSocket07TextFrameSourceChannel(WebSocket07Channel wsChannel, int rsv, boolean finalFragment, Masker masker, UTF8Checker checker, PooledByteBuffer pooled, long frameLength, final int maxReadFrameCount) {
        super(wsChannel, WebSocketFrameType.TEXT, rsv, finalFragment, pooled, frameLength, masker, maxReadFrameCount, checker);
    }

    WebSocket07TextFrameSourceChannel(final WebSocket07Channel wsChannel, final int rsv, final boolean finalFragment, final UTF8Checker checker, final PooledByteBuffer pooled, final long frameLength, final int maxReadFrameCount) {
        super(wsChannel, WebSocketFrameType.TEXT, rsv, finalFragment, pooled, frameLength, null, maxReadFrameCount, checker);
    }
}
