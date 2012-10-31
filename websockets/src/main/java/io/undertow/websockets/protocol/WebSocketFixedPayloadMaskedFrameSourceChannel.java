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

import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * A StreamSourceFrameChannel that is used to read a Frame with a fixed sized payload.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocketFixedPayloadMaskedFrameSourceChannel extends WebSocketFixedPayloadFrameSourceChannel {

    private final boolean masked;
    private final byte[] maskingKey;

    protected WebSocketFixedPayloadMaskedFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, int rsv, boolean finalFragment, long payloadSize, final boolean masked, final int maskingKey) {
        super(streamSourceChannelControl, channel, wsChannel, type, rsv, finalFragment, payloadSize);
        this.masked = masked;
        this.maskingKey = createsMaskingKey(maskingKey);
    }

    protected WebSocketFixedPayloadMaskedFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize, final boolean masked, final int maskingKey) {
        super(streamSourceChannelControl, channel, wsChannel, type, payloadSize);
        this.masked = masked;
        this.maskingKey = createsMaskingKey(maskingKey);
    }

    private static byte[] createsMaskingKey(int maskingKey) {
        byte[] key = new byte[4];
        key[0] = (byte) ((maskingKey >> 24) & 0xFF);
        key[1] = (byte) ((maskingKey >> 16) & 0xFF);
        key[2] = (byte) ((maskingKey >> 8) & 0xFF);
        key[3] = (byte) (maskingKey & 0xFF);
        return key;
    }

    @Override
    protected long transferTo0(long position, long count, FileChannel target) throws IOException {
        if (!masked) {
            return super.transferTo0(position, count, target);
        }
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public long transferTo0(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if (!masked) {
            return super.transferTo0(count, throughBuffer, target);
        }
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    protected int read0(ByteBuffer dst) throws IOException {
        int ret = super.read0(dst);
        if (!masked) {
            return ret;
        }
        ByteBuffer d = dst.duplicate();
        d.flip();
        int m = 0;
        for (int i = d.position(); i < d.limit(); ++i) {
            d.put(i, (byte) (d.get(i) ^ maskingKey[m++]));
            m = m % 4;
        }
        return ret;
    }

    @Override
    protected long read0(ByteBuffer[] dsts) throws IOException {
        return read0(dsts, 0, dsts.length);
    }

    @Override
    protected long read0(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long ret = super.read0(dsts, offset, length);
        if (!masked) {
            return ret;
        }
        for (int j = offset; j < offset + length; ++j) {
            ByteBuffer d = dsts[j].duplicate();
            d.flip();
            int m = 0;
            for (int i = d.position(); i < d.limit(); ++i) {
                d.put(i, (byte) (d.get(i) ^ maskingKey[m++]));
                m = m % 4;
            }
        }
        return ret;
    }

}
