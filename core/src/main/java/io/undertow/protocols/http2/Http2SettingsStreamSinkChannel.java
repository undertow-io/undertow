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
import java.util.List;
import io.undertow.connector.PooledByteBuffer;

import io.undertow.server.protocol.framed.SendFrameHeader;

/**
 * //TODO: ack
 *
 * @author Stuart Douglas
 */
public class Http2SettingsStreamSinkChannel extends Http2StreamSinkChannel {

    private final List<Http2Setting> settings;

    Http2SettingsStreamSinkChannel(Http2Channel channel, List<Http2Setting> settings) {
        super(channel, 0);
        this.settings = settings;
    }

    /**
     * //an ack frame
     *
     * @param channel
     */
    Http2SettingsStreamSinkChannel(Http2Channel channel) {
        super(channel, 0);
        this.settings = null;
    }

    @Override
    protected SendFrameHeader createFrameHeaderImpl() {
        PooledByteBuffer pooled = getChannel().getBufferPool().allocate();
        ByteBuffer currentBuffer = pooled.getBuffer();
        if (settings != null) {
            int size = settings.size() * 6;
            currentBuffer.put((byte) ((size >> 16) & 0xFF));
            currentBuffer.put((byte) ((size >> 8) & 0xFF));
            currentBuffer.put((byte) (size & 0xFF));
            currentBuffer.put((byte) Http2Channel.FRAME_TYPE_SETTINGS); //type
            currentBuffer.put((byte) 0); //flags
            Http2ProtocolUtils.putInt(currentBuffer, getStreamId());
            for (Http2Setting setting : settings) {
                currentBuffer.put((byte) ((setting.getId() >> 8) & 0xFF));
                currentBuffer.put((byte) (setting.getId() & 0xFF));

                currentBuffer.put((byte) ((setting.getValue() >> 24) & 0xFF));
                currentBuffer.put((byte) ((setting.getValue() >> 16) & 0xFF));
                currentBuffer.put((byte) ((setting.getValue() >> 8) & 0xFF));
                currentBuffer.put((byte) (setting.getValue() & 0xFF));
            }
        } else {

            currentBuffer.put((byte) 0);
            currentBuffer.put((byte) 0);
            currentBuffer.put((byte) 0);
            currentBuffer.put((byte) Http2Channel.FRAME_TYPE_SETTINGS); //type
            currentBuffer.put((byte) Http2Channel.SETTINGS_FLAG_ACK); //flags
            Http2ProtocolUtils.putInt(currentBuffer, getStreamId());
        }
        currentBuffer.flip();
        return new SendFrameHeader(pooled);
    }
}
