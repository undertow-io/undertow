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
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketException;
import io.undertow.websockets.WebSocketFrameCorruptedException;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketLogger;
import io.undertow.websockets.WebSocketMessages;
import io.undertow.websockets.WebSocketVersion;
import io.undertow.websockets.protocol.version07.Masker;
import io.undertow.websockets.protocol.version08.WebSocket08Channel;
import io.undertow.websockets.protocol.version07.UTF8Checker;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;


/**
 * {@link io.undertow.websockets.WebSocketChannel} which is used for {@link io.undertow.websockets.WebSocketVersion#V08}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07Channel extends WebSocketChannel {


    private static enum State {
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
    private ByteBuffer lengthBuffer = ByteBuffer.allocate(8);

    private UTF8Checker checker;

    protected static final byte OPCODE_CONT = 0x0;
    protected static final byte OPCODE_TEXT = 0x1;
    protected static final byte OPCODE_BINARY = 0x2;
    protected static final byte OPCODE_CLOSE = 0x8;
    protected static final byte OPCODE_PING = 0x9;
    protected static final byte OPCODE_PONG = 0xA;

    private final boolean allowExtensions;

    /**
     * Create a new {@link WebSocket08Channel}
     *
     * @param channel    The {@link org.xnio.channels.ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                   Be aware that it already must be "upgraded".
     * @param bufferPool The {@link org.xnio.Pool} which will be used to acquire {@link java.nio.ByteBuffer}'s from.
     * @param wsUrl      The url for which the {@link WebSocket08Channel} was created.
     */
    public WebSocket07Channel(ConnectedStreamChannel channel, Pool<ByteBuffer> bufferPool,
                              String wsUrl, boolean allowExtensions) {
        super(channel, bufferPool, WebSocketVersion.V08, wsUrl);
        this.allowExtensions = allowExtensions;
    }

    @Override
    protected PartialFrame receiveFrame(final StreamSourceChannelControl streamSourceChannelControl) {
        return new PartialFrame() {

            private boolean frameFinalFlag;
            private int frameRsv;
            private int frameOpcode;
            private int maskingKey = 0;
            private boolean frameMasked;
            private long framePayloadLength = 0;
            private State state = State.READING_FIRST;
            private int framePayloadLen1;

            private StreamSourceFrameChannel channel;

            @Override
            public StreamSourceFrameChannel getChannel() {
                return channel;
            }

            private void protocolViolation(PushBackStreamChannel channel, String reason) throws WebSocketFrameCorruptedException {
                IoUtils.safeClose(channel);
                throw new WebSocketFrameCorruptedException(reason);
            }

            @Override
            public void handle(final ByteBuffer buffer, final PushBackStreamChannel channel) throws WebSocketException {
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
                            // clear the lenghtbuffer to reuse it later
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

                            if (frameRsv != 0 && !allowExtensions) {
                                IoUtils.safeClose(channel);
                                throw WebSocketMessages.MESSAGES.extensionsNotAllowed(frameRsv);
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
                        case READING_MASK_1:
                            if (!buffer.hasRemaining()) {
                                return;
                            }
                            b = buffer.get();
                            maskingKey = (b & 0xFF);
                            state = State.READING_MASK_2;
                        case READING_MASK_2:
                            if (!buffer.hasRemaining()) {
                                return;
                            }
                            b = buffer.get();
                            maskingKey = (maskingKey << 8) | ((int)b & 0xFF);
                            state = State.READING_MASK_3;
                        case READING_MASK_3:
                            if (!buffer.hasRemaining()) {
                                return;
                            }
                            b = buffer.get();
                            maskingKey = (maskingKey << 8) | ((int)b & 0xFF);
                            state = State.READING_MASK_4;
                        case READING_MASK_4:
                            if (!buffer.hasRemaining()) {
                                return;
                            }
                            b = buffer.get();
                            maskingKey = (maskingKey << 8) | ((int)b & 0xFF);
                            state = State.DONE;
                            break;
                        default:
                            throw new IllegalStateException(state.toString());
                    }
                }
                // Processing ping/pong/close frames because they cannot be
                // fragmented as per spec
                if (frameOpcode == OPCODE_PING) {
                    if (frameMasked) {
                        this.channel = new WebSocket07PingFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, new Masker(maskingKey));
                    } else {
                        this.channel = new WebSocket07PingFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv);
                    }
                    return;
                } else if (frameOpcode == OPCODE_PONG) {
                    if (frameMasked) {
                        this.channel = new WebSocket07PongFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, new Masker(maskingKey));
                    } else {
                        this.channel = new WebSocket07PongFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv);
                    }
                    return;
                } else if (frameOpcode == OPCODE_CLOSE) {
                    if (frameMasked) {
                        this.channel = new WebSocket07CloseFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, new Masker(maskingKey));
                    } else {
                        this.channel = new WebSocket07CloseFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv);
                    }
                    return;
                }

                if (frameFinalFlag) {
                    // check if the frame is a ping frame as these are allowed in the middle
                    if (frameOpcode != OPCODE_PING) {
                        fragmentedFramesCount = 0;
                    }
                } else {
                    // Increment counter
                    fragmentedFramesCount++;
                }

                if (frameOpcode == OPCODE_TEXT) {
                    // try to grab the checker which was used before
                    UTF8Checker checker = WebSocket07Channel.this.checker;
                    if (checker == null) {
                        checker = new UTF8Checker();
                    }

                    if (frameMasked) {
                        this.channel = new WebSocket07TextFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, frameFinalFlag, new Masker(maskingKey), checker);
                    } else {
                        this.channel = new WebSocket07TextFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, frameFinalFlag, checker);

                    }

                    if (!frameFinalFlag) {
                        // if this is not the final fragment store the used checker to use it in later fragements also
                        WebSocket07Channel.this.checker = checker;
                    } else {
                        // was the final fragement reset the checker to null
                        WebSocket07Channel.this.checker = null;
                    }

                    return;
                } else if (frameOpcode == OPCODE_BINARY) {
                    if (frameMasked) {
                        this.channel = new WebSocket07BinaryFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, frameFinalFlag, new Masker(maskingKey));
                    } else {
                        this.channel = new WebSocket07BinaryFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, frameFinalFlag);
                    }
                    return;
                } else if (frameOpcode == OPCODE_CONT) {
                    if (frameMasked) {
                        this.channel = new WebSocket07ContinuationFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, frameFinalFlag, new Masker(maskingKey), WebSocket07Channel.this.checker);
                    } else {
                        this.channel = new WebSocket07ContinuationFrameSourceChannel(streamSourceChannelControl, channel, WebSocket07Channel.this, framePayloadLength, frameRsv, frameFinalFlag, WebSocket07Channel.this.checker);
                    }
                    return;
                } else {
                    throw WebSocketMessages.MESSAGES.unsupportedOpCode(frameOpcode);
                }
            }

            private void validateDataFrame() throws WebSocketFrameCorruptedException {
                // check for reserved data frame opcodes
                if (!(frameOpcode == OPCODE_CONT || frameOpcode == OPCODE_TEXT || frameOpcode == OPCODE_BINARY)) {
                    throw WebSocketMessages.MESSAGES.reservedOpCodeInDataFrame(frameOpcode);
                }

                // check opcode vs message fragmentation state 1/2
                if (fragmentedFramesCount == 0 && frameOpcode == OPCODE_CONT) {
                    throw WebSocketMessages.MESSAGES.continuationFrameOutsideFragmented();
                }

                // check opcode vs message fragmentation state 2/2
                if (fragmentedFramesCount != 0 && frameOpcode != OPCODE_CONT && frameOpcode != OPCODE_PING) {
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
                return channel != null;
            }
        };
    }

    @Override
    protected StreamSinkFrameChannel createStreamSinkChannel(StreamSinkChannel channel, WebSocketFrameType type, long payloadSize) {
        switch (type) {
            case TEXT:
                return new WebSocket07TextFrameSinkChannel(channel, this, payloadSize);
            case BINARY:
                return new WebSocket07BinaryFrameSinkChannel(channel, this, payloadSize);
            case CLOSE:
                return new WebSocket07CloseFrameSinkChannel(channel, this, payloadSize);
            case PONG:
                return new WebSocket07PongFrameSinkChannel(channel, this, payloadSize);
            case PING:
                return new WebSocket07PingFrameSinkChannel(channel, this, payloadSize);
            case CONTINUATION:
                return new WebSocket07ContinuationFrameSinkChannel(channel, this, payloadSize);
            default:
                throw WebSocketMessages.MESSAGES.unsupportedFrameType(type);
        }
    }

}
