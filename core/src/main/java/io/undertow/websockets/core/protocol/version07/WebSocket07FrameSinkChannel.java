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
import io.undertow.websockets.extensions.ExtensionFunction;
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

    private int maskingKey;
    private final Masker masker;
    private long payloadSize;
    private boolean dataWritten = false;
    long toWrite;
    protected ExtensionFunction extensionFunction;

    protected WebSocket07FrameSinkChannel(WebSocket07Channel wsChannel, WebSocketFrameType type, long payloadSize) {
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
        extensionFunction = wsChannel.getExtensionFunction();
        /*
            Checks if there are negotiated extensions that need to modify RSV bits
         */
        if (wsChannel.areExtensionsSupported() && extensionFunction != null &&
                (type == WebSocketFrameType.TEXT || type == WebSocketFrameType.BINARY)) {
            setRsv(extensionFunction.writeRsv(0));
        } else {
            setRsv(0);
        }
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
    public boolean send(Pooled<ByteBuffer> pooled) throws IOException {
        // Check that the underlying write will succeed prior to applying the function
        // Could corrupt LZW stream if not
        if(safeToSend()) {
            return super.send(extensionFunction.transformForWrite(pooled, getWebSocketChannel()));
        }

        return false;
    }
}
