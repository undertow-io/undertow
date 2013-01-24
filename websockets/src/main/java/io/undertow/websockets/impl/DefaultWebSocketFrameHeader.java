/*
 * Copyright 2013 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.impl;

import io.undertow.websockets.api.WebSocketFrameHeader;
import io.undertow.websockets.core.WebSocketFrameType;

/**
 * Default implementation of {@link DefaultWebSocketFrameHeader}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultWebSocketFrameHeader implements WebSocketFrameHeader {
    private final FrameType type;
    private final int rsv;
    private final boolean last;

    public DefaultWebSocketFrameHeader(WebSocketFrameType type, int rsv, boolean last) {
        this.type = type(type);
        this.rsv = rsv;
        this.last = last;
    }

    private static FrameType type(WebSocketFrameType type) {
        switch(type) {
            case BINARY:
                return FrameType.BINARY;
            case TEXT:
                return FrameType.TEXT;
            case CONTINUATION:
                return FrameType.CONTINUATION;
            default:
                throw new IllegalArgumentException();

        }
    }

    @Override
    public FrameType getType() {
        return type;
    }

    @Override
    public int getRsv() {
        return rsv;
    }

    @Override
    public boolean isLastFragement() {
        return last;
    }
}
