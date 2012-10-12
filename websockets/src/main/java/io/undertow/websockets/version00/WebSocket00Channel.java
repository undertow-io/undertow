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

import org.xnio.ChannelListener;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketVersion;

public class WebSocket00Channel extends WebSocketChannel{

    public WebSocket00Channel(ConnectedStreamChannel channel, Pool<ByteBuffer> bufferPool,
            String wsUrl) {
        super(channel, bufferPool, WebSocketVersion.V00, wsUrl);
    }

    @Override
    public void sendClose() {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected StreamSourceFrameChannel create(StreamSourceChannel channel) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected StreamSinkFrameChannel create(StreamSinkChannel channel, WebSocketFrameType type, long payloadSize) {
        switch (type) {
        case TEXT:
            return new WebSocket00TextFrameChannel(channel, this, payloadSize);
        case BINARY:
            return new WebSocket00BinaryFrameChannel(channel, this, payloadSize);
        default:
            throw new IllegalArgumentException("WebSocketFrameType " + type + " is not supported by this WebSocketChannel");
        }
    }

    @Override
    protected ChannelListener<PushBackStreamChannel> createListener() {
        // TODO Auto-generated method stub
        return null;
    }

}
