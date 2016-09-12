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

package io.undertow.protocols.http2;

import org.xnio.Bits;

import java.nio.ByteBuffer;

/**
 * Parser for HTTP2 Headers frames
 *
 * @author Stuart Douglas
 */
class Http2PushPromiseParser extends Http2HeaderBlockParser {

    private int paddingLength = 0;
    private int promisedStreamId;
    private static final int STREAM_MASK = ~(1 << 7);

    Http2PushPromiseParser(int frameLength, HpackDecoder hpackDecoder, boolean client, int streamId) {
        super(frameLength, hpackDecoder, client, streamId);
    }

    @Override
    protected boolean handleBeforeHeader(ByteBuffer resource, Http2FrameHeaderParser headerParser) {
        boolean hasPadding = Bits.anyAreSet(headerParser.flags, Http2Channel.HEADERS_FLAG_PADDED);
        int reqLength = (hasPadding ? 1 : 0) + 4;
        if (resource.remaining() < reqLength) {
            return false;
        }
        if (hasPadding) {
            paddingLength = (resource.get() & 0xFF);
        }
        promisedStreamId = (resource.get() & STREAM_MASK) << 24;
        promisedStreamId += (resource.get() & 0xFF) << 16;
        promisedStreamId += (resource.get() & 0xFF) << 8;
        promisedStreamId += (resource.get() & 0xFF);
        return true;
    }

    protected int getPaddingLength() {
        return paddingLength;
    }

    public int getPromisedStreamId() {
        return promisedStreamId;
    }
}
