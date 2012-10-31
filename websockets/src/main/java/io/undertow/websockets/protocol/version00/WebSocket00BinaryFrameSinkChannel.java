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
package io.undertow.websockets.protocol.version00;

import java.nio.ByteBuffer;

import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.protocol.AbstractFrameSinkChannel;
import org.xnio.Buffers;
import org.xnio.channels.StreamSinkChannel;

/**
 * {@link io.undertow.websockets.protocol.AbstractFrameSinkChannel} implementation for writing {@link WebSocketFrameType#BINARY}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket00BinaryFrameSinkChannel extends AbstractFrameSinkChannel {

    WebSocket00BinaryFrameSinkChannel(StreamSinkChannel channel, WebSocket00Channel wsChannel, long payloadSize) {
        super(channel, wsChannel, WebSocketFrameType.BINARY, payloadSize);
    }

    @Override
    protected ByteBuffer createFrameStart() {
        int dataLen = (int) payloadSize;
        ByteBuffer buffer = ByteBuffer.allocate(5);
        // Encode type.
        buffer.put((byte) 0x80);

        // Encode length.
        int b1 = dataLen >>> 28 & 0x7F;
        int b2 = dataLen >>> 14 & 0x7F;
        int b3 = dataLen >>> 7 & 0x7F;
        int b4 = dataLen & 0x7F;
        if (b1 == 0) {
            if (b2 == 0) {
                if (b3 == 0) {
                    buffer.put((byte) b4);
                } else {
                    buffer.put((byte) (b3 | 0x80));
                    buffer.put((byte) b4);
                }
            } else {
                buffer.put((byte) (b2 | 0x80));
                buffer.put((byte) (b3 | 0x80));
                buffer.put((byte) b4);
            }
        } else {
            buffer.put((byte) (b1 | 0x80));
            buffer.put((byte) (b2 | 0x80));
            buffer.put((byte) (b3 | 0x80));
            buffer.put((byte) b4);
        }
        buffer.flip();
        return buffer;
    }

    @Override
    protected ByteBuffer createFrameEnd() {
        return Buffers.EMPTY_BYTE_BUFFER;
    }
}
