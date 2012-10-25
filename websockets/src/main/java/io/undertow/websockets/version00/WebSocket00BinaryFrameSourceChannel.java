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

import io.undertow.websockets.WebSocketChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketFrameType;

/**
 * {@link StreamSourceFrameChannel} implementations for read {@link WebSocketFrameType#BINARY}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket00BinaryFrameSourceChannel extends StreamSourceFrameChannel {

    private final int payloadSize;
    private int readBytes;

    WebSocket00BinaryFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket00Channel wsChannel, int payloadSize) {
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.BINARY, true);
        this.payloadSize = payloadSize;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        int toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }

        if (toRead < count) {
            count = toRead;
        }

        long r = channel.transferTo(position, count, target);
        readBytes += (int) r;
        if(readBytes == payloadSize) {
            streamSourceChannelControl.readFrameDone();
        }
        return r;
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        int toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }

        if (toRead < count) {
            count = toRead;
        }
        long r = channel.transferTo(count, throughBuffer, target);
        readBytes += (int) (r + throughBuffer.remaining());
        if(readBytes == payloadSize) {
            streamSourceChannelControl.readFrameDone();
        }
        return r;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }

        int old = dst.limit();
        try {
            if (byteToRead() < dst.remaining()) {
                dst.limit(dst.position() + byteToRead());
            }
            int r = channel.read(dst);
            readBytes += r;
            if(readBytes == payloadSize) {
                streamSourceChannelControl.readFrameDone();
            }
            return r;
        } finally {
            dst.limit(old);
        }
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        int toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }
        int[] old = new int[length];
        int used = 0;
        int remaining = toRead;
        for (int i = offset; i < length; i++) {
            old[i - offset] = dsts[i].limit();
            final int bufferRemaining = dsts[i].remaining();
            used += bufferRemaining;
            if (used > remaining) {
                dsts[i].limit(remaining);
            }
            remaining -= bufferRemaining;
            remaining = remaining < 0 ? 0 : remaining;
        }
        try {
            long b = channel.read(dsts, offset, length);
            readBytes += b;
            if(readBytes == payloadSize) {
                streamSourceChannelControl.readFrameDone();
            }
            return b;
        } finally {
            for (int i = offset; i < length; i++) {
                dsts[i].limit(old[i - offset]);
            }
        }

    }

    private int byteToRead() {
        return payloadSize - readBytes;
    }
}
