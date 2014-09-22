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

/**
 * Parser for HTTP2 GO_AWAY frames
 *
 * @author Stuart Douglas
 */
public class Http2GoAwayParser extends Http2PushBackParser {

    private int statusCode;
    private int lastGoodStreamId;

    public Http2GoAwayParser(int frameLength) {
        super(frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource, Http2FrameHeaderParser headerParser) {
        if (resource.remaining() < 8) {
            return;
        }
        lastGoodStreamId = Http2ProtocolUtils.readInt(resource);
        statusCode = Http2ProtocolUtils.readInt(resource);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getLastGoodStreamId() {
        return lastGoodStreamId;
    }
}
