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
 * Parser for HTTP2 window update frames
 *
 * @author Stuart Douglas
 */
class Http2PriorityParser extends Http2PushBackParser {

    private int streamDependency;
    private int weight;
    private boolean exclusive;

    Http2PriorityParser(int frameLength) {
        super(frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource, Http2FrameHeaderParser frameHeaderParser) {
        if (resource.remaining() < 5) {
            return;
        }
        int read = Http2ProtocolUtils.readInt(resource);
        if(Bits.anyAreSet(read, 1 << 31)) {
            exclusive = true;
            streamDependency = read & ~(1 << 31);
        } else {
            exclusive = false;
            streamDependency = read;
        }
        weight = resource.get();
    }

    public int getWeight() {
        return weight;
    }

    public int getStreamDependency() {
        return streamDependency;
    }

    public boolean isExclusive() {
        return exclusive;
    }
}
