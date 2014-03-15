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

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import org.xnio.Buffers;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * {@link StreamSinkFrameChannel} implementation for writing WebSocket Frames on {@link io.undertow.websockets.core.WebSocketVersion#V08} connections
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocket07FrameSinkChannel extends StreamSinkFrameChannel {

    private final int maskingKey;
    private final Masker masker;
    private final long payloadSize;
    private boolean dataWritten = false;
    long toWrite;

    protected WebSocket07FrameSinkChannel(WebSocket07Channel wsChannel, WebSocketFrameType type,
                                       long payloadSize) {
        super(wsChannel, type);
        this.payloadSize = payloadSize;
        this.toWrite = payloadSize;
        if(wsChannel.isClient()) {
            maskingKey = new Random().nextInt();
            masker = new Masker(maskingKey);
        } else {
            masker = null;
            maskingKey = 0;
        }
    }

    @Override
    protected void handleFlushComplete() {
        dataWritten = true;
    }

    private byte opCode() {
        if(dataWritten) {
            return WebSocket07Channel.OPCODE_CONT;
        }
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
    protected SendFrameHeader createFrameHeader() {
        if(payloadSize >= 0 && dataWritten) {
            //for fixed length we don't need more than one header
            return null;
        }
        Pooled<ByteBuffer> start = getChannel().getBufferPool().allocate();
        byte b0 = 0;
        //if writes are shutdown this is the final fragment
        if (isFinalFrameQueued() || payloadSize >= 0) {
            b0 |= 1 << 7;
        }
        b0 |= (getRsv() & 7) << 4;
        b0 |= opCode() & 0xf;

        final ByteBuffer header = start.getResource();
        //int maskLength = 0; // handle masking for clients but we are currently only
        // support servers this is not a priority by now
        byte maskKey = 0;
        if(masker != null) {
            maskKey |= 1 << 7;
        }
        long payloadSize;
        if(this.payloadSize >= 0) {
            payloadSize = this.payloadSize;
        } else {
            payloadSize = getBuffer().remaining();
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
        header.flip();
        return new SendFrameHeader(0, start);
    }

    @Override
    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if(toWrite >= 0 && Buffers.remaining(srcs) > toWrite) {
            throw WebSocketMessages.MESSAGES.messageOverflow();
        }
        if(masker == null) {
            return super.write(srcs, offset, length);
        } else {
            final Pooled<ByteBuffer> buffer = getChannel().getBufferPool().allocate();
            try {
                ByteBuffer[] copy = new ByteBuffer[length];
                for(int i = 0; i < length; ++i) {
                    copy[i] = srcs[offset + i].duplicate();
                }
                Buffers.copy(buffer.getResource(), copy, 0, length);
                buffer.getResource().flip();
                masker.beforeWrite(buffer.getResource(), 0, buffer.getResource().remaining());
                long written = super.write(buffer.getResource());
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
                toWrite -= written;
                return written;
            } finally {
                buffer.free();
            }
        }
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if(toWrite >= 0 && src.remaining() > toWrite) {
            throw WebSocketMessages.MESSAGES.messageOverflow();
        }
        if(masker == null) {
            return super.write(src);
        } else {
            final Pooled<ByteBuffer> buffer = getChannel().getBufferPool().allocate();
            try {
                ByteBuffer copy = src.duplicate();
                Buffers.copy(buffer.getResource(), copy);
                buffer.getResource().flip();
                masker.beforeWrite(buffer.getResource(), 0, buffer.getResource().remaining());
                int written = super.write(buffer.getResource());
                src.position(src.position() + written);
                toWrite -= written;
                return written;
            } finally {
                buffer.free();
            }
        }
    }
}
