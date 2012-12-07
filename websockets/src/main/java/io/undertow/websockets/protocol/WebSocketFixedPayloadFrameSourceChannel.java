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
package io.undertow.websockets.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * A StreamSourceFrameChannel that is used to read a Frame with a fixed sized payload.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocketFixedPayloadFrameSourceChannel extends StreamSourceFrameChannel {

    protected long readBytes;

    protected WebSocketFixedPayloadFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize, int rsv, boolean finalFragment) {
        super(streamSourceChannelControl, channel, wsChannel, type, payloadSize, rsv, finalFragment);
    }

    protected WebSocketFixedPayloadFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize) {
        super(streamSourceChannelControl, channel, wsChannel, type, payloadSize);
    }

    @Override
    protected long transferTo0(long position, long count, FileChannel target) throws IOException {
        long toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }

        if (toRead < count) {
            count = toRead;
        }

        long r = channel.transferTo(position, count, target);
        if (r > 0) {
            readBytes += r;
        }
        return r;
    }

    protected static long transfer(final ReadableByteChannel source, final long count, final ByteBuffer throughBuffer, final WritableByteChannel sink) throws IOException {
        long res;
        long total = 0L;
        throughBuffer.clear();
        while (total < count) {
            if (count - total < throughBuffer.remaining()) {
                throughBuffer.limit((int) (count - total));
            }

            try {
                res = source.read(throughBuffer);
                if (res <= 0) {
                    return total == 0L ? res : total;
                }
            } finally {
                throughBuffer.flip();

            }
            res = sink.write(throughBuffer);

            if (res == 0) {
                return total;
            }
            total += res;
            if (total < count) {
                // only compact if nothing is left otherwise we may
                // end up with a buffer that has a lim == cap even
                // if it not contain data that we are interested in
                throughBuffer.compact();
            }

        }
        return total;
    }

    @Override
    protected final long transferTo0(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        long toRead = byteToRead();
        if (toRead < 1) {
            throughBuffer.clear();
            return -1;
        }

        if (toRead < count) {
            count = toRead;
        }

        // use this because of XNIO bug
        // See https://issues.jboss.org/browse/XNIO-185
        return transfer(this, count, throughBuffer, target);
    }

    @Override
    protected int read0(ByteBuffer dst) throws IOException {
        long toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }

        int old = dst.limit();
        try {
            if (byteToRead() < dst.remaining()) {
                dst.limit(dst.position() + (int) byteToRead());
            }
            int r = channel.read(dst);
            if (r > 0) {
                readBytes += r;
            }
            return r;
        } finally {
            dst.limit(old);
        }
    }

    @Override
    protected long read0(ByteBuffer[] dsts) throws IOException {
        return read0(dsts, 0, dsts.length);
    }

    @Override
    protected long read0(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }
        int[] old = new int[length];
        int used = 0;
        long remaining = toRead;
        for (int i = offset; i < length; i++) {
            old[i - offset] = dsts[i].limit();
            final int bufferRemaining = dsts[i].remaining();
            used += bufferRemaining;
            if (used > remaining) {
                dsts[i].limit((int) remaining);
            }
            remaining -= bufferRemaining;
            remaining = remaining < 0 ? 0 : remaining;
        }
        try {
            long b = channel.read(dsts, offset, length);
            if (b > 0) {
                readBytes += b;
            }
            return b;
        } finally {
            for (int i = offset; i < length; i++) {
                dsts[i].limit(old[i - offset]);
            }
        }
    }

    private long byteToRead() {
        return getPayloadSize() - readBytes;
    }

    @Override
    protected boolean isComplete() {
        assert readBytes <= getPayloadSize();
        return readBytes == getPayloadSize();
    }
}
