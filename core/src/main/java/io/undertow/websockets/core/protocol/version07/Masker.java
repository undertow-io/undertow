/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.undertow.websockets.core.protocol.version07;

import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.websockets.core.function.ChannelFunction;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class Masker implements ChannelFunction {

    private byte[] maskingKey;
    int m;

    Masker(int maskingKey) {
        this.maskingKey = createsMaskingKey(maskingKey);
    }

    public void setMaskingKey(int maskingKey) {
        this.maskingKey = createsMaskingKey(maskingKey);
        m = 0;
    }

    private static byte[] createsMaskingKey(int maskingKey) {
        byte[] key = new byte[4];
        key[0] = (byte) (maskingKey >> 24 & 0xFF);
        key[1] = (byte) (maskingKey >> 16 & 0xFF);
        key[2] = (byte) (maskingKey >> 8 & 0xFF);
        key[3] = (byte) (maskingKey & 0xFF);
        return key;
    }

    private void mask(ByteBuffer buf, int position, int length) {
        int limit = position + length;
        for (int i = position ; i < limit; ++i) {
            buf.put(i, (byte) (buf.get(i) ^ maskingKey[m++]));
            m %= 4;
        }
    }

    @Override
    public void newFrame(FrameHeaderData headerData) {
        WebSocket07Channel.WebSocketFrameHeader header = (WebSocket07Channel.WebSocketFrameHeader) headerData;
        setMaskingKey(header.getMaskingKey());
    }

    @Override
    public void afterRead(ByteBuffer buf, int position, int length) {
        mask(buf, position, length);
    }

    @Override
    public void beforeWrite(ByteBuffer buf, int position, int length) {
        mask(buf, position, length);
    }

    @Override
    public void complete() {
        // noop
    }
}
