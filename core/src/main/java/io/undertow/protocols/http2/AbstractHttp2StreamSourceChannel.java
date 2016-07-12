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

import io.undertow.connector.PooledByteBuffer;

import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;

/**
 * HTTP2 stream source channel
 *
 * @author Stuart Douglas
 */
public class AbstractHttp2StreamSourceChannel extends AbstractFramedStreamSourceChannel<Http2Channel, AbstractHttp2StreamSourceChannel, AbstractHttp2StreamSinkChannel> {

    AbstractHttp2StreamSourceChannel(Http2Channel framedChannel) {
        super(framedChannel);
    }

    AbstractHttp2StreamSourceChannel(Http2Channel framedChannel, PooledByteBuffer data, long frameDataRemaining) {
        super(framedChannel, data, frameDataRemaining);
    }

    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        //by default we do nothing
    }

    @Override
    protected Http2Channel getFramedChannel() {
        return super.getFramedChannel();
    }

    public Http2Channel getHttp2Channel() {
        return getFramedChannel();
    }

    @Override
    protected void lastFrame() {
        super.lastFrame();
    }

    void rstStream() {
        rstStream(Http2Channel.ERROR_CANCEL);
    }

    void rstStream(int error) {
        //noop by default
    }


}
