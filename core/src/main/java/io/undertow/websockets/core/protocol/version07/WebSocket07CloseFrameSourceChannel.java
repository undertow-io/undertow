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
package io.undertow.websockets.core.protocol.version07;

import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.connector.PooledByteBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket07CloseFrameSourceChannel extends StreamSourceFrameChannel {

    WebSocket07CloseFrameSourceChannel(WebSocket07Channel wsChannel, int rsv, Masker masker, PooledByteBuffer pooled, long frameLength) {
        // no fragmentation allowed per spec
        super(wsChannel, WebSocketFrameType.CLOSE, rsv, true, pooled, frameLength, masker, new CloseFrameValidatorChannelFunction(wsChannel));
    }

    WebSocket07CloseFrameSourceChannel(WebSocket07Channel wsChannel, int rsv, PooledByteBuffer pooled, long frameLength) {
        // no fragmentation allowed per spec
        super(wsChannel, WebSocketFrameType.CLOSE, rsv, true, pooled, frameLength, null, new CloseFrameValidatorChannelFunction(wsChannel));
    }

    public static class CloseFrameValidatorChannelFunction extends UTF8Checker {

        private final WebSocket07Channel wsChannel;
        private int statusBytesRead;
        private int status;

        CloseFrameValidatorChannelFunction(WebSocket07Channel wsChannel) {
            this.wsChannel = wsChannel;
        }


        @Override
        public void afterRead(ByteBuffer buf, int position, int length) throws IOException {
            int i = 0;
            if(statusBytesRead < 2) {
                while (statusBytesRead < 2 && i < length) {
                    status <<= 8;
                    status += buf.get(position + i) & 0xFF;
                    statusBytesRead ++;
                    ++i;
                }
                if(statusBytesRead == 2) {
                    // Must have 2 byte integer within the valid range
                    if (status >= 0 && status <= 999 || status >= 1004 && status <= 1006
                            || status >= 1012 && status <= 2999 || status >= 5000) {
                        IOException exception =  WebSocketMessages.MESSAGES.invalidCloseFrameStatusCode(status);
                        wsChannel.markReadsBroken(exception);
                        throw exception;
                    }
                }
            }
            super.afterRead(buf, position + i, length - i);
        }
    }

}
