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

import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketException;
import io.undertow.websockets.core.WebSocketFrame;
import io.undertow.websockets.core.WebSocketFrameCorruptedException;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.core.WebSocketVersion;

import io.undertow.websockets.extensions.ExtensionFunction;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;
import java.util.Set;


/**
 * {@link WebSocketChannel} which is used for {@link WebSocketVersion#V08}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07Channel extends WebSocketChannel {

    private enum State {
        READING_FIRST,
        READING_SECOND,
        READING_EXTENDED_SIZE1,
        READING_EXTENDED_SIZE2,
        READING_EXTENDED_SIZE3,
        READING_EXTENDED_SIZE4,
        READING_EXTENDED_SIZE5,
        READING_EXTENDED_SIZE6,
        READING_EXTENDED_SIZE7,
        READING_EXTENDED_SIZE8,
        READING_MASK_1,
        READING_MASK_2,
        READING_MASK_3,
        READING_MASK_4,
        DONE,
    }

    private int fragmentedFramesCount;
    private final ByteBuffer lengthBuffer = ByteBuffer.allocate(8);

    private UTF8Checker checker;

    protected static final byte OPCODE_CONT = 0x0;
    protected static final byte OPCODE_TEXT = 0x1;
    protected static final byte OPCODE_BINARY = 0x2;
    protected static final byte OPCODE_CLOSE = 0x8;
    protected static final byte OPCODE_PING = 0x9;
    protected static final byte OPCODE_PONG = 0xA;

    /**
     * Create a new {@link WebSocket07Channel}
     *
     * @param channel    The {@link StreamConnection} over which the WebSocket Frames should get send and received.
     *                   Be aware that it already must be "upgraded".
     * @param bufferPool The {@link ByteBufferPool} which will be used to acquire {@link ByteBuffer}'s from.
     * @param wsUrl      The url for which the {@link WebSocket07Channel} was created.
     */
    public WebSocket07Channel(StreamConnection channel, ByteBufferPool bufferPool,
                              String wsUrl, String subProtocol, final boolean client, boolean allowExtensions, final ExtensionFunction extensionFunction, Set<WebSocketChannel> openConnections, OptionMap options) {
        super(channel, bufferPool, WebSocketVersion.V08, wsUrl, subProtocol, client, allowExtensions, extensionFunction, openConnections, options);
    }

    @Override
    protected PartialFrame receiveFrame() {
        return new WebSocketFrameHeader();
    }

    @Override
    protected void markReadsBroken(Throwable cause) {
        super.markReadsBroken(cause);
    }

    @Override
    protected void closeSubChannels() {
        IoUtils.safeClose(fragmentedChannel);
    }

    @Override
    protected StreamSinkFrameChannel createStreamSinkChannel(WebSocketFrameType type) {
        switch (type) {
            case TEXT:
                return new WebSocket07TextFrameSinkChannel(this);
            case BINARY:
                return new WebSocket07BinaryFrameSinkChannel(this);
            case CLOSE:
                return new WebSocket07CloseFrameSinkChannel(this);
            case PONG:
                return new WebSocket07PongFrameSinkChannel(this);
            case PING:
                return new WebSocket07PingFrameSinkChannel(this);
            default:
                throw WebSocketMessages.MESSAGES.unsupportedFrameType(type);
        }
    }

    class WebSocketFrameHeader implements WebSocketFrame {

        private boolean frameFinalFlag;
        private int frameRsv;
        private int frameOpcode;
        private int maskingKey;
        private boolean frameMasked;
        private long framePayloadLength;
        private State state = State.READING_FIRST;
        private int framePayloadLen1;
        private boolean done = false;

        @Override
        public StreamSourceFrameChannel getChannel(PooledByteBuffer pooled) {
            StreamSourceFrameChannel channel = createChannel(pooled);
            if (frameFinalFlag) {
                channel.finalFrame();
            } else {
                fragmentedChannel = channel;
            }
            return channel;
        }

        public StreamSourceFrameChannel createChannel(PooledByteBuffer pooled) {


            // Processing ping/pong/close frames because they cannot be
            // fragmented as per spec
            if (frameOpcode == OPCODE_PING) {
                if (frameMasked) {
                    return new WebSocket07PingFrameSourceChannel(WebSocket07Channel.this, frameRsv, new Masker(maskingKey), pooled, framePayloadLength);
                } else {
                    return new WebSocket07PingFrameSourceChannel(WebSocket07Channel.this, frameRsv, pooled, framePayloadLength);
                }
            }
            if (frameOpcode == OPCODE_PONG) {
                if (frameMasked) {
                    return new WebSocket07PongFrameSourceChannel(WebSocket07Channel.this, frameRsv, new Masker(maskingKey), pooled, framePayloadLength);
                } else {
                    return new WebSocket07PongFrameSourceChannel(WebSocket07Channel.this, frameRsv, pooled, framePayloadLength);
                }
            }
            if (frameOpcode == OPCODE_CLOSE) {
                if (frameMasked) {
                    return new WebSocket07CloseFrameSourceChannel(WebSocket07Channel.this, frameRsv, new Masker(maskingKey), pooled, framePayloadLength);
                } else {
                    return new WebSocket07CloseFrameSourceChannel(WebSocket07Channel.this, frameRsv, pooled, framePayloadLength);
                }
            }

            if (frameOpcode == OPCODE_TEXT) {
                // try to grab the checker which was used before
                UTF8Checker checker = WebSocket07Channel.this.checker;
                if (checker == null) {
                    checker = new UTF8Checker();
                }

                if (!frameFinalFlag) {
                    // if this is not the final fragment store the used checker to use it in later fragments also
                    WebSocket07Channel.this.checker = checker;
                } else {
                    // was the final fragment reset the checker to null
                    WebSocket07Channel.this.checker = null;
                }

                if (frameMasked) {
                    return new WebSocket07TextFrameSourceChannel(WebSocket07Channel.this, frameRsv, frameFinalFlag, new Masker(maskingKey), checker, pooled, framePayloadLength);
                } else {
                    return new WebSocket07TextFrameSourceChannel(WebSocket07Channel.this, frameRsv, frameFinalFlag, checker, pooled, framePayloadLength);
                }
            } else if (frameOpcode == OPCODE_BINARY) {
                if (frameMasked) {
                    return new WebSocket07BinaryFrameSourceChannel(WebSocket07Channel.this, frameRsv, frameFinalFlag, new Masker(maskingKey), pooled, framePayloadLength);
                } else {
                    return new WebSocket07BinaryFrameSourceChannel(WebSocket07Channel.this, frameRsv, frameFinalFlag, pooled, framePayloadLength);
                }
            } else if (frameOpcode == OPCODE_CONT) {
                throw new RuntimeException(); //should never happen
            } else {
                /*
                    Spec does not define how specific OpCodes should be treated.
                    We are going to return a Binary if an extension code is present.
                    Extensions implementation should be responsible of specific logic.
                 */
                if (hasReservedOpCode) {
                    if (frameMasked) {
                        return new WebSocket07BinaryFrameSourceChannel(WebSocket07Channel.this, frameRsv, frameFinalFlag, new Masker(maskingKey), pooled, framePayloadLength);
                    } else {
                        return new WebSocket07BinaryFrameSourceChannel(WebSocket07Channel.this, frameRsv, frameFinalFlag, pooled, framePayloadLength);
                    }
                } else {
                    throw WebSocketMessages.MESSAGES.unsupportedOpCode(frameOpcode);
                }
            }
        }

        @Override
        public void handle(final ByteBuffer buffer) throws WebSocketException {
            if (!buffer.hasRemaining()) {
                return;
            }
            while (state != State.DONE) {
                byte b;
                switch (state) {
                    case READING_FIRST:
                        // Read FIN, RSV, OPCODE
                        b = buffer.get();
                        frameFinalFlag = (b & 0x80) != 0;
                        frameRsv = (b & 0x70) >> 4;
                        frameOpcode = b & 0x0F;

                        if (WebSocketLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            WebSocketLogger.REQUEST_LOGGER.decodingFrameWithOpCode(frameOpcode);
                        }
                        state = State.READING_SECOND;
                        // clear the lengthBuffer to reuse it later
                        lengthBuffer.clear();
                    case READING_SECOND:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        // Read MASK, PAYLOAD LEN 1
                        //
                        frameMasked = (b & 0x80) != 0;
                        framePayloadLen1 = b & 0x7F;

                        if (frameRsv != 0) {
                            if (!areExtensionsSupported()) {
                                throw WebSocketMessages.MESSAGES.extensionsNotAllowed(frameRsv);
                            }
                        }

                        if (frameOpcode > 7) { // control frame (have MSB in opcode set)
                            validateControlFrame();
                        } else { // data frame
                            validateDataFrame();
                        }
                        if (framePayloadLen1 == 126 || framePayloadLen1 == 127) {
                            state = State.READING_EXTENDED_SIZE1;
                        } else {
                            framePayloadLength = framePayloadLen1;
                            if (frameMasked) {
                                state = State.READING_MASK_1;
                            } else {
                                state = State.DONE;
                            }
                            continue;
                        }
                    case READING_EXTENDED_SIZE1:
                        // Read frame payload length
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        lengthBuffer.put(b);
                        state = State.READING_EXTENDED_SIZE2;
                    case READING_EXTENDED_SIZE2:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        lengthBuffer.put(b);

                        if (framePayloadLen1 == 126) {
                            lengthBuffer.flip();
                            // must be unsigned short
                            framePayloadLength = lengthBuffer.getShort() & 0xFFFF;

                            if (frameMasked) {
                                state = State.READING_MASK_1;
                            } else {
                                state = State.DONE;
                            }
                            continue;
                        }
                        state = State.READING_EXTENDED_SIZE3;
                    case READING_EXTENDED_SIZE3:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        lengthBuffer.put(b);

                        state = State.READING_EXTENDED_SIZE4;
                    case READING_EXTENDED_SIZE4:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        lengthBuffer.put(b);
                        state = State.READING_EXTENDED_SIZE5;
                    case READING_EXTENDED_SIZE5:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        lengthBuffer.put(b);
                        state = State.READING_EXTENDED_SIZE6;
                    case READING_EXTENDED_SIZE6:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        lengthBuffer.put(b);
                        state = State.READING_EXTENDED_SIZE7;
                    case READING_EXTENDED_SIZE7:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        lengthBuffer.put(b);
                        state = State.READING_EXTENDED_SIZE8;
                    case READING_EXTENDED_SIZE8:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        lengthBuffer.put(b);

                        lengthBuffer.flip();
                        framePayloadLength = lengthBuffer.getLong();
                        if (frameMasked) {
                            state = State.READING_MASK_1;
                        } else {
                            state = State.DONE;
                            break;
                        }
                        state = State.READING_MASK_1;
                    case READING_MASK_1:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        maskingKey = b & 0xFF;
                        state = State.READING_MASK_2;
                    case READING_MASK_2:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        maskingKey = maskingKey << 8 | b & 0xFF;
                        state = State.READING_MASK_3;
                    case READING_MASK_3:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        maskingKey = maskingKey << 8 | b & 0xFF;
                        state = State.READING_MASK_4;
                    case READING_MASK_4:
                        if (!buffer.hasRemaining()) {
                            return;
                        }
                        b = buffer.get();
                        maskingKey = maskingKey << 8 | b & 0xFF;
                        state = State.DONE;
                        break;
                    default:
                        throw new IllegalStateException(state.toString());
                }
            }
            if (frameFinalFlag) {
                // check if the frame is a ping frame as these are allowed in the middle
                if (frameOpcode != OPCODE_PING && frameOpcode != OPCODE_PONG) {
                    fragmentedFramesCount = 0;
                }
            } else {
                // Increment counter
                fragmentedFramesCount++;
            }
            done = true;
        }

        private void validateDataFrame() throws WebSocketFrameCorruptedException {

            if (!isClient() && !frameMasked) {
                throw WebSocketMessages.MESSAGES.frameNotMasked();
            }

            // check for reserved data frame opcodes
            if (!(frameOpcode == OPCODE_CONT || frameOpcode == OPCODE_TEXT || frameOpcode == OPCODE_BINARY)) {
                throw WebSocketMessages.MESSAGES.reservedOpCodeInDataFrame(frameOpcode);
            }

            // check opcode vs message fragmentation state 1/2
            if (fragmentedFramesCount == 0 && frameOpcode == OPCODE_CONT) {
                throw WebSocketMessages.MESSAGES.continuationFrameOutsideFragmented();
            }

            // check opcode vs message fragmentation state 2/2
            if (fragmentedFramesCount != 0 && frameOpcode != OPCODE_CONT) {
                throw WebSocketMessages.MESSAGES.nonContinuationFrameInsideFragmented();
            }
        }

        private void validateControlFrame() throws WebSocketFrameCorruptedException {
            // control frames MUST NOT be fragmented
            if (!frameFinalFlag) {
                throw WebSocketMessages.MESSAGES.fragmentedControlFrame();
            }

            // control frames MUST have payload 125 octets or less as stated in the spec
            if (framePayloadLen1 > 125) {
                throw WebSocketMessages.MESSAGES.toBigControlFrame();
            }

            // check for reserved control frame opcodes
            if (!(frameOpcode == OPCODE_CLOSE || frameOpcode == OPCODE_PING || frameOpcode == OPCODE_PONG)) {
                throw WebSocketMessages.MESSAGES.reservedOpCodeInControlFrame(frameOpcode);
            }

            // close frame : if there is a body, the first two bytes of the
            // body MUST be a 2-byte unsigned integer (in network byte
            // order) representing a status code
            if (frameOpcode == 8 && framePayloadLen1 == 1) {
                throw WebSocketMessages.MESSAGES.controlFrameWithPayloadLen1();
            }
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public long getFrameLength() {
            return framePayloadLength;
        }

        int getMaskingKey() {
            return maskingKey;
        }

        @Override
        public AbstractFramedStreamSourceChannel<?, ?, ?> getExistingChannel() {
            if (frameOpcode == OPCODE_CONT) {
                StreamSourceFrameChannel ret = fragmentedChannel;
                if(frameFinalFlag) {
                    fragmentedChannel = null;
                }
                return ret;
            }
            return null;
        }

        @Override
        public boolean isFinalFragment() {
            return frameFinalFlag;
        }
    }


}
