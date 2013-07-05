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
package io.undertow.websockets.core.protocol.version00;

import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import org.xnio.Buffers;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * {@link StreamSinkFrameChannel} implementation for writing {@link WebSocketFrameType#CLOSE}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket00CloseFrameSinkChannel extends StreamSinkFrameChannel {
    private static final ByteBuffer END = ByteBuffer.allocateDirect(2).put((byte) 0xFF).put((byte) 0x00);

    WebSocket00CloseFrameSinkChannel(StreamSinkChannel channel, WebSocket00Channel wsChannel) {
        super(channel, wsChannel, WebSocketFrameType.CLOSE, 0);
    }

    @Override
    protected long write0(ByteBuffer[] srcs, int offset, int length) throws IOException {
        throw WebSocketMessages.MESSAGES.payloadNotSupportedInCloseFrames();
    }

    @Override
    protected long transferFrom0(FileChannel src, long position, long count) throws IOException {
        throw WebSocketMessages.MESSAGES.payloadNotSupportedInCloseFrames();
    }

    @Override
    protected ByteBuffer createFrameStart() {
        return Buffers.EMPTY_BYTE_BUFFER;
    }

    @Override
    protected ByteBuffer createFrameEnd() {
        return END.duplicate();
    }
}
