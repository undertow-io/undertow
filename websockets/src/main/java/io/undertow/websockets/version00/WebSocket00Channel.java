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
package io.undertow.websockets.version00;

import java.nio.ByteBuffer;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketException;
import io.undertow.websockets.WebSocketFrameCorruptedException;
import io.undertow.websockets.WebSocketFrameType;
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
                //TODO: deal with the case where we can't read all the data at once
                if (!buffer.hasRemaining()) {
                    return;
                }
                if (receivedClosingHandshake) {
                    // discard everything as we received a close frame before
                    buffer.clear();
                    return;
                }
                switch (state) {
                    case FRAME_START:
                        if (buffer.remaining() < 1) {
                            return;
                        }
                        byte type = buffer.get();

                        if ((type & 0x80) == 0x80) {
                            state = State.NON_TEXT_FRAME;
                        } else {
                            state = State.TEXT_FRAME;
                        }
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
                                throw new WebSocketFrameCorruptedException("No Length encoded in the frame");
                            }
                        } while ((b & 0x80) == 0x80 && buffer.hasRemaining());
                        state = State.FRAME_SIZE_READ;
                    case FRAME_SIZE_READ:
                        if (frameSize == 0) {
                            receivedClosingHandshake = true;
                            this.channel = new WebSocket00CloseFrameSourceChannel(streamSourceChannelControl, channel, WebSocket00Channel.this);
                        } else {
                            this.channel = new WebSocketFixed00BinaryFrameSourceChannel(streamSourceChannelControl, channel, WebSocket00Channel.this, frameSize);
                        }
                        return;
                    case TEXT_FRAME:
                        if (buffer.remaining() < 1) {
                            return;
                        }
                        // skip start marker
                        buffer.position(buffer.position() + 1);

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
    protected StreamSinkFrameChannel create(StreamSinkChannel channel, WebSocketFrameType type, long payloadSize) {
        switch (type) {
            case TEXT:
                return new WebSocket00TextFrameSinkChannel(channel, this, payloadSize);
            case BINARY:
                return new WebSocket00BinaryFrameSinkChannel(channel, this, payloadSize);
            case CLOSE:
                if (payloadSize != 0) {
                    throw new IllegalArgumentException("Payload is not support in CloseFrames when using WebSocket Version 00");
                }
                return new WebSocket00CloseFrameSinkChannel(channel, this);
            default:
                throw new IllegalArgumentException("WebSocketFrameType " + type + " is not supported by this WebSocketChannel");
        }
    }
}
