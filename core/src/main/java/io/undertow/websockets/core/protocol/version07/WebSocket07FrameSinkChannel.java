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

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.extensions.ExtensionByteBuffer;
import io.undertow.websockets.extensions.ExtensionFunction;
import org.xnio.Buffers;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

/**
 * {@link StreamSinkFrameChannel} implementation for writing WebSocket Frames on {@link io.undertow.websockets.core.WebSocketVersion#V08} connections
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocket07FrameSinkChannel extends StreamSinkFrameChannel {

    private int maskingKey;
    private final Masker masker;
    private long payloadSize;
    private boolean dataWritten = false;
    long toWrite;
    protected List<ExtensionFunction> extensions;
    protected boolean overflow = false;
    protected final int LAST_OVERFLOW = -13;
    protected ByteBuffer bufOverflow = null;
    protected Pooled<ByteBuffer> pooledOverflow = null;
    protected ExtensionByteBuffer extensionResult = null;

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
        extensions = wsChannel.getExtensions();
        /*
            Checks if there are negotiated extensions that need to modify RSV bits
         */
        int rsv = 0;
        if (wsChannel.areExtensionsSupported() && extensions != null &&
                (type == WebSocketFrameType.TEXT ||
                    type == WebSocketFrameType.BINARY)) {
            for (ExtensionFunction ext : extensions) {
                rsv = ext.writeRsv(rsv);
            }
        }
        setRsv(rsv);
    }

    @Override
    protected void handleFlushComplete(boolean finalFrame) {
        dataWritten = true;
        if(masker != null) {
            masker.setMaskingKey(maskingKey);
        }
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
        if (getRsv() == 0) {
            /*
                Case:
                - No extension scenario:
                - For fixed length we do not need more that one header.
             */
            if(payloadSize >= 0 && dataWritten) {
                if(masker != null) {
                    //do any required masking
                    //this is all one frame, so we don't call setMaskingKey
                    ByteBuffer buf = getBuffer();
                    masker.beforeWrite(buf, buf.position(), buf.remaining());
                }
                return null;
            }
        } else {
            /*
                Case:
                - Extensions scenario.
                - Extensions may require to include additional header with updated payloadSize. For example, several Type 0
                  Continuation fragments after a Text/Binary fragment.
             */
            payloadSize = getBuffer().remaining();
        }

        Pooled<ByteBuffer> start = getChannel().getBufferPool().allocate();
        byte b0 = 0;
        //if writes are shutdown this is the final fragment
        if (isFinalFrameQueued() || (getRsv() == 0 && payloadSize >= 0)) {
            b0 |= 1 << 7;
        }
        /*
            Known extensions (i.e. compression) should not modify RSV bit on continuation bit.
         */
        int rsv = opCode() == WebSocket07Channel.OPCODE_CONT ? 0 : getRsv();
        b0 |= (rsv & 7) << 4;
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
            maskingKey = new Random().nextInt(); //generate a new key for this frame
            header.put((byte)((maskingKey >> 24) & 0xFF));
            header.put((byte)((maskingKey >> 16) & 0xFF));
            header.put((byte)((maskingKey >> 8) & 0xFF));
            header.put((byte)((maskingKey & 0xFF)));
        }
        header.flip();


        if(masker != null) {
            masker.setMaskingKey(maskingKey);
            //do any required masking
            ByteBuffer buf = getBuffer();
            masker.beforeWrite(buf, buf.position(), buf.remaining());
        }

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
        if (getRsv() == 0) {
            return writeNoExtensions(srcs, offset, length);
        } else {
            return writeExtensions(srcs, offset, length);
        }
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if(toWrite >= 0 && src.remaining() > toWrite) {
            throw WebSocketMessages.MESSAGES.messageOverflow();
        }
        if (getRsv() == 0) {
            return writeNoExtensions(src);
        } else {
            return writeExtensions(src);
        }
    }

    private int writeNoExtensions(final ByteBuffer src) throws IOException {
        return super.write(src);
    }

    private int writeExtensions(final ByteBuffer src) throws IOException {
        if (!overflow) {
            final Pooled<ByteBuffer> buffer = getChannel().getBufferPool().allocate();
            try {
                ByteBuffer copy = src.duplicate();
                Buffers.copy(buffer.getResource(), copy);
                buffer.getResource().flip();

                int remainingBeforeExtension = buffer.getResource().remaining();
                /*
                    Case:
                    - Extension present.
                    - A extension can transform internally buffer to write.
                      For example, we can have a 10K bytes buffer to write, but an extension can compress it in 2K, so
                      internally we should write 2K but we should return that we write 10K.
                      We can have remotely scenarios where we can have buffer expanded, for example, we can write a 10K
                      buffer but an extension can expand it internally to 20K but we should return that we write 10K.
                 */
                extensionResult = applyExtensions(buffer.getResource(), 0, buffer.getResource().remaining());
                int written = super.write(buffer.getResource());
                if (written == 0) {
                    /*
                        Case:
                        - Channel is waiting for flush.
                     */
                    return written;
                }
                if (buffer.getResource().hasRemaining()) {
                    /*
                        Case:
                        - After a write() operation there are pending bytes to write.
                        - Normally when we do not have space in buffer and a flush is needed.
                        - Extension present so as we can have a non 1 to 1 between source and real buffer, we need to save an
                          overflow buffer to write transformed data.
                     */
                    overflow = true;
                    bufOverflow = buffer.getResource();
                    pooledOverflow = buffer;
                }

                if (!overflow && extensionResult != null) {
                    /*
                        Case:
                        - An extension needs more extra buffers.
                     */
                    overflow = true;
                    bufOverflow = null;
                }

                /*
                    Case:
                    - After a write operation source buffer position should be updated.
                    - We need to update equivalent chunks, for example a 10K can be written in 2K buffer. And each 1024 bytes
                      can be 112 bytes, so after 112 bytes written we should update in the source buffer its 1024 bytes equivalent.
                 */
                if ((src.position() + remainingBeforeExtension) < src.capacity()) {
                    if ((src.position() + remainingBeforeExtension) < src.limit()) {
                        src.position(src.position() + remainingBeforeExtension);
                    } else {
                        src.limit(src.position() + remainingBeforeExtension);
                        src.position(src.limit());
                    }
                } else {
                    src.limit(src.capacity());
                    src.position(src.limit());
                }

                toWrite -= remainingBeforeExtension;

                /*
                    Case:
                    - All source buffer is processed but overflow buffer is pending.
                    - We should maintain source buffer under limit to force a new write invocation.
                 */
                if (overflow && !src.hasRemaining()) {
                    if (src.limit() == 0) {
                        src.limit(1);
                        src.put(0, (byte) 0);
                    } else if (src.limit() == src.position()) {
                        src.position(src.limit() - 1);
                    }
                    toWrite = LAST_OVERFLOW;
                }

                return remainingBeforeExtension;
            } finally {
                if (!overflow) {
                    buffer.free();
                }
            }
        } else {
            /*
                We have two types of overflow:
                - overflow of original buffer (bufOverflow != null)
                - extensionResult extra buffers
             */
            if (bufOverflow != null) {

                try {
                    int writtenOverflow = super.write(bufOverflow);
                    if (writtenOverflow == 0) {
                        return writtenOverflow;
                    }
                    if (!bufOverflow.hasRemaining()) {
                        bufOverflow = null;
                        if (extensionResult == null) {
                            overflow = false;
                        }
                    }
                    if (toWrite == LAST_OVERFLOW && !overflow) {
                        if (src.limit() == 1) {
                            src.limit(0);
                        } else {
                            src.position(src.limit());
                        }
                        return -1;
                    }
                    return writtenOverflow;
                } finally {
                    if (bufOverflow == null && pooledOverflow != null) {
                        pooledOverflow.free();
                    }
                }
            } else {

                try {
                    ByteBuffer extraBuffer = extensionResult.getExtraRemainingBuffer();
                    int writtenOverflow = super.write(extraBuffer);
                    if (writtenOverflow == 0) {
                        return writtenOverflow;
                    }
                    if (!extensionResult.hasExtraRemaining()) {
                        overflow = false;
                    }
                    if (toWrite == LAST_OVERFLOW && !overflow) {
                        if (src.limit() == 1) {
                            src.limit(0);
                        } else {
                            src.position(src.limit());
                        }
                        return -1;
                    }
                    return writtenOverflow;
                } finally {
                    if (!overflow) {
                        extensionResult.free();
                        extensionResult = null;
                    }
                }

            }
        }
    }

    private long writeNoExtensions(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return super.write(srcs, offset, length);
    }

    private long writeExtensions(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (!overflow) {
            final Pooled<ByteBuffer> buffer = getChannel().getBufferPool().allocate();
            try {
                ByteBuffer[] copy = new ByteBuffer[length];
                for (int i = 0; i < length; ++i) {
                    copy[i] = srcs[offset + i].duplicate();
                }
                Buffers.copy(buffer.getResource(), copy, 0, length);
                buffer.getResource().flip();

                int remainingBeforeExtension = buffer.getResource().remaining();

                /*
                    Case:
                    - Extension present.
                    - A extension can transform internally buffer to write.
                      For example, we can have a 10K bytes buffer to write, but an extension can compress it in 2K, so
                      internally we should write 2K but we should return that we write 10K.
                      We can have remotely scenarios where we can have buffer expanded, for example, we can write a 10K
                      buffer but an extension can expand it internally to 20K but we should return that we write 10K.
                 */
                extensionResult = applyExtensions(buffer.getResource(), 0, buffer.getResource().remaining());

                long written = super.write(buffer.getResource());
                if (written == 0) {
                    /*
                        Case:
                        - Channel is waiting for flush.
                     */
                    return 0;
                }

                if (buffer.getResource().hasRemaining()) {
                    /*
                        Case:
                        - After a write() operation there are pending bytes to write.
                        - Normally when we do not have space in buffer and a flush is needed.
                        - Extension present so as we can have a non 1 to 1 between source and real buffer, we need to save an
                          overflow buffer to write transformed data.
                     */
                    overflow = true;
                    bufOverflow = buffer.getResource();
                    pooledOverflow = buffer;
                }

                if (!overflow && extensionResult != null) {
                    /*
                        Case:
                        - An extension needs more extra buffers.
                     */
                    overflow = true;
                    bufOverflow = null;
                }

                /*
                   Case:
                   - Extension can modify internally content length to write.
                   - Position should be adjusted for that.
                 */
                long toAllocate = remainingBeforeExtension;

                for (int i = offset; i < length; ++i) {
                    ByteBuffer thisBuf = srcs[i];
                    if (toAllocate <= thisBuf.remaining()) {
                        thisBuf.position((int) (thisBuf.position() + toAllocate));
                        break;
                    } else {
                        toAllocate -= thisBuf.remaining();
                        thisBuf.position(thisBuf.limit());
                    }
                }

                toWrite -= toAllocate;

                /*
                    Case:
                    - All source buffer is processed but overflow buffer is pending.
                    - We should maintain source buffer under limit to force a new write invocation.
                 */
                if (overflow && !Buffers.hasRemaining(srcs)) {
                    ByteBuffer lastBuf = srcs[srcs.length - 1];
                    if (lastBuf.limit() == 0) {
                        lastBuf.limit(1);
                        lastBuf.put(0, (byte)0);
                    } else if (lastBuf.limit() == lastBuf.position()) {
                        lastBuf.position(lastBuf.position() - 1);
                    }
                    toWrite = LAST_OVERFLOW;
                }
                return toAllocate;
            } finally {
                if (!overflow) {
                    buffer.free();
                }
            }

        } else {
            /*
                We have two types of overflow:
                - overflow of original buffer (bufOverflow != null)
                - extensionResult extra buffers
             */
            if (bufOverflow != null) {

                try {
                    int writtenOverflow = super.write(bufOverflow);
                    if (writtenOverflow == 0) {
                        return writtenOverflow;
                    }
                    if (!bufOverflow.hasRemaining()) {
                        bufOverflow = null;
                        if (extensionResult == null) {
                            overflow = false;
                        }
                    }
                    if (toWrite == LAST_OVERFLOW && !overflow) {
                        ByteBuffer lastBuf = srcs[srcs.length - 1];
                        if (lastBuf.limit() == 1) {
                            lastBuf.limit(0);
                        } else {
                            lastBuf.position(lastBuf.limit());
                        }
                        return -1;
                    }
                    return writtenOverflow;
                } finally {
                    if (bufOverflow == null && pooledOverflow != null) {
                        pooledOverflow.free();
                    }
                }

            } else {

                try {
                    ByteBuffer extraBuffer = extensionResult.getExtraRemainingBuffer();
                    int writtenOverflow = super.write(extraBuffer);
                    if (writtenOverflow == 0) {
                        return writtenOverflow;
                    }
                    if (!extensionResult.hasExtraRemaining()) {
                        overflow = false;
                    }
                    if (toWrite == LAST_OVERFLOW && !overflow) {
                        ByteBuffer lastBuf = srcs[srcs.length - 1];
                        if (lastBuf.limit() == 1) {
                            lastBuf.limit(0);
                        } else {
                            lastBuf.position(lastBuf.limit());
                        }
                        return -1;
                    }
                    return writtenOverflow;
                } finally {
                    if (!overflow) {
                        extensionResult.free();
                        extensionResult = null;
                    }
                }
            }

        }
    }

    /**
     * Process Extensions chain before a write operation.
     * <p>
     * An extension can modify original content beyond {@code ByteBuffer} capacity,then original buffer is wrapped with
     * {@link ExtensionByteBuffer} class. {@code ExtensionByteBuffer} stores extra buffer to manage overflow of original
     * {@code ByteBuffer} .
     *
     * @param buffer    the buffer to operate on
     * @param position  the index in the buffer to start from
     * @param length    the number of bytes to operate on
     * @return          a {@link ExtensionByteBuffer} instance as a wrapper of original buffer with extra buffers;
     *                  {@code null} if no extra buffers needed
     * @throws IOException
     */
    protected ExtensionByteBuffer applyExtensions(final ByteBuffer buffer, final int position, final int length) throws IOException {
        ExtensionByteBuffer extBuffer = new ExtensionByteBuffer(getWebSocketChannel(), buffer, position);
        int newLength = length;
        if (extensions != null) {
            for (ExtensionFunction ext : extensions) {
                ext.beforeWrite(this, extBuffer, position, newLength);
                if (extBuffer.getFilled() == 0) {
                    buffer.position(position);
                    newLength = 0;
                } else if (extBuffer.getFilled() != newLength) {
                    newLength = extBuffer.getFilled();
                }
            }
        }
        buffer.flip();
        if (extBuffer.hasExtra()) {
            extBuffer.flipExtra();
            return extBuffer;
        } else {
            return null;
        }
    }

    /**
     * Process Extensions chain before a flush operation.
     * <p>
     * An extension can modify original content beyond {@code ByteBuffer} capacity,then original buffer is wrapped with
     * {@link ExtensionByteBuffer} class. {@code ExtensionByteBuffer} stores extra buffer to manage overflow of original
     * {@code ByteBuffer} .
     *
     * @param buffer    the buffer to operate on
     * @param position  the index in the buffer to start from
     * @param length    the number of bytes to operate on
     * @return          a {@link ExtensionByteBuffer} instance as a wrapper of original buffer with extra buffers;
     *                  {@code null} if no extra buffers needed
     * @throws IOException
     */
    protected ExtensionByteBuffer applyExtensionsFlush(final ByteBuffer buffer, final int position, final int length) throws IOException {
        ExtensionByteBuffer extBuffer = new ExtensionByteBuffer(getWebSocketChannel(), buffer, position);
        int newLength = length;
        if (extensions != null) {
            for (ExtensionFunction ext : extensions) {
                ext.beforeFlush(this, extBuffer, position, newLength);
                if (extBuffer.getFilled() == 0) {
                    buffer.position(position);
                    newLength = 0;
                } else if (extBuffer.getFilled() != newLength) {
                    newLength = extBuffer.getFilled();
                }
            }
        }
        buffer.flip();
        if (extBuffer.hasExtra()) {
            extBuffer.flipExtra();
            return extBuffer;
        } else {
            return null;
        }
    }

    @Override
    public void shutdownWrites() throws IOException {
        if (getRsv() > 0 && isOpen()) {
            Pooled<ByteBuffer> pooledPadding = this.getChannel().getBufferPool().allocate();
            ByteBuffer buffer = pooledPadding.getResource();
            ExtensionByteBuffer extPadding = applyExtensionsFlush(buffer, 0, buffer.remaining());
            try {
                while (buffer.hasRemaining()) {
                    super.write(buffer);
                }
                if (extPadding != null) {
                    while (extPadding.hasExtraRemaining()) {
                        super.write(extPadding.getExtraRemainingBuffer());
                    }
                }
            } finally {
                pooledPadding.free();
                if (extPadding != null && extPadding.hasExtra()) {
                    extPadding.free();
                }
            }
        }
        super.shutdownWrites();
    }
}
