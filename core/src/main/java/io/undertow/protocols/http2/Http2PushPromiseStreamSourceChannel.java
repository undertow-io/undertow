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
import io.undertow.connector.PooledByteBuffer;

/**
 * A HTTP2 push promise frame
 *
 * @author Stuart Douglas
 */
public class Http2PushPromiseStreamSourceChannel extends AbstractHttp2StreamSourceChannel {

    private final HeaderMap headers;
    private final int pushedStreamId;
    private final int associatedStreamId;

    Http2PushPromiseStreamSourceChannel(Http2Channel framedChannel, PooledByteBuffer data, long frameDataRemaining, HeaderMap headers, int pushedStreamId, int associatedStreamId) {
        super(framedChannel, data, frameDataRemaining);
        this.headers = headers;
        this.pushedStreamId = pushedStreamId;
        this.associatedStreamId = associatedStreamId;
        lastFrame();
    }

    public HeaderMap getHeaders() {
        return headers;
    }

    public int getPushedStreamId() {
        return pushedStreamId;
    }

    public int getAssociatedStreamId() {
        return associatedStreamId;
    }
}
