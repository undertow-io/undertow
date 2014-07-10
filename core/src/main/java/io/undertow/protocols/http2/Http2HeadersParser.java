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

import java.nio.ByteBuffer;
import org.xnio.Bits;

/**
 * Parser for HTTP2 Headers frames
 *
 * @author Stuart Douglas
 */
class Http2HeadersParser extends Http2HeaderBlockParser {

    private static final int DEPENDENCY_MASK = ~(1 << 7);
    private int paddingLength = 0;
    private int dependentStreamId = 0;
    private int weight;

    public Http2HeadersParser(int frameLength, HpackDecoder hpackDecoder) {
        super(frameLength, hpackDecoder);
    }

    @Override
    protected boolean handleBeforeHeader(ByteBuffer resource, Http2FrameHeaderParser headerParser) {
        boolean hasPadding = Bits.anyAreSet(headerParser.flags, Http2Channel.HEADERS_FLAG_PADDED);
        boolean hasPriority = Bits.anyAreSet(headerParser.flags, Http2Channel.HEADERS_FLAG_PRIORITY);
        int reqLength = (hasPadding ? 1 : 0) + (hasPriority ? 5 : 0);
        if (reqLength == 0) {
            return true;
        }
        if (resource.remaining() < reqLength) {
            return false;
        }
        if (hasPadding) {
            paddingLength = (resource.get() & 0xFF);
        }
        if (hasPriority) {
            if (resource.remaining() < 4) {
                return false;
            }
            dependentStreamId = (resource.get() & DEPENDENCY_MASK & 0xFF) << 24;
            dependentStreamId += (resource.get() & 0xFF) << 16;
            dependentStreamId += (resource.get() & 0xFF) << 8;
            dependentStreamId += (resource.get() & 0xFF);
            weight = resource.get() & 0xFF;
        }
        return true;
    }

    int getPaddingLength() {
        return paddingLength;
    }

    int getDependentStreamId() {
        return dependentStreamId;
    }

    int getWeight() {
        return weight;
    }
}