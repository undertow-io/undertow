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

import io.undertow.util.HeaderMap;

import java.nio.ByteBuffer;

/**
 * Push promise channel
 *
 * @author Stuart Douglas
 */
public class Http2PushPromiseStreamSinkChannel extends Http2DataStreamSinkChannel {

    private final int pushedStreamId;

    Http2PushPromiseStreamSinkChannel(Http2Channel channel,  HeaderMap requestHeaders, int associatedStreamId, int pushedStreamId) {
        super(channel, associatedStreamId, requestHeaders, Http2Channel.FRAME_TYPE_PUSH_PROMISE);
        this.pushedStreamId = pushedStreamId;
    }


    protected void writeBeforeHeaderBlock(ByteBuffer buffer) {
        buffer.put((byte) ((pushedStreamId >> 24) & 0xFF));
        buffer.put((byte) ((pushedStreamId >> 16) & 0xFF));
        buffer.put((byte) ((pushedStreamId >> 8) & 0xFF));
        buffer.put((byte) (pushedStreamId & 0xFF));
    }

    /**
     * this stream is not flow controlled
     * @param bytes
     * @return
     */
    protected int grabFlowControlBytes(int bytes) {
        return bytes;
    }

    public int getPushedStreamId() {
       return pushedStreamId;
    }

}
