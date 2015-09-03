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

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.ImmediatePooledByteBuffer;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
class SpdyGoAwayStreamSinkChannel extends SpdyControlFrameStreamSinkChannel {

    private final int status;
    private final int lastGoodStreamId;

    protected SpdyGoAwayStreamSinkChannel(SpdyChannel channel, int status, int lastGoodStreamId) {
        super(channel);
        this.status = status;
        this.lastGoodStreamId = lastGoodStreamId;
    }

    @Override
    protected SendFrameHeader createFrameHeader() {
        ByteBuffer buf = ByteBuffer.allocate(16);

        int firstInt = SpdyChannel.CONTROL_FRAME | (getChannel().getSpdyVersion() << 16) | 7;
        SpdyProtocolUtils.putInt(buf, firstInt);
        SpdyProtocolUtils.putInt(buf, 8);
        SpdyProtocolUtils.putInt(buf, lastGoodStreamId);
        SpdyProtocolUtils.putInt(buf, status);
        buf.flip();
        return new SendFrameHeader( new ImmediatePooledByteBuffer(buf));
    }

    @Override
    protected boolean isLastFrame() {
        return true;
    }
}
