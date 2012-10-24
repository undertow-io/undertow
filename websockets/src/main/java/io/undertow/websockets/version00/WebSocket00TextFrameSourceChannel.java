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

package io.undertow.websockets.version00;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import io.undertow.websockets.WebSocketChannel;
import org.xnio.Pooled;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketFrameType;

/**
 * {@link StreamSourceFrameChannel} to read Frames of type {@link WebSocketFrameType#TEXT}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class WebSocket00TextFrameSourceChannel extends StreamSourceFrameChannel {

    private final byte END_FRAME_MARKER = (byte) 0xFF;
    private boolean complete = false;

    WebSocket00TextFrameSourceChannel(WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl,PushBackStreamChannel channel, WebSocket00Channel wsChannel) {
        super(streamSourceChannelControl, channel, wsChannel, WebSocketFrameType.TEXT);
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        if (complete) {
            return -1;
        }

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
                if (read(buf) > 0) {
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
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if (complete) {
            return -1;
        }

        try {
            // clear the buffer
            throughBuffer.clear();

            if (count < throughBuffer.limit()) {
                throughBuffer.limit((int) count);
            }

            long r = 0;
            while (r < count) {
                int i = read(throughBuffer);
                if (r == 0 && r == -1) {
                    return -1;
                }

                if (i < 1) {
                    return r;
                }
                throughBuffer.flip();

                while (throughBuffer.hasRemaining()) {
                    int written = target.write(throughBuffer);
                    if (written == 0) {
                        return r;
                    } else {
                        r += written;
                    }
                }
                throughBuffer.clear();
                long toRead = r - count;
                if (toRead < throughBuffer.limit()) {
                    // the rest which needs to be read is smaller as the buffers
                    // limit, so set it
                    // to make sure we not read to much
                    throughBuffer.limit((int) toRead);
                }
            }

            return r;
        } finally {
            // flip it
            throughBuffer.flip();
        }
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        if (complete) {
            return -1;
        }

        int pos = buf.position();
        int r = channel.read(buf);
        int limit = pos + r;

        if (r == 1) {
            if (buf.get(pos) == END_FRAME_MARKER) {
                complete = true;
                // frame was complete to just set the position to the limit
                buf.position(pos + 1);
                return -1;
            }
        } else if (r > 1) {
            while (pos < limit) {
                if (buf.get(pos) == END_FRAME_MARKER) {
                    complete = true;

                    if (pos + 1 < r) {
                        ByteBuffer remainingBytes;
                        if (pos == 0) {
                            remainingBytes = (ByteBuffer) buf.duplicate().position(pos + 1).limit(buf.limit());
                        } else {
                            remainingBytes = (ByteBuffer) buf.duplicate().position(pos + 1).limit(buf.limit() - pos + 1);
                        }

                        // Set the new position so that once the buffer is flipped it will be the new limit
                        buf.position(pos);

                        Pooled<ByteBuffer> pooled = wsChannel.getBufferPool().allocate();
                        ByteBuffer pooledBuf = pooled.getResource();
                        pooledBuf.clear();

                        boolean failed = true;

                        try {

                            pooledBuf.put(remainingBytes).flip();

                            // push back the bytes that not belong to the frame
                            ((PushBackStreamChannel) channel).unget(pooled);
                            failed = false;

                        } finally {
                            if (failed) {
                                // for whatever reason it failed to hand the bytes back to the stream, free the pooled buffer
                                // to not run into a leak
                                pooled.free();

                                // What we should do here now that it was failed ?
                                // I think closing the channel would probably make sense as the channel is
                                // unusable
                                // TODO: Fix me
                            }
                        }
                        if (pos == 0) {
                            return -1;
                        } else {
                            return pos;
                        }
                    } else {
                        // Set the new position so that once the buffer is flipped it will be the new limit
                        buf.position(pos);
                    }

                    // return the read bytes
                    return r - pos + 1;
                }
                pos++;
            }
            return r;

        }
        return r;
    }

    @Override
    public long read(ByteBuffer[] bufs) throws IOException {
        return read(bufs, 0, bufs.length);
    }

    @Override
    public long read(ByteBuffer[] bufs, int index, int length) throws IOException {
        if (complete) {
            return -1;
        }

        long r = 0;
        while (index < length) {
            int i = read(bufs[index++]);
            if (i > 0) {
                r = +i;
            } else {
                break;
            }
        }
        return r;
    }
}
