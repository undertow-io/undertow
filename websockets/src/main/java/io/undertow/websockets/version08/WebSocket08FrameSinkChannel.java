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
package io.undertow.websockets.version08;

import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketVersion;
import io.undertow.websockets.version00.WebSocket00FrameSinkChannel;

import java.nio.ByteBuffer;

import org.xnio.Buffers;
import org.xnio.channels.StreamSinkChannel;

/**
 * {@link WebSocket00FrameSinkChannel} implementation for writing WebSocket Frames on {@link WebSocketVersion#V08} connections
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket08FrameSinkChannel extends WebSocket00FrameSinkChannel {
    public WebSocket08FrameSinkChannel(StreamSinkChannel channel, WebSocket08Channel wsChannel, WebSocketFrameType type,
                                long payloadSize) {
        super(channel, wsChannel, type, payloadSize);
        if (opcode == OPCODE_PING && payloadSize > 125) {
            throw new IllegalArgumentException("invalid payload for PING (payload length must be <= 125, was "
                    + payloadSize);
        }
    }

    private static final byte OPCODE_CONT = 0x0;
    private static final byte OPCODE_TEXT = 0x1;
    private static final byte OPCODE_BINARY = 0x2;
    private static final byte OPCODE_CLOSE = 0x8;
    private static final byte OPCODE_PING = 0x9;
    private static final byte OPCODE_PONG = 0xA;

    private final byte opcode = opCode();

    private byte opCode() {
        switch (getType()) {
        case CONTINUATION:
            return OPCODE_CONT;
        case TEXT:
            return OPCODE_TEXT;
        case BINARY:
            return OPCODE_BINARY;
        case CLOSE:
            return OPCODE_CLOSE;
        case PING:
            return OPCODE_PING;
        case PONG:
            return OPCODE_PONG;
        default:
            throw new IllegalStateException("Unsupported WebsocketType " + getType());
        }
    }

    @Override
    protected ByteBuffer createFrameStart() {
        byte opcode = opCode();
        int b0 = 0;
        if (isFinalFragment()) {
            b0 |= 1 << 7;
        }
        b0 |= getRsv() % 8 << 4;
        b0 |= opcode % 128;

        final ByteBuffer header;
        int maskLength = 0; // handle masking for clients but we are currently only
                            // support servers this is not a priority by now
        if (payloadSize <= 125) {
            header = ByteBuffer.allocate(2 + maskLength);
            header.put((byte) b0);
            header.put((byte) payloadSize);
        } else if (payloadSize <= 0xFFFF) {
            header = ByteBuffer.allocate(3 + maskLength);
            header.put((byte) b0);
            header.put((byte) 126);
            header.put((byte) (payloadSize >>> 8 & 0xFF));
            header.put((byte) (payloadSize & 0xFF));
        } else {
            header = ByteBuffer.allocate(10 + maskLength);
            header.put((byte) b0);
            header.put((byte) 127);
            header.putLong(payloadSize);
        }

        return (ByteBuffer) header.flip();
    }


    @Override
    protected ByteBuffer createFrameEnd() {
        return Buffers.EMPTY_BYTE_BUFFER;
    }
}
