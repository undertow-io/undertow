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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;


/**
 * {@link StreamSourceFrameChannel} which allows to read WebSocketFrames of type {@link WebSocketFrameType#CLOSE}
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
class WebSocket00CloseFrameSourceChannel extends StreamSourceFrameChannel {

    WebSocket00CloseFrameSourceChannel(StreamSourceChannel channel, WebSocketChannel wsChannel) {
        super(channel, wsChannel, WebSocketFrameType.CLOSE);
    }

    /**
     * Always returns <code>-1</code> as the frame can not contain any payload
     */
    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        return -1;
    }

    /**
     * Always returns <code>-1</code> as the frame can not contain any payload
     */
    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return -1;
    }

    /**
     * Always returns <code>-1</code> as the frame can not contain any payload
     */
    @Override
    public int read(ByteBuffer arg0) throws IOException {
        return -1;
    }

    /**
     * Always returns <code>-1</code> as the frame can not contain any payload
     */
    @Override
    public long read(ByteBuffer[] arg0) throws IOException {
        return -1;
    }

    /**
     * Always returns <code>-1</code> as the frame can not contain any payload
     */
    @Override
    public long read(ByteBuffer[] arg0, int arg1, int arg2) throws IOException {
        return -1;
    }

}
