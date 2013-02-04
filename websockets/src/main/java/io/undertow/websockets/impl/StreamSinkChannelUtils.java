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

import io.undertow.websockets.api.SendCallback;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;

/**
 * Utility class for internal usage.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class StreamSinkChannelUtils {

    /**
     * Shutdown and flush the given {@link StreamSinkChannel} and notify the given {@link SendCallback}.
     */
    public static void shutdownAndFlush(StreamSinkChannel sink, final SendCallback callback) {
        try {
            sink.shutdownWrites();
            if (!sink.flush()) {
                sink.getWriteSetter().set(
                        ChannelListeners.flushingChannelListener(
                                new ChannelListener<StreamSinkChannel>() {
                                    @Override
                                    public void handleEvent(StreamSinkChannel sink) {
                                        try {
                                            sink.close();
                                            safeNotify(callback, null);
                                        } catch (IOException e) {
                                            safeNotify(callback, e);
                                        }
                                    }
                                }, new ChannelExceptionHandler<Channel>() {
                                    @Override
                                    public void handleException(Channel channel, IOException e) {
                                        safeNotify(callback, e);
                                        IoUtils.safeClose(channel);
                                    }
                                }
                        ));
                sink.resumeWrites();
            } else {
                sink.close();
                safeNotify(callback, null);
            }
        } catch (IOException e) {
            safeNotify(callback, e);
        }
    }

    /**
     * Return the payload size which take all given {@link ByteBuffer} into account.
     */
    public static long payloadLength(ByteBuffer... bufs) {
        if (bufs == null) {
            return 0;
        }
        long length = 0;
        for (ByteBuffer buf: bufs) {
            length += buf.remaining();
        }
        return length;
    }

    /**
     * Notify the given {@link SendCallback} if not {@code null}.
     *
     * @param callback  the {@link SendCallback} to notify
     * @param cause     if {code null} is used it will call {@link SendCallback#onCompletion()}
     *                  otherwise {@link SendCallback#onError(Throwable)}
     */
    public static void safeNotify(SendCallback callback, Throwable cause) {
        if (callback == null) {
            return;
        }
        if (cause == null) {
            callback.onCompletion();
        } else {
            callback.onError(cause);
        }
    }

    public static void send(StreamSinkChannel sink, final ByteBuffer payload, final SendCallback callback) {
        try {
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
                                shutdownAndFlush(sink, callback);
                            } catch (IOException e) {
                                safeNotify(callback, e);
                            }
                        }
                    });
                    sink.resumeWrites();
                    return;
                }
            }
            shutdownAndFlush(sink, callback);

        } catch (IOException e) {
            safeNotify(callback, e);
        }
    }

    public static void send(StreamSinkChannel sink, final ByteBuffer[] payload, final SendCallback callback) {
        try {
            final long length = payloadLength(payload);
            long written = 0;

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
                                shutdownAndFlush(sink, callback);
                            } catch (IOException e) {
                                safeNotify(callback, e);
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
            shutdownAndFlush(sink, callback);

        } catch (IOException e) {
            safeNotify(callback, e);
        }
    }

    public static void send(StreamSinkChannel sink, ByteBuffer payload) throws IOException {
        FlushingBlockingWritableByteChannel channel = new FlushingBlockingWritableByteChannel(sink);
        while(payload.hasRemaining()) {
            channel.write(payload);
        }
        channel.close();
    }

    public static void send(StreamSinkChannel sink, ByteBuffer[] payload) throws IOException {
        long length = payloadLength(payload);
        FlushingBlockingWritableByteChannel channel = new FlushingBlockingWritableByteChannel(sink);
        long written = 0;
        while(written < length) {
            long w = channel.write(payload);
            if (w > 0) {
                written += w;
            }
        }
        channel.close();
    }

    public static StreamSinkChannel applyAsyncSendTimeout(WebSocketChannelSession session, StreamSinkChannel sink) {
        int asyncSendtime = session.getAsyncSendTimeout();
        if (asyncSendtime > 0) {
            return new AsyncSendTimeoutStreamChannelSink(session.getChannel(), sink, asyncSendtime);
        }
        return sink;
    }

    private StreamSinkChannelUtils() {
        // utility
    }
}
