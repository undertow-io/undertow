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

import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.api.BinaryFrameSender;
import io.undertow.websockets.api.SendCallback;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.streams.ChannelOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Default implementation of the {@link BinaryFrameSender}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class DefaultBinaryFrameSender implements BinaryFrameSender {
    protected final WebSocketChannelSession session;

    DefaultBinaryFrameSender(WebSocketChannelSession session) {
        this.session = session;
    }

    /**
     * Create a {@link StreamSinkChannel} which will be used to send the payload of the given size.
     *
     */
    protected StreamSinkChannel createSink(long payloadSize) throws IOException {
        return session.getChannel().send(WebSocketFrameType.BINARY, payloadSize);
    }

    @Override
    public void sendBinary(final ByteBuffer payload, final SendCallback callback) {
        try {
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(payload.remaining()));

            StreamSinkChannelUtils.send(sink, payload, callback);
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }

    @Override
    public void sendBinary(final ByteBuffer[] payload, final SendCallback callback) {
        try {
            final long length = StreamSinkChannelUtils.payloadLength(payload);
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(length));
            StreamSinkChannelUtils.send(sink, payload, callback);
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }

    @Override
    public void sendBinary(final FileChannel payloadChannel, final int offset, final long length, final SendCallback callback) {
        try{
            if (length > payloadChannel.size() - offset) {
                throw WebSocketMessages.MESSAGES.lengthBiggerThenFileChannel();
            }
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session,createSink(length));
            long written = 0;
            while (written < length) {
                long w = sink.transferFrom(payloadChannel, offset + written, length - written);
                if (w == 0) {
                    final long writtenBytes = written;
                    sink.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        long written = writtenBytes;
                        @Override
                        public void handleEvent(StreamSinkChannel sink) {
                            try {
                                while (written < length) {
                                    long w = sink.transferFrom(payloadChannel, offset + written, length - written);
                                    if (w == 0) {
                                        sink.resumeWrites();
                                        return;
                                    }
                                    if (w > 0) {
                                        written += w;
                                    }
                                }
                                StreamSinkChannelUtils.shutdownAndFlush(sink, callback);

                            } catch (IOException e) {
                                StreamSinkChannelUtils.safeNotify(callback, e);
                            }
                        }
                    });
                    sink.resumeWrites();
                    return;
                }
                if (w > 0) {
                    written += w;
                }

            }

            StreamSinkChannelUtils.shutdownAndFlush(sink, callback);
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }

    @Override
    public void sendBinary(ByteBuffer payload) throws IOException {
        StreamSinkChannel sink = createSink(payload.remaining());
        StreamSinkChannelUtils.send(sink, payload);

    }

    @Override
    public void sendBinary(ByteBuffer[] payload) throws IOException {
        long length = StreamSinkChannelUtils.payloadLength(payload);
        StreamSinkChannel sink = createSink(length);
        StreamSinkChannelUtils.send(sink, payload);
    }

    @Override
    public OutputStream sendBinary(long payloadSize) throws IOException {
        return new ChannelOutputStream(createSink(payloadSize));
    }
}
