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

import org.xnio.Buffers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static org.xnio.ChannelListeners.flushingChannelListener;

/**
 * @author Stuart Douglas
 */
public class WebSockets {

    private static final Charset utf8 = Charset.forName("UTF-8");

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     * @param callback
     */
    public static void sendText(final String message, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        final ByteBuffer data = ByteBuffer.wrap(message.getBytes(utf8));
        sendInternal(new ByteBuffer[]{data}, WebSocketFrameType.TEXT, wsChannel, callback);
    }


    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     * @param callback
     */
    public static void sendText(final ByteBuffer message, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(new ByteBuffer[]{message}, WebSocketFrameType.TEXT, wsChannel, callback);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     */
    public static void sendTextBlocking(final String message, final WebSocketChannel wsChannel) throws IOException {
        final ByteBuffer data = ByteBuffer.wrap(message.getBytes(utf8));
        sendBlockingInternal(new ByteBuffer[]{data}, WebSocketFrameType.TEXT, wsChannel);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     */
    public static void sendTextBlocking(final ByteBuffer message, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(new ByteBuffer[]{message}, WebSocketFrameType.TEXT, wsChannel);
    }

    /**
     * Sends a complete ping message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPing(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(new ByteBuffer[]{data}, WebSocketFrameType.PING, wsChannel, callback);
    }

    /**
     * Sends a complete ping message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPing(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(data, WebSocketFrameType.PING, wsChannel, callback);
    }

    /**
     * Sends a complete ping message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendPingBlocking(final ByteBuffer data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(new ByteBuffer[]{data}, WebSocketFrameType.PING, wsChannel);
    }

    /**
     * Sends a complete ping message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendPingBlocking(final ByteBuffer[] data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(data, WebSocketFrameType.PING, wsChannel);
    }

    /**
     * Sends a complete pong message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPong(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(new ByteBuffer[]{data}, WebSocketFrameType.PONG, wsChannel, callback);
    }


    /**
     * Sends a complete pong message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPong(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(data, WebSocketFrameType.PONG, wsChannel, callback);
    }

    /**
     * Sends a complete pong message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendPongBlocking(final ByteBuffer data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(new ByteBuffer[]{data}, WebSocketFrameType.PONG, wsChannel);
    }

    /**
     * Sends a complete pong message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendPongBlocking(final ByteBuffer[] data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(data, WebSocketFrameType.PONG, wsChannel);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendBinary(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(new ByteBuffer[]{data}, WebSocketFrameType.BINARY, wsChannel, callback);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendBinary(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(data, WebSocketFrameType.BINARY, wsChannel, callback);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     */
    public static void sendBinaryBlocking(final ByteBuffer data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(new ByteBuffer[]{data}, WebSocketFrameType.BINARY, wsChannel);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     */
    public static void sendBinaryBlocking(final ByteBuffer[] data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(data, WebSocketFrameType.BINARY, wsChannel);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendClose(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(new ByteBuffer[]{data}, WebSocketFrameType.CLOSE, wsChannel, callback);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendClose(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(data, WebSocketFrameType.CLOSE, wsChannel, callback);
    }


    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param code The close code
     * @param wsChannel
     * @param callback
     */
    public static void sendClose(final int code, String reason, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendClose(new CloseMessage(code, reason).toByteBuffer(), wsChannel, callback);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param code
     * @param wsChannel
     */
    public static void sendCloseBlocking(final int code, String reason, final WebSocketChannel wsChannel) throws IOException {
        sendCloseBlocking(new CloseMessage(code, reason).toByteBuffer(), wsChannel);
    }
    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     */
    public static void sendCloseBlocking(final ByteBuffer data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(new ByteBuffer[]{data}, WebSocketFrameType.CLOSE, wsChannel);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     */
    public static void sendCloseBlocking(final ByteBuffer[] data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(data, WebSocketFrameType.CLOSE, wsChannel);
    }

    private static void sendInternal(final ByteBuffer[] data, WebSocketFrameType type, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        try {
            long totalData = Buffers.remaining(data);
            StreamSinkFrameChannel channel = wsChannel.send(type, totalData);
            sendData(data, wsChannel, callback, channel, null);
        } catch (IOException e) {
            if (callback != null) {
                callback.onError(wsChannel, null, e);
            } else {
                IoUtils.safeClose(wsChannel);
            }
        }
    }

    private static <T> void sendData(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<T> callback, StreamSinkFrameChannel channel, final T context) throws IOException {
        boolean hasRemaining = true;
        while (hasRemaining) {
            long res = channel.write(data);
            hasRemaining = Buffers.hasRemaining(data);
            if (res == 0 && hasRemaining) {
                channel.getWriteSetter().set(new ChannelListener<StreamSinkFrameChannel>() {
                    @Override
                    public void handleEvent(StreamSinkFrameChannel channel) {
                        do {
                            try {
                                long res = channel.write(data);
                                if (res == 0) {
                                    return;
                                }
                            } catch (IOException e) {
                                handleIoException(channel, e, callback, context, wsChannel);
                                return;
                            }
                        } while (Buffers.hasRemaining(data));
                        channel.suspendWrites();
                        try {
                            flushChannelAsync(wsChannel, callback, channel, context);
                        } catch (IOException e) {
                            handleIoException(channel, e, callback, context, wsChannel);
                        }
                    }
                });
                channel.resumeWrites();
                return;
            }
        }
        flushChannelAsync(wsChannel, callback, channel, context);
    }

    private static <T> void handleIoException(StreamSinkFrameChannel channel, IOException e, WebSocketCallback<T> callback, T context, WebSocketChannel wsChannel) {
        if (callback != null) {
            callback.onError(channel.getWebSocketChannel(), context, e);
        }
        IoUtils.safeClose(wsChannel);
        channel.suspendWrites();
    }

    private static <T> void flushChannelAsync(final WebSocketChannel wsChannel, final WebSocketCallback<T> callback, StreamSinkFrameChannel channel, final T context) throws IOException {
        final WebSocketFrameType type = channel.getType();
        channel.shutdownWrites();
        if (!channel.flush()) {
            channel.getWriteSetter().set(flushingChannelListener(
                    new ChannelListener<StreamSinkFrameChannel>() {
                        @Override
                        public void handleEvent(StreamSinkFrameChannel channel) {
                            if (callback != null) {
                                callback.complete(wsChannel, context);
                            }
                            if (type == WebSocketFrameType.CLOSE && wsChannel.isCloseFrameReceived()) {
                                IoUtils.safeClose(wsChannel);
                            }
                        }
                    }, new ChannelExceptionHandler<StreamSinkFrameChannel>() {
                        @Override
                        public void handleException(StreamSinkFrameChannel channel, IOException exception) {
                            if (callback != null) {
                                callback.onError(wsChannel, context, exception);
                            }
                            if (type == WebSocketFrameType.CLOSE && wsChannel.isCloseFrameReceived()) {
                                IoUtils.safeClose(wsChannel);
                            }
                        }
                    }
            ));
            channel.resumeWrites();
            return;
        }
        if (callback != null) {
            callback.complete(wsChannel, context);
        }
        if (type == WebSocketFrameType.CLOSE && wsChannel.isCloseFrameReceived()) {
            IoUtils.safeClose(wsChannel);
        }
    }

    private static void sendBlockingInternal(final ByteBuffer[] data, WebSocketFrameType type, final WebSocketChannel wsChannel) throws IOException {
        long totalData = Buffers.remaining(data);
        StreamSinkFrameChannel channel = wsChannel.send(type, totalData);
        for (ByteBuffer buf : data) {
            while (buf.hasRemaining()) {
                int res = channel.write(buf);
                if (res == 0) {
                    channel.awaitWritable();
                }
            }
        }
        channel.shutdownWrites();
        while (!channel.flush()) {
            channel.awaitWritable();
        }
        if (type == WebSocketFrameType.CLOSE && wsChannel.isCloseFrameReceived()) {
            IoUtils.safeClose(wsChannel);
        }
    }

    private WebSockets() {

    }

    public static ByteBuffer mergeBuffers(ByteBuffer... payload) {
        int size = (int) Buffers.remaining(payload);
        if (size == 0) {
            return Buffers.EMPTY_BYTE_BUFFER;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (ByteBuffer buf : payload) {
            buffer.put(buf);
        }
        buffer.flip();
        return buffer;
    }
}
