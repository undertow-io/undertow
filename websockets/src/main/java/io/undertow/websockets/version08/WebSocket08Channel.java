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
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;


/**
 * {@link WebSocketChannel} which is used for {@link WebSocketVersion#V08}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket08Channel extends WebSocketChannel {

    private int fragmentedFramesCount;

    protected static final byte OPCODE_CONT = 0x0;
    protected static final byte OPCODE_TEXT = 0x1;
    protected static final byte OPCODE_BINARY = 0x2;
    protected static final byte OPCODE_CLOSE = 0x8;
    protected static final byte OPCODE_PING = 0x9;
    protected static final byte OPCODE_PONG = 0xA;

    /**
     * Create a new {@link WebSocket08Channel}
     *
     * @param channel    The {@link ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                   Be aware that it already must be "upgraded".
     * @param bufferPool The {@link Pool} which will be used to acquire {@link ByteBuffer}'s from.
     * @param wsUrl      The url for which the {@link WebSocket08Channel} was created.
     */
    public WebSocket08Channel(ConnectedStreamChannel channel, Pool<ByteBuffer> bufferPool,
                              String wsUrl) {
        super(channel, bufferPool, WebSocketVersion.V08, wsUrl);
    }


    @Override
    protected PartialFrame receiveFrame(final StreamSourceChannelControl streamSourceChannelControl) {
        return new PartialFrame() {


            private boolean frameFinalFlag;
            private int frameRsv;
            private int frameOpcode;
            private long framePayloadLength;


            // TODO: We may want to make it configurable
            private final boolean allowExtensions = true;
            private boolean receivedClosingHandshake;

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
                //TODO: deal with the case where we can't read all the data at once
                if (!buffer.hasRemaining()) {
                    return;
                }
                if (receivedClosingHandshake) {
                    // discard everything as we received a close frame before
                    buffer.clear();
                    return;
                }
                framePayloadLength = -1;

                // Read FIN, RSV, OPCODE
                byte b = buffer.get();
                frameFinalFlag = (b & 0x80) != 0;
                frameRsv = (b & 0x70) >> 4;
                frameOpcode = b & 0x0F;

                if (WebSocketLogger.REQUEST_LOGGER.isDebugEnabled()) {
                    WebSocketLogger.REQUEST_LOGGER.decodingFrameWithOpCode(frameOpcode);
                }

                // Read MASK, PAYLOAD LEN 1
                //
                // TODO: Handle masking for client-side usage
                b = buffer.get();
                boolean frameMasked = (b & 0x80) != 0;
                int framePayloadLen1 = b & 0x7F;

                if (frameRsv != 0 && !allowExtensions) {
                    IoUtils.safeClose(channel);
                    throw WebSocketMessages.MESSAGES.extensionsNotAllowed(frameRsv);
                }

                if (frameOpcode > 7) { // control frame (have MSB in opcode set)

                    // control frames MUST NOT be fragmented
                    if (!frameFinalFlag) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.fragmentedControlFrame();
                    }

                    // control frames MUST have payload 125 octets or less as stated in the spec
                    if (framePayloadLen1 > 125) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.toBigControlFrame();
                    }

                    // check for reserved control frame opcodes
                    if (!(frameOpcode == OPCODE_CLOSE || frameOpcode == OPCODE_PING || frameOpcode == OPCODE_PONG)) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.reservedOpCodeInControlFrame(frameOpcode);
                    }

                    // close frame : if there is a body, the first two bytes of the
                    // body MUST be a 2-byte unsigned integer (in network byte
                    // order) representing a status code
                    if (frameOpcode == 8 && framePayloadLen1 == 1) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.controlFrameWithPayloadLen1();
                    }
                } else { // data frame
                    // check for reserved data frame opcodes
                    if (!(frameOpcode == OPCODE_CONT || frameOpcode == OPCODE_TEXT || frameOpcode == OPCODE_BINARY)) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.reservedOpCodeInDataFrame(frameOpcode);
                    }

                    // check opcode vs message fragmentation state 1/2
                    if (fragmentedFramesCount == 0 && frameOpcode == OPCODE_CONT) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.continuationFrameOutsideFragmented();
                    }

                    // check opcode vs message fragmentation state 2/2
                    if (fragmentedFramesCount != 0 && frameOpcode != OPCODE_CONT && frameOpcode != OPCODE_PING) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.nonContinuationFrameInsideFragmented();
                    }
                }

                // Read frame payload length
                if (framePayloadLen1 == 126) {
                    // read unsigned short
                    framePayloadLength = buffer.get() & 0xffff;
                    if (framePayloadLength < 126) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.invalidDataFrameLength();
                    }
                } else if (framePayloadLen1 == 127) {
                    framePayloadLength = buffer.getLong();
                    // TODO: check if it's bigger than 0x7FFFFFFFFFFFFFFF, Maybe
                    // just check if it's negative?

                    if (framePayloadLength < 65536) {
                        IoUtils.safeClose(channel);
                        throw WebSocketMessages.MESSAGES.invalidDataFrameLength();
                    }
                } else {
                    framePayloadLength = framePayloadLen1;
                }

                // Processing ping/pong/close frames because they cannot be
                // fragmented as per spec
                if (frameOpcode == OPCODE_PING) {
                    this.channel = new WebSocket08PingFrameSourceChannel(streamSourceChannelControl, channel,
                            WebSocket08Channel.this, frameRsv, framePayloadLength);
                    return;
                } else if (frameOpcode == OPCODE_PONG) {
                    this.channel = new WebSocket08PongFrameSourceChannel(streamSourceChannelControl, channel,
                            WebSocket08Channel.this, frameRsv, framePayloadLength);
                    return;
                } else if (frameOpcode == OPCODE_CLOSE) {
                    receivedClosingHandshake = true;
                    this.channel = new WebSocket08CloseFrameSourceChannel(streamSourceChannelControl, channel,
                            WebSocket08Channel.this, frameRsv, framePayloadLength);
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
                    this.channel = new WebSocket08TextFrameSourceChannel(streamSourceChannelControl, channel, WebSocket08Channel.this, frameRsv, frameFinalFlag, framePayloadLength);
                    return;
                } else if (frameOpcode == OPCODE_BINARY) {
                    this.channel = new WebSocket08BinaryFrameSourceChannel(streamSourceChannelControl, channel, WebSocket08Channel.this, frameRsv, frameFinalFlag, framePayloadLength);
                    return;
                } else if (frameOpcode == OPCODE_CONT) {
                    this.channel = new WebSocket08ContinuationFrameSourceChannel(streamSourceChannelControl, channel, WebSocket08Channel.this, frameRsv, frameFinalFlag, framePayloadLength);
                    return;
                } else {
                    throw WebSocketMessages.MESSAGES.unsupportedOpCode(frameOpcode);
                }

            }


            @Override
            public boolean isDone() {
                return channel != null;
            }
        };
    }

    @Override
    protected StreamSinkFrameChannel create(StreamSinkChannel channel, WebSocketFrameType type, long payloadSize) {
        switch (type) {
            case TEXT:
                return new WebSocket08TextFrameSinkChannel(channel, this, payloadSize);
            case BINARY:
                return new WebSocket08BinaryFrameSinkChannel(channel, this, payloadSize);
            case CLOSE:
                return new WebSocket08CloseFrameSinkChannel(channel, this, payloadSize);
            case PONG:
                return new WebSocket08PongFrameSinkChannel(channel, this, payloadSize);
            case PING:
                return new WebSocket08PingFrameSinkChannel(channel, this, payloadSize);
            case CONTINUATION:
                return new WebSocket08ContinuationFrameSinkChannel(channel, this, payloadSize);
            default:
                throw WebSocketMessages.MESSAGES.unsupportedFrameType(type);
        }
    }
}
