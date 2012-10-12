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

import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * {@link StreamSinkFrameChannel} implementations for write {@link WebSocketFrameType#TEXT}
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocket00TextFrameChannel extends StreamSinkFrameChannel {
    private final static ByteBuffer TEXT_FRAME_PREFIX = ByteBuffer.wrap(new byte[] {(byte) 0x00});
    private final static ByteBuffer TEXT_FRAME_SUFFIX = ByteBuffer.wrap(new byte[] {(byte) 0xFF});
    
    private boolean prefixWritten = false;
    
    public WebSocket00TextFrameChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, long payloadSize) {
        super(channel, wsChannel, WebSocketFrameType.TEXT, payloadSize);
    }


    @Override
    protected void close0() throws IOException {
        if (write(TEXT_FRAME_SUFFIX.duplicate()) != 1) {
            throw new IOException("Unable to write end of frame");
        }
    }

    @Override
    protected int write0(ByteBuffer src) throws IOException {
        if (writePrefix()) {
            return channel.write(src);
        }
        return 0;
    }
    
    private boolean writePrefix() throws IOException {
        if (!prefixWritten) {
            if (channel.write(TEXT_FRAME_PREFIX.duplicate()) == 1) {
                prefixWritten = true;
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    protected long write0(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (writePrefix()) {
            return channel.write(srcs, offset, length);
        }
        return 0;
    }

    @Override
    protected long write0(ByteBuffer[] srcs) throws IOException {
        if (writePrefix()) {
            return channel.write(srcs);
        }
        return 0;
    }

    @Override
    protected long transferFrom0(FileChannel src, long position, long count) throws IOException {
        if (writePrefix()) {
            return channel.transferFrom(src, position, count);
        }
        return 0;
    }

    @Override
    protected long transferFrom0(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        if (writePrefix()) {
            return channel.transferFrom(source, count, throughBuffer);
        }
        return 0;
    }
    

}
