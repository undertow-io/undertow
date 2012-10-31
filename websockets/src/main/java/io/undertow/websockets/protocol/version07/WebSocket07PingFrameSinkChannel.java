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

import io.undertow.websockets.WebSocketFrameType;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07PingFrameSinkChannel extends WebSocket07FrameSinkChannel {
    public WebSocket07PingFrameSinkChannel(StreamSinkChannel channel, WebSocket07Channel wsChannel, long payloadSize) {
        super(channel, wsChannel, WebSocketFrameType.CLOSE, payloadSize);
        if (payloadSize > 125) {
            throw new IllegalArgumentException("invalid payload for PING (payload length must be <= 125, was " + payloadSize);
        }
    }
}
