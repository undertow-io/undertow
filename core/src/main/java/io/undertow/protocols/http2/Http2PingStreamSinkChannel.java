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

import static io.undertow.protocols.http2.Http2Channel.PING_FRAME_LENGTH;

import java.nio.ByteBuffer;

import io.undertow.UndertowMessages;
import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.ImmediatePooled;

/**
 * @author Stuart Douglas
 */
class Http2PingStreamSinkChannel extends Http2NoDataStreamSinkChannel {

    public static final int HEADER_NO_ACK = (PING_FRAME_LENGTH << 8) | (Http2Channel.FRAME_TYPE_PING);
    public static final int HEADER_ACK = (PING_FRAME_LENGTH << 16) | (Http2Channel.FRAME_TYPE_PING << 8) | Http2Channel.PING_FLAG_ACK;
    private final byte[] data;
    private final boolean ack;

    protected Http2PingStreamSinkChannel(Http2Channel channel, byte[] data, boolean ack) {
        super(channel);
        if (data.length != PING_FRAME_LENGTH) {
            throw new IllegalArgumentException(UndertowMessages.MESSAGES.httpPingDataMustBeLength8());
        }
        this.data = data;
        this.ack = ack;
    }

    @Override
    protected SendFrameHeader createFrameHeader() {
        ByteBuffer buf = ByteBuffer.allocate(16);
        int firstInt = ack ? HEADER_ACK : HEADER_NO_ACK;
        Http2ProtocolUtils.putInt(buf, firstInt);
        buf.put((byte) 0);
        Http2ProtocolUtils.putInt(buf, 0); //stream id, must be zero
        for (int i = 0; i < PING_FRAME_LENGTH; ++i) {
            buf.put(data[i]);
        }
        return new SendFrameHeader(new ImmediatePooled<>(buf));
    }

}
