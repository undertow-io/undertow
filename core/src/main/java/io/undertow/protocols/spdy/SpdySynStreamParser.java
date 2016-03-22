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

package io.undertow.protocols.spdy;

import io.undertow.connector.ByteBufferPool;

import java.nio.ByteBuffer;
import java.util.zip.Inflater;

/**
 * Parser for SPDY syn stream frames
 *
 * @author Stuart Douglas
 */
class SpdySynStreamParser extends SpdyHeaderBlockParser {

    private static final int STREAM_ID_MASK = ~(1 << 7);
    private int associatedToStreamId = -1;
    private int priority = -1;

    SpdySynStreamParser(ByteBufferPool bufferPool, SpdyChannel channel, int frameLength, Inflater inflater) {
        super(channel, frameLength, inflater);
    }

    protected boolean handleBeforeHeader(ByteBuffer resource) {
        if (streamId == -1) {
            if (resource.remaining() < 4) {
                return false;
            }
            streamId = (resource.get() & STREAM_ID_MASK & 0xFF) << 24;
            streamId += (resource.get() & 0xFF) << 16;
            streamId += (resource.get() & 0xFF) << 8;
            streamId += (resource.get() & 0xFF);
        }
        if (associatedToStreamId == -1) {
            if (resource.remaining() < 4) {
                return false;
            }
            associatedToStreamId = (resource.get() & STREAM_ID_MASK & 0xFF) << 24;
            associatedToStreamId += (resource.get() & 0xFF) << 16;
            associatedToStreamId += (resource.get() & 0xFF) << 8;
            associatedToStreamId += (resource.get() & 0xFF);
        }
        if (priority == -1) {
            if (resource.remaining() < 2) {
                return false;
            }
            priority = (resource.get() >> 5) & 0xFF;
            resource.get(); //unused at the moment
        }
        return true;
    }

    public int getAssociatedToStreamId() {
        return associatedToStreamId;
    }

    public int getPriority() {
        return priority;
    }
}
