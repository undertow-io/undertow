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
package io.undertow.websockets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import io.undertow.websockets.function.ChannelFunction;
import io.undertow.websockets.function.ChannelFunctionFileChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * A StreamSourceFrameChannel that is used to read a Frame with a fixed sized payload.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class FixedPayloadFrameSourceChannel extends StreamSourceFrameChannel {

    protected long readBytes;
    private final ChannelFunction[] functions;

    protected FixedPayloadFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize, int rsv, boolean finalFragment, ChannelFunction... functions) {
        super(streamSourceChannelControl, channel, wsChannel, type, payloadSize, rsv, finalFragment);
        this.functions = functions;
    }

    @Override
    protected final long transferTo0(long position, long count, FileChannel target) throws IOException {
        long toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }

        if (toRead < count) {
            count = toRead;
        }

        long r;
        if (functions != null && functions.length > 0) {
            r = channel.transferTo(position, count, new ChannelFunctionFileChannel(target, functions));
        } else {
            r = channel.transferTo(position, count, target);
        }
        if (r > 0) {
            readBytes += r;
        }
        return r;
    }

    protected static long transfer(final ReadableByteChannel source, final long count, final ByteBuffer throughBuffer, final WritableByteChannel sink) throws IOException {
        long total = 0L;
        while (total < count) {
            throughBuffer.clear();
            if (count - total < throughBuffer.remaining()) {
                throughBuffer.limit((int) (count - total));
            }

            try {
                long res = source.read(throughBuffer);
                if (res <= 0) {
                    return total == 0L ? res : total;
                }
            } finally {
                throughBuffer.flip();

            }
            while (throughBuffer.hasRemaining()) {
                long res = sink.write(throughBuffer);
                if (res <= 0) {
                    return total;
                }
                total += res;
            }
        }
        return total;
    }

    @Override
    protected final long transferTo0(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        long toRead = byteToRead();
        if (toRead < 1) {
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
        int r;
        int position = dst.position();
        int old = dst.limit();
        try {
            if (toRead < dst.remaining()) {
                dst.limit(dst.position() + (int) toRead);
            }
            r = channel.read(dst);
            if (r > 0) {
                readBytes += r;

                afterRead(dst, position, r);
            }
            return r;
        } finally {
            dst.limit(old);

        }
    }

    @Override
    protected final long read0(ByteBuffer[] dsts) throws IOException {
        return read0(dsts, 0, dsts.length);
    }

    @Override
    protected long read0(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long toRead = byteToRead();
        if (toRead < 1) {
            return -1;
        }
        Bounds[] old = new Bounds[length];
        int used = 0;
        long remaining = toRead;
        for (int i = offset; i < length; i++) {
            ByteBuffer dst = dsts[i];
            old[i - offset] = new Bounds(dst.position(), dst.limit());
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

                for (int i = offset; i < length; i++) {
                    ByteBuffer dst = dsts[i];
                    int oldPos = old[i - offset].position;
                    afterRead(dst, oldPos, dst.position() - oldPos);
                }
            }
            return b;
        } finally {
            for (int i = offset; i < length; i++) {
                dsts[i].limit(old[i - offset].limit);
            }

        }
    }

    private long byteToRead() {
        return getPayloadSize() - readBytes;
    }

    @Override
    protected final boolean isComplete() {
        assert readBytes <= getPayloadSize();
        return readBytes == getPayloadSize();
    }

    protected void afterRead(ByteBuffer buffer, int position, int length) throws IOException {
        for (ChannelFunction func : functions) {
            func.afterRead(buffer, position, length);
        }

    }

    @Override
    protected void complete() throws IOException {
        if (isFinalFragment()) {
            for (ChannelFunction func : functions) {
                func.complete();
            }
        }
        super.complete();
    }

    private static class Bounds {
        final int position;
        final int limit;

        Bounds(int position, int limit) {
            this.position = position;
            this.limit = limit;
        }
    }
}
