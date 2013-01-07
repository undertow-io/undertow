/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package io.undertow.websockets.protocol.version07;

import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.WebSocketMessages;
import io.undertow.websockets.FixedPayloadFrameSourceChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket07CloseFrameSourceChannel extends FixedPayloadFrameSourceChannel {
    private final ByteBuffer status = ByteBuffer.allocate(2);
    private boolean statusValidated;
    private final Masker masker;
    enum State {
        EOF,
        DONE,
        VALIDATE
    }

    WebSocket07CloseFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket07Channel wsChannel, long payloadSize, int rsv, Masker masker) {
        // no fragmentation allowed per spec
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.CLOSE, payloadSize, rsv, true, masker, new UTF8Checker());
        this.masker = masker;
    }

    WebSocket07CloseFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocket07Channel wsChannel, long payloadSize, int rsv) {
        // no fragmentation allowed per spec
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.CLOSE, payloadSize, rsv, true, new UTF8Checker());
        masker = null;
    }

    @Override
    protected int read0(ByteBuffer dst) throws IOException {
        switch (validateStatus()) {
            case DONE:
                if (status.hasRemaining()) {
                    int copied = 0;
                    while(dst.hasRemaining() && status.hasRemaining()) {
                        dst.put(status.get());
                        copied++;
                    }
                    return copied;
                } else {
                    return super.read0(dst);
                }
            case EOF:
                return -1;
            default:
                return 0;
        }
    }

    @Override
    protected long read0(ByteBuffer[] dsts, int offset, int length) throws IOException {
        switch (validateStatus()) {
            case DONE:
                if (status.hasRemaining()) {
                    int copied = 0;
                    for (int i = offset; i < length; i++) {
                        ByteBuffer dst = dsts[i];
                        while(dst.hasRemaining() && status.hasRemaining()) {
                            dst.put(status.get());
                            copied++;
                        }
                        if (dst.hasRemaining()) {
                            return copied + super.read0(dsts, offset, length);
                        }
                    }

                    return copied;
                } else {
                    return super.read0(dsts, offset, length);
                }
            case EOF:
                return -1;
            default:
                return 0;
        }
    }

    private State validateStatus() throws IOException{
        if (statusValidated) {
            return State.DONE;
        }
        for (;;) {
            int r = super.read0(status);
            if (r == -1) {
                return State.EOF;
            }
            if (!status.hasRemaining()) {
                statusValidated = true;

                status.flip();
                // Must have 2 byte integer within the valid range
                int statusCode = status.getShort(0);

                if (statusCode >= 0 && statusCode <= 999 || statusCode >= 1004 && statusCode <= 1006
                        || statusCode >= 1012 && statusCode <= 2999) {
                    throw WebSocketMessages.MESSAGES.invalidCloseFrameStatusCode(statusCode);
                }
                return State.DONE;
            }
            if (r == 0) {
                return State.VALIDATE;
            }
        }
    }

    @Override
    protected void afterRead(ByteBuffer buffer, int position, int length) throws IOException {
        // not check for utf8 when read the status code
        if (!statusValidated) {
            if (masker != null) {
                masker.afterRead(buffer, position, length);
            }
            return;
        }
        super.afterRead(buffer, position, length);

    }
}
