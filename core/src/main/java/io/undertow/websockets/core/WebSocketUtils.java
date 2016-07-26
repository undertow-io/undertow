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
package io.undertow.websockets.core;

import io.undertow.UndertowLogger;
import org.xnio.Buffers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import io.undertow.connector.ByteBufferPool;
import io.undertow.util.Transfer;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

/**
 * Utility class which holds general useful utility methods which
 * can be used within WebSocket implementations.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class WebSocketUtils {

    private static final String EMPTY = "";

    /**
     * Create a {@link ByteBuffer} which holds the UTF8 encoded bytes for the
     * given {@link String}.
     *
     * @param utfString The {@link String} to convert
     * @return buffer   The {@link ByteBuffer} which was created
     */
    public static ByteBuffer fromUtf8String(CharSequence utfString) {
        if (utfString == null || utfString.length() == 0) {
            return Buffers.EMPTY_BYTE_BUFFER;
        } else {
            return ByteBuffer.wrap(utfString.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    public static String toUtf8String(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return EMPTY;
        }
        if (buffer.hasArray()) {
            return new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), StandardCharsets.UTF_8);
        } else {
            byte[] content = new byte[buffer.remaining()];
            buffer.get(content);
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    public static String toUtf8String(ByteBuffer... buffers) {
        int size = 0;
        for (ByteBuffer buf: buffers) {
            size += buf.remaining();
        }
        if (size == 0) {
            return EMPTY;
        }

        int index = 0;
        byte[] bytes = new byte[size];
        for (ByteBuffer buf: buffers) {
            if (buf.hasArray()) {
                int len = buf.remaining();
                System.arraycopy(buf.array(), buf.arrayOffset() + buf.position(), bytes, index, len);
                index += len;
            } else {
                int len = buf.remaining();
                buf.get(bytes, index, len);
                index += len;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Transfer the data from the source to the sink using the given through buffer to pass data through.
     */
    public static long transfer(final ReadableByteChannel source, final long count, final ByteBuffer throughBuffer, final WritableByteChannel sink) throws IOException {
        long total = 0L;
        while (total < count) {
            throughBuffer.clear();
            if (count - total < throughBuffer.remaining()) {
                throughBuffer.limit((int) (count - total));
            }

            try {
                long res = source.read(throughBuffer);
                if (res <= 0) {
                    return total == 0L ? res : total;
                }
            } finally {
                throughBuffer.flip();

            }
            while (throughBuffer.hasRemaining()) {
                long res = sink.write(throughBuffer);
                if (res <= 0) {
                    return total;
                }
                total += res;
            }
        }
        return total;
    }

    /**
     * Echo back the frame to the sender
     */
    public static void echoFrame(final WebSocketChannel channel, final StreamSourceFrameChannel ws) throws IOException {

        final WebSocketFrameType type;
        switch (ws.getType()) {
            case PONG:
                // pong frames must be discarded
                ws.close();
                return;
            case PING:
                // if a ping is send the autobahn testsuite expects a PONG when echo back
                type = WebSocketFrameType.PONG;
                break;
            default:
                type = ws.getType();
                break;
        }
        final StreamSinkFrameChannel sink = channel.send(type);
        sink.setRsv(ws.getRsv());
        initiateTransfer(ws, sink, new ChannelListener<StreamSourceFrameChannel>() {
                    @Override
                    public void handleEvent(StreamSourceFrameChannel streamSourceFrameChannel) {
                        IoUtils.safeClose(streamSourceFrameChannel);
                    }
                }, new ChannelListener<StreamSinkFrameChannel>() {
                    @Override
                    public void handleEvent(StreamSinkFrameChannel streamSinkFrameChannel) {
                        try {
                            streamSinkFrameChannel.shutdownWrites();
                        } catch (IOException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                            IoUtils.safeClose(streamSinkFrameChannel, channel);
                            return;
                        }
                        try {
                            if (!streamSinkFrameChannel.flush()) {
                                streamSinkFrameChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                                        new ChannelListener<StreamSinkFrameChannel>() {
                                            @Override
                                            public void handleEvent(StreamSinkFrameChannel streamSinkFrameChannel) {
                                                streamSinkFrameChannel.getWriteSetter().set(null);
                                                IoUtils.safeClose(streamSinkFrameChannel);
                                                if (type == WebSocketFrameType.CLOSE) {
                                                    IoUtils.safeClose(channel);
                                                }
                                            }
                                        }, new ChannelExceptionHandler<StreamSinkFrameChannel>() {
                                            @Override
                                            public void handleException(StreamSinkFrameChannel streamSinkFrameChannel, IOException e) {

                                                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                                                IoUtils.safeClose(streamSinkFrameChannel, channel);

                                            }
                                        }
                                ));
                                streamSinkFrameChannel.resumeWrites();
                            } else {
                                if (type == WebSocketFrameType.CLOSE) {
                                    IoUtils.safeClose(channel);
                                }
                                streamSinkFrameChannel.getWriteSetter().set(null);
                                IoUtils.safeClose(streamSinkFrameChannel);
                            }
                        } catch (IOException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                            IoUtils.safeClose(streamSinkFrameChannel, channel);

                        }
                    }
                }, new ChannelExceptionHandler<StreamSourceFrameChannel>() {
                    @Override
                    public void handleException(StreamSourceFrameChannel streamSourceFrameChannel, IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        IoUtils.safeClose(streamSourceFrameChannel, channel);
                    }
                }, new ChannelExceptionHandler<StreamSinkFrameChannel>() {
                    @Override
                    public void handleException(StreamSinkFrameChannel streamSinkFrameChannel, IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        IoUtils.safeClose(streamSinkFrameChannel, channel);
                    }
                }, channel.getBufferPool()
        );

    }

    /**
     * Initiate a low-copy transfer between two stream channels.  The pool should be a direct buffer pool for best
     * performance.
     *
     * @param source                the source channel
     * @param sink                  the target channel
     * @param sourceListener        the source listener to set and call when the transfer is complete, or {@code null} to clear the listener at that time
     * @param sinkListener          the target listener to set and call when the transfer is complete, or {@code null} to clear the listener at that time
     * @param readExceptionHandler  the read exception handler to call if an error occurs during a read operation
     * @param writeExceptionHandler the write exception handler to call if an error occurs during a write operation
     * @param pool                  the pool from which the transfer buffer should be allocated
     */
    @Deprecated
    public static <I extends StreamSourceChannel, O extends StreamSinkChannel> void initiateTransfer(final I source, final O sink, final ChannelListener<? super I> sourceListener, final ChannelListener<? super O> sinkListener, final ChannelExceptionHandler<? super I> readExceptionHandler, final ChannelExceptionHandler<? super O> writeExceptionHandler, ByteBufferPool pool) {
        Transfer.initiateTransfer(source, sink, sourceListener, sinkListener, readExceptionHandler, writeExceptionHandler, pool);
    }

    private WebSocketUtils() {
        // utility class
    }
}
