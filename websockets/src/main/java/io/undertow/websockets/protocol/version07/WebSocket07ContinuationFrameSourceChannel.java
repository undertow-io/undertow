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
package io.undertow.websockets.protocol.version07;

import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.protocol.WebSocketFixedPayloadMaskedFrameSourceChannel;
import io.undertow.websockets.utf8.UTF8Checker;
import io.undertow.websockets.utf8.UTF8FixedPayloadMaskedFrameSourceChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07ContinuationFrameSourceChannel extends UTF8FixedPayloadMaskedFrameSourceChannel {
    WebSocket07ContinuationFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket07Channel wsChannel, long payloadSize, int rsv, boolean finalFragment, final boolean masked, final int mask, UTF8Checker checker) {
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.CONTINUATION, payloadSize, rsv, finalFragment, masked, mask, checker);
    }

    WebSocket07ContinuationFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket07Channel wsChannel, long payloadSize, int rsv, boolean finalFragment, final boolean masked, final int mask) {
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.CONTINUATION, payloadSize, rsv, finalFragment, masked, mask, null);
    }
}
