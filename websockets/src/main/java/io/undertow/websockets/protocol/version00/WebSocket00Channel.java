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
package io.undertow.websockets.protocol.version00;

import java.nio.ByteBuffer;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketException;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketMessages;
import io.undertow.websockets.WebSocketVersion;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;


/**
 * {@link WebSocketChannel} which is used for {@link WebSocketVersion#V00}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket00Channel extends WebSocketChannel {
    private enum State {
        FRAME_START, TEXT_FRAME, NON_TEXT_FRAME, FRAME_SIZE_READ
    }

    /**
     * Create a new {@link WebSocket00Channel}
     *
     * @param channel    The {@link ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                   Be aware that it already must be "upgraded".
     * @param bufferPool The {@link Pool} which will be used to acquire {@link ByteBuffer}'s from.
     * @param wsUrl      The url for which the {@link WebSocket00Channel} was created.
     */
    public WebSocket00Channel(ConnectedStreamChannel channel, Pool<ByteBuffer> bufferPool,
                              String wsUrl) {
        super(channel, bufferPool, WebSocketVersion.V00, wsUrl);
    }


    @Override
    protected PartialFrame receiveFrame(final StreamSourceChannelControl streamSourceChannelControl) {
        return new PartialFrame() {
            private boolean receivedClosingHandshake;
            private State state = State.FRAME_START;
            private StreamSourceFrameChannel channel;
            private long frameSize = 0;
            private int lengthFieldSize = 0;

            @Override
            public StreamSourceFrameChannel getChannel() {
                return channel;
            }

            @Override
            public void handle(final ByteBuffer buffer, final PushBackStreamChannel channel) throws WebSocketException {
                if (!buffer.hasRemaining()) {
                    return;
                }

                if (receivedClosingHandshake) {
                    // discard everything as we received a close frame before
                    buffer.clear();
                    return;
                }

                if (state == State.FRAME_START) {
                    if (buffer.remaining() < 1) {
                        return;
                    }
                    byte type = buffer.get();

                    if ((type & 0x80) == 0x80) {
                        state = State.NON_TEXT_FRAME;
                    } else {
                        state = State.TEXT_FRAME;
                    }
                }

                switch (state) {
                    case NON_TEXT_FRAME:
                        if (buffer.remaining() < 1) {
                            return;
                        }
                        byte b;
                        // If the MSB on type is set, decode the frame length
                        do {
                            b = buffer.get();
                            frameSize <<= 7;
                            frameSize |= b & 0x7f;

                            lengthFieldSize++;
                            if (lengthFieldSize > 8) {
                                // Perhaps a malicious peer?
                                throw WebSocketMessages.MESSAGES.noLengthEncodedInFrame();
                            }
                            if (!buffer.hasRemaining()) {
                                if ((b & 0x80) != 0x80) {
                                    // that's ok just break here
                                    break;
                                }

                                // nothing left to read and still not fully read the frame size
                                return;
                            }
                        } while ((b & 0x80) == 0x80);
                        state = State.FRAME_SIZE_READ;
                    case FRAME_SIZE_READ:
                        if (frameSize == 0) {
                            receivedClosingHandshake = true;
                            this.channel = new WebSocket00CloseFrameSourceChannel(streamSourceChannelControl, channel, WebSocket00Channel.this);
                        } else {
                            this.channel = new WebSocket00BinaryFrameSourceChannel(streamSourceChannelControl, channel, WebSocket00Channel.this, frameSize);
                        }
                        return;
                    case TEXT_FRAME:
                        // Decode a 0xff terminated UTF-8 string
                        this.channel = new WebSocket00TextFrameSourceChannel(streamSourceChannelControl, channel, WebSocket00Channel.this);
                        return;
                    default:
                        throw new IllegalStateException();
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
                return new WebSocket00TextFrameSinkChannel(channel, this, payloadSize);
            case BINARY:
                return new WebSocket00BinaryFrameSinkChannel(channel, this, payloadSize);
            case CLOSE:
                if (payloadSize != 0) {
                    throw WebSocketMessages.MESSAGES.payloadNotSupportedInCloseFrames();
                }
                return new WebSocket00CloseFrameSinkChannel(channel, this);
            default:
                throw WebSocketMessages.MESSAGES.unsupportedFrameType(type);
        }
    }
}
