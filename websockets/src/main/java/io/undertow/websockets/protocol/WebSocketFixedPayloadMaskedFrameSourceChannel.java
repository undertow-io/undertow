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
import io.undertow.websockets.masking.Masker;
import io.undertow.websockets.masking.MaskingFileChannel;
import io.undertow.websockets.masking.MaskingStreamSinkChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * A StreamSourceFrameChannel that is used to read a Frame with a fixed sized payload.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class WebSocketFixedPayloadMaskedFrameSourceChannel extends WebSocketFixedPayloadFrameSourceChannel {

    private final Masker masker;

    protected WebSocketFixedPayloadMaskedFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize, int rsv, boolean finalFragment, final boolean masked, final int maskingKey) {
        super(streamSourceChannelControl, channel, wsChannel, type, payloadSize, rsv, finalFragment);
        if (masked) {
            this.masker = new Masker(maskingKey);
        } else {
            this.masker = null;
        }
    }

    protected WebSocketFixedPayloadMaskedFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize, final boolean masked, final int maskingKey) {
        super(streamSourceChannelControl, channel, wsChannel, type, payloadSize);
        if (masked) {
            this.masker = new Masker(maskingKey);
        } else {
            this.masker = null;
        }
    }


    @Override
    protected long transferTo0(long position, long count, FileChannel target) throws IOException {
        if (masker == null) {
            return super.transferTo0(position, count, target);
        }
        return super.transferTo0(position, count, new MaskingFileChannel(target, masker));
    }

    @Override
    public long transferTo0(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if (masker == null) {
            return super.transferTo0(count, throughBuffer, target);
        }
        return super.transferTo0(count , throughBuffer, new MaskingStreamSinkChannel(target, masker));
    }

    @Override
    protected int read0(ByteBuffer dst) throws IOException {
        int ret = super.read0(dst);
        if (masker == null) {
            return ret;
        }
        masker.maskAfterRead(dst);
        return ret;
    }

    @Override
    protected long read0(ByteBuffer[] dsts) throws IOException {
        if (masker == null) {
            return super.read0(dsts);
        }
        return read0(dsts, 0, dsts.length);
    }

    @Override
    protected long read0(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (masker == null) {
            return super.read0(dsts, offset, length);
        }
        long ret = super.read0(dsts, offset, length);

        for (int j = offset; j < offset + length; ++j) {
            masker.maskAfterRead(dsts[j]);
        }
        return ret;
    }

}
