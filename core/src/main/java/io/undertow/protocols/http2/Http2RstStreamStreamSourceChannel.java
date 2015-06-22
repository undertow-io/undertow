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

/**
 * A HTTP2 RST Stream channel
 *
 * @author Stuart Douglas
 */
public class Http2RstStreamStreamSourceChannel extends AbstractHttp2StreamSourceChannel {

    private final int errorCode;
    private final int streamId;

    Http2RstStreamStreamSourceChannel(Http2Channel framedChannel, PooledByteBuffer data, int errorCode, int streamId) {
        super(framedChannel, data, 0);
        this.errorCode = errorCode;
        this.streamId = streamId;
        lastFrame();
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getStreamId() {
        return streamId;
    }
}
