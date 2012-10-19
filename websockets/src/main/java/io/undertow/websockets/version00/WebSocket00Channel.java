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

import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketException;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketVersion;


/**
 * {@link WebSocketChannel} which is used for {@link WebSocketVersion#V00}
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public final class WebSocket00Channel extends WebSocketChannel {

    /**
     * Create a new {@link WebSocket00Channel} 
     * 
     * @param channel           The {@link ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                          Be aware that it already must be "upgraded".
     * @param bufferPool        The {@link Pool} which will be used to acquire {@link ByteBuffer}'s from.
     * @param wsUrl             The url for which the {@link WebSocket00Channel} was created.
     */
    public WebSocket00Channel(ConnectedStreamChannel channel, Pool<ByteBuffer> bufferPool,
            String wsUrl) {
        super(channel, bufferPool, WebSocketVersion.V00, wsUrl);
    }

    @Override
    protected StreamSourceFrameChannel create(Pooled<ByteBuffer> pooled, PushBackStreamChannel channel) throws WebSocketException {
        ByteBuffer buffer = pooled.getResource();
        byte type = buffer.get();

        if ((type & 0x80) == 0x80) {

            long frameSize = 0;
            int lengthFieldSize = 0;
            byte b;

            // If the MSB on type is set, decode the frame length
            do {
                b = buffer.get();
                frameSize <<= 7;
                frameSize |= b & 0x7f;

                lengthFieldSize++;
                if (lengthFieldSize > 8) {
                    // Perhaps a malicious peer?
                    throw new WebSocketException("No Length encoded in the frame");
                }
            } while ((b & 0x80) == 0x80 && buffer.hasRemaining());
            if (frameSize == 0) {
                return new WebSocket00CloseFrameSourceChannel(channel, this);
            } else {
                return new WebSocket00BinaryFrameSourceChannel(channel, this, (int) frameSize);
            }
        } else {
            // Decode a 0xff terminated UTF-8 string
            return new WebSocket00TextFrameSourceChannel(channel, this);
        }

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
