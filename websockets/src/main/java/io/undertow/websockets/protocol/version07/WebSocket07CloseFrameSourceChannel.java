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
import io.undertow.websockets.utf8.UTF8Checker;
import io.undertow.websockets.utf8.UTF8FixedPayloadMaskedFrameSourceChannel;
import org.xnio.Pooled;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07CloseFrameSourceChannel extends UTF8FixedPayloadMaskedFrameSourceChannel {
    private final ByteBuffer status = ByteBuffer.allocate(2);
    private boolean statusValidated;
    enum State {
        EOF,
        DONE,
        VALIDATE
    }

    WebSocket07CloseFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, PushBackStreamChannel channel, WebSocket07Channel wsChannel, long payloadSize, int rsv, final boolean masked, final int mask) {
        // no fragmentation allowed per spec
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.CLOSE, payloadSize, rsv, true, masked, mask, new UTF8Checker());
    }

    @Override
    protected long transferTo0(long position, long count, FileChannel target) throws IOException {
        if (count == 0) {
            return 0;
        }

        // Set the position of the channel
        target.position(position);

        boolean free = true;
        Pooled<ByteBuffer> pooled = wsChannel.getBufferPool().allocate();
        try {
            ByteBuffer buf = pooled.getResource();
            // clear the buffer before use it
            buf.clear();

            long r = 0;
            while (r < count) {
                int remaining = (int) (count - r);
                if (remaining < buf.limit()) {
                    // we have left less to read as the limit of the buffer, so adjust it
                    buf.limit(remaining);
                }
                // read into the buffer and flip it. It's not that effective but
                // I can not think of a
                // better way that would us allow to detect the end of the frame
                if (read0(buf) > 0) {
                    buf.flip();

                    while (buf.hasRemaining()) {
                        int written = target.write(buf);
                        if (written == 0) {
                            if (buf.hasRemaining()) {
                                // nothing could be written and the buffer has something left in there, so push it back to the channel
                                ((PushBackStreamChannel) channel).unget(pooled);
                                free = false;
                            }
                            return r;
                        } else {
                            r += written;
                        }
                    }
                    // Clear the buffer so it can get used for writing again
                    buf.clear();

                    // check if the read operation marked it as complete and if so just return
                    // now
                    if (isComplete()) {
                        return r;
                    }
                } else {
                    return r;
                }
            }
            return r;
        } finally {
            if (free) {
                // free the pooled resource again
                pooled.free();
            }
        }
    }

    @Override
    public long transferTo0(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        return transfer(this, count, throughBuffer, target);
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
    protected long read0(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
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
    protected final void checkUTF8(ByteBuffer buffer) throws IOException {
        // not check for utf8 when read the status code
        if (!statusValidated) {
            return;
        }
        super.checkUTF8(buffer);
    }
}
