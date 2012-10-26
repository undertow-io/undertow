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
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketVersion;
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

            private StreamSourceFrameChannel channel;

            @Override
            public StreamSourceFrameChannel getChannel() {
                return channel;
            }

            @Override
            public void handle(final ByteBuffer buffer, final PushBackStreamChannel channel) throws WebSocketException {
                // TODO: implement me
            }

            @Override
            public boolean isDone() {
                return channel != null;
            }
        };
    }

    @Override
    protected StreamSinkFrameChannel create(StreamSinkChannel channel, WebSocketFrameType type, long payloadSize) {
        return new WebSocket08FrameSinkChannel(channel, this, type, payloadSize);
    }
}
