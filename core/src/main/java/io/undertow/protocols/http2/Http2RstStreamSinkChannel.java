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

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.ImmediatePooledByteBuffer;

/**
 * @author Stuart Douglas
 */
class Http2RstStreamSinkChannel extends Http2NoDataStreamSinkChannel {

    public static final int HEADER_FIRST_LINE = (4 << 8) | (Http2Channel.FRAME_TYPE_RST_STREAM);
    private final int streamId;
    private final int errorCode;

    protected Http2RstStreamSinkChannel(Http2Channel channel, int streamId, int errorCode) {
        super(channel);
        this.errorCode = errorCode;
        this.streamId = streamId;
    }

    @Override
    protected SendFrameHeader createFrameHeader() {
        ByteBuffer buf = ByteBuffer.allocate(13);
        Http2ProtocolUtils.putInt(buf, HEADER_FIRST_LINE);
        buf.put((byte)0);
        Http2ProtocolUtils.putInt(buf, streamId);
        Http2ProtocolUtils.putInt(buf, errorCode);
        buf.flip();
        return new SendFrameHeader(new ImmediatePooledByteBuffer(buf));
    }

}
