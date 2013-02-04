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

import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.api.FragmentedTextFrameSender;
import io.undertow.websockets.api.SendCallback;
import org.xnio.ChannelListener;
import org.xnio.channels.BlockingWritableByteChannel;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Default {@link FragmentedTextFrameSender} implementation which use a {@link WebSocketChannel} for the write
 * operations.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultFragmentedTextFrameSender extends DefaultTextFrameSender implements FragmentedTextFrameSender {

    private boolean firstFragment = true;
    private boolean finalFragment;
    private boolean finalFragmentStarted;

    DefaultFragmentedTextFrameSender(WebSocketChannelSession session) {
        super(session);
    }

    @Override
    protected StreamSinkFrameChannel createSink(long payloadSize) throws IOException {
        if (finalFragmentStarted) {
            throw WebSocketMessages.MESSAGES.fragmentedSenderCompleteAlready();
        }
        if (finalFragment) {
            finalFragmentStarted = true;
        }

        StreamSinkFrameChannel sink;
        if (firstFragment) {
            firstFragment = false;
            sink = session.getChannel().send(WebSocketFrameType.TEXT, payloadSize);
        } else {
            sink = session.getChannel().send(WebSocketFrameType.CONTINUATION, payloadSize);
        }
        sink.setFinalFragment(finalFragment);
        if (finalFragment) {
            sink.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
                @Override
                public void handleEvent(StreamSinkChannel channel) {
                    session.complete(DefaultFragmentedTextFrameSender.this);
                }
            });
        }
        return sink;
    }

    @Override
    public void finalFragment() {
        finalFragment = true;
    }


    @Override
    public void sendText(final ByteBuffer payload, final SendCallback callback) {
        try {
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(payload.remaining()));
            while (payload.hasRemaining()) {
                if (sink.write(payload) == 0) {
                    sink.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(StreamSinkChannel sink) {
                            try {
                                while (payload.hasRemaining()) {
                                    if (sink.write(payload) == 0) {
                                        sink.resumeWrites();
                                        return;
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
            }
            StreamSinkChannelUtils.shutdownAndFlush(sink, callback);

        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }

    @Override
    public void sendText(final ByteBuffer[] payload, final SendCallback callback) {
        try {
            final long length = StreamSinkChannelUtils.payloadLength(payload);
            long written = 0;
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(length));

            while (written < length) {
                long w = sink.write(payload);
                if (w == 0) {
                    final long writtenBytes = written;
                    sink.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        long written = writtenBytes;

                        @Override
                        public void handleEvent(StreamSinkChannel sink) {
                            try {
                                while (written < length) {
                                    long w = sink.write(payload);
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
                    written +=w;
                }
            }
            StreamSinkChannelUtils.shutdownAndFlush(sink, callback);

        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }
    @Override
    public void sendText(ByteBuffer payload) throws IOException {
        StreamSinkChannel sink = createSink(payload.remaining());
        BlockingWritableByteChannel channel = new BlockingWritableByteChannel(sink);
        while(payload.hasRemaining()) {
            channel.write(payload);
        }
        sink.shutdownWrites();
        channel.flush();
        channel.close();
    }

    @Override
    public void sendText(ByteBuffer[] payload) throws IOException {
        long length = StreamSinkChannelUtils.payloadLength(payload);
        StreamSinkChannel sink = createSink(length);
        BlockingWritableByteChannel channel = new BlockingWritableByteChannel(sink);
        long written = 0;
        while(written < length) {
            long w = channel.write(payload);
            if (w > 0) {
                written += w;
            }
        }
        sink.shutdownWrites();
        channel.flush();
        channel.close();
    }
}
