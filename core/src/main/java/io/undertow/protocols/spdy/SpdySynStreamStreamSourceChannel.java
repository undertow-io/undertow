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

import io.undertow.util.HeaderMap;
import io.undertow.connector.PooledByteBuffer;

import java.util.zip.Deflater;

/**
 * @author Stuart Douglas
 */
public class SpdySynStreamStreamSourceChannel extends SpdyStreamStreamSourceChannel {

    private SpdySynReplyStreamSinkChannel synResponse;
    private final Deflater deflater;

    SpdySynStreamStreamSourceChannel(SpdyChannel framedChannel, PooledByteBuffer data, long frameDataRemaining, Deflater deflater, HeaderMap headers, int streamId) {
        super(framedChannel, data, frameDataRemaining, headers, streamId);
        this.deflater = deflater;
    }

    public SpdySynReplyStreamSinkChannel getResponseChannel() {
        if(synResponse != null) {
            return synResponse;
        }
        synResponse = new SpdySynReplyStreamSinkChannel(getSpdyChannel(), getStreamId(), deflater);
        getSpdyChannel().registerStreamSink(synResponse);
        return synResponse;
    }
}
