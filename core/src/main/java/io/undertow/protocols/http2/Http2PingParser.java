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

import io.undertow.UndertowMessages;

import static io.undertow.protocols.http2.Http2Channel.PING_FRAME_LENGTH;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Parser for HTTP2 ping frames.
 *
 * @author Stuart Douglas
 */
class Http2PingParser extends Http2PushBackParser {

    final byte[] data = new byte[PING_FRAME_LENGTH];

    Http2PingParser(int frameLength) {
        super(frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource, Http2FrameHeaderParser parser) throws IOException {
        if(parser.length != PING_FRAME_LENGTH) {
            throw new IOException(UndertowMessages.MESSAGES.httpPingDataMustBeLength8());
        }
        if(parser.streamId != 0) {
            throw new IOException(UndertowMessages.MESSAGES.streamIdMustBeZeroForFrameType(Http2Channel.FRAME_TYPE_PING));
        }
        if (resource.remaining() < PING_FRAME_LENGTH) {
            return;
        }
        resource.get(data);
    }

    byte[] getData() {
        return data;
    }
}
