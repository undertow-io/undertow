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

import org.xnio.Bits;
import io.undertow.connector.PooledByteBuffer;

import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;

/**
 * SPDY stream source channel
 *
 * @author Stuart Douglas
 */
public class SpdyStreamSourceChannel extends AbstractFramedStreamSourceChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> {

    SpdyStreamSourceChannel(SpdyChannel framedChannel) {
        super(framedChannel);
    }

    SpdyStreamSourceChannel(SpdyChannel framedChannel, PooledByteBuffer data, long frameDataRemaining) {
        super(framedChannel, data, frameDataRemaining);
    }

    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        SpdyChannel.SpdyFrameParser data = (SpdyChannel.SpdyFrameParser) headerData;
        if(Bits.anyAreSet(data.flags, SpdyChannel.FLAG_FIN)) {
            this.lastFrame();
        }
    }

    public SpdyChannel getSpdyChannel() {
        return getFramedChannel();
    }

    @Override
    protected void lastFrame() {
        super.lastFrame();
    }

    void rstStream() {
        //noop by default
    }


}
