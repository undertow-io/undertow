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

import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import org.xnio.Buffers;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

/**
 * {@link StreamSinkFrameChannel} implementation for writing WebSocket Frames on {@link io.undertow.websockets.core.WebSocketVersion#V08} connections
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocket07FrameSinkChannel extends StreamSinkFrameChannel {

    private Pooled<ByteBuffer> start;
    private final int maskingKey;
    private final Masker masker;

    protected WebSocket07FrameSinkChannel(StreamSinkChannel channel, WebSocket07Channel wsChannel, WebSocketFrameType type,
                                       long payloadSize) {
        super(channel, wsChannel, type, payloadSize);
        if(wsChannel.isClient()) {
            maskingKey = new Random().nextInt();
            masker = new Masker(maskingKey);
        } else {
            masker = null;
            maskingKey = 0;
        }
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
        byte maskKey = 0;
        if(masker != null) {
            maskKey |= 1 << 7;
        }
        if (payloadSize <= 125) {
            header.put(b0);
            header.put((byte)((payloadSize | maskKey) & 0xFF));
        } else if (payloadSize <= 0xFFFF) {
            header.put(b0);
            header.put((byte) ((126 | maskKey) & 0xFF));
            header.put((byte) (payloadSize >>> 8 & 0xFF));
            header.put((byte) (payloadSize & 0xFF));
        } else {
            header.put(b0);
            header.put((byte) ((127 | maskKey) & 0xFF));
            header.putLong(payloadSize);
        }
        if(masker != null) {
            header.put((byte)((maskingKey >> 24) & 0xFF));
            header.put((byte)((maskingKey >> 16) & 0xFF));
            header.put((byte)((maskingKey >> 8) & 0xFF));
            header.put((byte)((maskingKey & 0xFF)));
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

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if(masker == null) {
            return super.write(srcs, offset, length);
        } else {
            final Pooled<ByteBuffer> buffer = wsChannel.getBufferPool().allocate();
            try {
                ByteBuffer[] copy = new ByteBuffer[length];
                for(int i = 0; i < length; ++i) {
                    copy[i] = srcs[offset + i].duplicate();
                }
                Buffers.copy(buffer.getResource(), copy, 0, length);
                buffer.getResource().flip();
                masker.beforeWrite(buffer.getResource(), 0, buffer.getResource().remaining());
                long written = super.write(new ByteBuffer[]{buffer.getResource()}, 0, 1);
                long toAllocate = written;
                for(int i = offset; i < length; ++i) {
                    ByteBuffer thisBuf = srcs[i];
                    if(toAllocate < thisBuf.remaining()) {
                        thisBuf.position((int) (thisBuf.position() + toAllocate));
                        break;
                    } else {
                        toAllocate -= thisBuf.remaining();
                        thisBuf.position(thisBuf.limit());
                    }
                }
                return written;
            } finally {
                buffer.free();
            }
        }
    }

    @Override
    protected long transferFrom0(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, this);
    }
}
