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

import io.undertow.UndertowLogger;
import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.ImmediatePooledByteBuffer;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.extensions.ExtensionFunction;
import io.undertow.websockets.extensions.NoopExtensionFunction;
import io.undertow.connector.PooledByteBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * {@link StreamSinkFrameChannel} implementation for writing WebSocket Frames on {@link io.undertow.websockets.core.WebSocketVersion#V08} connections
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocket07FrameSinkChannel extends StreamSinkFrameChannel {

    private final Masker masker;
    private volatile boolean dataWritten = false;
    protected final ExtensionFunction extensionFunction;
    private final Random random = new Random();

    protected WebSocket07FrameSinkChannel(WebSocket07Channel wsChannel, WebSocketFrameType type) {
        super(wsChannel, type);

        if(wsChannel.isClient()) {
            masker = new Masker(0);
        } else {
            masker = null;
        }

        /*
            Checks if there are negotiated extensions that need to modify RSV bits
         */
        if (wsChannel.areExtensionsSupported() && (type == WebSocketFrameType.TEXT || type == WebSocketFrameType.BINARY)) {
            extensionFunction = wsChannel.getExtensionFunction();
            setRsv(extensionFunction.writeRsv(0));
        } else {
            extensionFunction = NoopExtensionFunction.INSTANCE;
            setRsv(0);
        }
    }

    @Override
    protected void handleFlushComplete(boolean finalFrame) {
        dataWritten = true;
// TODO not sure we need to do this as the key was set when it was last used
//        if(masker != null) {
//            masker.setMaskingKey(maskingKey);
//        }
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
        byte b0 = 0;

        //if writes are shutdown this is the final fragment
        if (isFinalFrameQueued()) {
            b0 |= 1 << 7; // set FIN
        }

        /*
            Known extensions (i.e. compression) should not modify RSV bit on continuation bit.
         */
        byte opCode = opCode();

        int rsv = opCode == WebSocket07Channel.OPCODE_CONT ? 0 : getRsv();
        b0 |= (rsv & 7) << 4;
        b0 |= opCode & 0xf;

        final ByteBuffer header = ByteBuffer.allocate(14);

        byte maskKey = 0;
        if(masker != null) {
            maskKey |= 1 << 7;
        }

        long payloadSize = getBuffer().remaining();

        if (payloadSize > 125 && opCode == WebSocket07Channel.OPCODE_PING) {
            throw WebSocketMessages.MESSAGES.invalidPayloadLengthForPing(payloadSize);
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
            int maskingKey = random.nextInt(); //generate a new key for this frame
            header.put((byte)((maskingKey >> 24) & 0xFF));
            header.put((byte)((maskingKey >> 16) & 0xFF));
            header.put((byte)((maskingKey >> 8) & 0xFF));
            header.put((byte)((maskingKey & 0xFF)));
            masker.setMaskingKey(maskingKey);
            //do any required masking
            ByteBuffer buf = getBuffer();
            masker.beforeWrite(buf, buf.position(), buf.remaining());
        }

        header.flip();

        return new SendFrameHeader(0, new ImmediatePooledByteBuffer(header));
    }

    @Override
    protected PooledByteBuffer preWriteTransform(PooledByteBuffer body) {
        try {
            return super.preWriteTransform(extensionFunction.transformForWrite(body, this, this.isFinalFrameQueued()));
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            markBroken();
            throw new RuntimeException(e);
        }
    }
}
