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

import java.nio.ByteBuffer;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketMessages;
import io.undertow.websockets.WebSocketVersion;
import org.xnio.Buffers;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

/**
 * {@link StreamSinkFrameChannel} implementation for writing WebSocket Frames on {@link WebSocketVersion#V08} connections
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocket07FrameSinkChannel extends StreamSinkFrameChannel {

    private Pooled<ByteBuffer> start;

    protected WebSocket07FrameSinkChannel(StreamSinkChannel channel, WebSocket07Channel wsChannel, WebSocketFrameType type,
                                       long payloadSize) {
        super(channel, wsChannel, type, payloadSize);
    }

    private byte opCode() {
        switch (getType()) {
        case CONTINUATION:
            return WebSocket07Channel.OPCODE_CONT;
        case TEXT:
            return WebSocket07Channel.OPCODE_TEXT;
        case BINARY:
            return WebSocket07Channel.OPCODE_BINARY;
        case CLOSE:
            return WebSocket07Channel.OPCODE_CLOSE;
        case PING:
            return WebSocket07Channel.OPCODE_PING;
        case PONG:
            return WebSocket07Channel.OPCODE_PONG;
        default:
            throw WebSocketMessages.MESSAGES.unsupportedFrameType(getType());
        }
    }

    @Override
    protected ByteBuffer createFrameStart() {
        byte b0 = 0;
        if (isFinalFragment()) {
            b0 |= 1 << 7;
        }
        b0 |= (getRsv() & 7) << 4;
        b0 |= opCode() & 0xf;

        start = wsChannel.getBufferPool().allocate();

        final ByteBuffer header = start.getResource();
        //int maskLength = 0; // handle masking for clients but we are currently only
                            // support servers this is not a priority by now

        if (payloadSize <= 125) {
            header.put(b0);
            header.put((byte)payloadSize);
        } else if (payloadSize <= 0xFFFF) {
            header.put(b0);
            header.put((byte) 126);
            header.put((byte) (payloadSize >>> 8 & 0xFF));
            header.put((byte) (payloadSize & 0xFF));
        } else {
            header.put(b0);
            header.put((byte) 127);
            header.putLong(payloadSize);
        }
        return header;
    }

    @Override
    protected void frameStartComplete() {
        super.frameStartComplete();
        if (start != null) {
            start.free();
        }
    }

    @Override
    protected ByteBuffer createFrameEnd() {
        return Buffers.EMPTY_BYTE_BUFFER;
    }
}
