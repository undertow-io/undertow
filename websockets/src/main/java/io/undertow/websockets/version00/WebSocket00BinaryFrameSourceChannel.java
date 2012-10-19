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
 * {@link StreamSourceFrameChannel} implementations for read {@link WebSocketFrameType#BINARY}
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
class WebSocket00BinaryFrameSourceChannel extends StreamSourceFrameChannel {

    private final int payloadSize;
    private int readBytes;
    WebSocket00BinaryFrameSourceChannel(StreamSourceChannel channel, WebSocketChannel wsChannel, int payloadSize) {
        super(channel, wsChannel, WebSocketFrameType.BINARY);
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
        return r;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }

        if (byteToRead() < dst.remaining()) {
            dst.limit(dst.position() + byteToRead());
        }
        return channel.read(dst);
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

        int l = 0;
        for (int i = offset; i < length; i++) {
            l++;
            ByteBuffer buf = dsts[i];
            int remain = buf.remaining();
            if (remain > toRead) {
                buf.limit(toRead);
                if (l == 0) {
                    int b = channel.read(buf);
                    readBytes += b;
                    return b;
                } else {
                    ByteBuffer[] dstsNew = new ByteBuffer[l];
                    System.arraycopy(dsts, offset, dstsNew, 0, dstsNew.length);
                    long b = channel.read(dstsNew);
                    readBytes += b;
                    return b;
                }
            }
        }
        long b = channel.read(dsts);
        readBytes += b;
        return b;
    }

    private int byteToRead() {
       return payloadSize - readBytes;
    }
}
