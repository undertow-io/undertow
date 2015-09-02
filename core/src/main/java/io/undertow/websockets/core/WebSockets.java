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

import io.undertow.util.ImmediatePooled;
import org.xnio.Buffers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.XnioExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.xnio.ChannelListeners.flushingChannelListener;

/**
 * @author Stuart Douglas
 */
public class WebSockets {

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     * @param callback
     */
    public static void sendText(final String message, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        final ByteBuffer data = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
        sendInternal(data, WebSocketFrameType.TEXT, wsChannel, callback, -1);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     * @param callback
     * @param timeoutmillis the timeout in milliseconds
     */
    public static void sendText(final String message, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        final ByteBuffer data = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
        sendInternal(data, WebSocketFrameType.TEXT, wsChannel, callback, timeoutmillis);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     * @param callback
     */
    public static void sendText(final ByteBuffer message, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(message, WebSocketFrameType.TEXT, wsChannel, callback, -1);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     * @param callback
     */
    public static void sendText(final ByteBuffer message, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        sendInternal(message, WebSocketFrameType.TEXT, wsChannel, callback, timeoutmillis);
    }


    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     */
    public static void sendTextBlocking(final String message, final WebSocketChannel wsChannel) throws IOException {
        final ByteBuffer data = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
        sendBlockingInternal(data, WebSocketFrameType.TEXT, wsChannel);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param message
     * @param wsChannel
     */
    public static void sendTextBlocking(final ByteBuffer message, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(message, WebSocketFrameType.TEXT, wsChannel);
    }

    /**
     * Sends a complete ping message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPing(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(data, WebSocketFrameType.PING, wsChannel, callback, -1);
    }

    /**
     * Sends a complete ping message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPing(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        sendInternal(data, WebSocketFrameType.PING, wsChannel, callback, timeoutmillis);
    }

    /**
     * Sends a complete ping message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPing(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(mergeBuffers(data), WebSocketFrameType.PING, wsChannel, callback, -1);
    }

    /**
     * Sends a complete ping message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPing(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        sendInternal(mergeBuffers(data), WebSocketFrameType.PING, wsChannel, callback, timeoutmillis);
    }

    /**
     * Sends a complete ping message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendPingBlocking(final ByteBuffer data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(data, WebSocketFrameType.PING, wsChannel);
    }

    /**
     * Sends a complete ping message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendPingBlocking(final ByteBuffer[] data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(mergeBuffers(data), WebSocketFrameType.PING, wsChannel);
    }

    /**
     * Sends a complete pong message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPong(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(data, WebSocketFrameType.PONG, wsChannel, callback, -1);
    }

    /**
     * Sends a complete pong message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPong(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        sendInternal(data, WebSocketFrameType.PONG, wsChannel, callback, timeoutmillis);
    }


    /**
     * Sends a complete pong message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPong(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(mergeBuffers(data), WebSocketFrameType.PONG, wsChannel, callback, -1);
    }

    /**
     * Sends a complete pong message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendPong(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        sendInternal(mergeBuffers(data), WebSocketFrameType.PONG, wsChannel, callback, timeoutmillis);
    }
    /**
     * Sends a complete pong message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendPongBlocking(final ByteBuffer data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(data, WebSocketFrameType.PONG, wsChannel);
    }

    /**
     * Sends a complete pong message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendPongBlocking(final ByteBuffer[] data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(mergeBuffers(data), WebSocketFrameType.PONG, wsChannel);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendBinary(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(data, WebSocketFrameType.BINARY, wsChannel, callback, -1);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendBinary(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        sendInternal(data, WebSocketFrameType.BINARY, wsChannel, callback, timeoutmillis);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendBinary(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendInternal(mergeBuffers(data), WebSocketFrameType.BINARY, wsChannel, callback, -1);
    }

    /**
     * Sends a complete text message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendBinary(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        sendInternal(mergeBuffers(data), WebSocketFrameType.BINARY, wsChannel, callback, timeoutmillis);
    }

    /**
     * Sends a complete binary message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendBinaryBlocking(final ByteBuffer data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(data, WebSocketFrameType.BINARY, wsChannel);
    }

    /**
     * Sends a complete binary message using blocking IO
     *
     * @param data
     * @param wsChannel
     */
    public static void sendBinaryBlocking(final ByteBuffer[] data, final WebSocketChannel wsChannel) throws IOException {
        sendBlockingInternal(mergeBuffers(data), WebSocketFrameType.BINARY, wsChannel);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendClose(final ByteBuffer data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        CloseMessage sm = new CloseMessage(data);
        sendClose(sm, wsChannel, callback);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     * @param callback
     */
    public static void sendClose(final ByteBuffer[] data, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        CloseMessage sm = new CloseMessage(data);
        sendClose(sm, wsChannel, callback);
    }


    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param code The close code
     * @param wsChannel
     * @param callback
     */
    public static void sendClose(final int code, String reason, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        sendClose(new CloseMessage(code, reason), wsChannel, callback);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param closeMessage The close message
     * @param wsChannel
     * @param callback
     */
    public static void sendClose(final CloseMessage closeMessage, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback) {
        wsChannel.setCloseCode(closeMessage.getCode());
        wsChannel.setCloseReason(closeMessage.getReason());
        sendInternal(closeMessage.toByteBuffer(), WebSocketFrameType.CLOSE, wsChannel, callback, -1);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param closeMessage the close message
     * @param wsChannel
     */
    public static void sendCloseBlocking(final CloseMessage closeMessage, final WebSocketChannel wsChannel) throws IOException {
        wsChannel.setCloseReason(closeMessage.getReason());
        wsChannel.setCloseCode(closeMessage.getCode());
        sendBlockingInternal(closeMessage.toByteBuffer(), WebSocketFrameType.CLOSE, wsChannel);
    }
    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param code
     * @param wsChannel
     */
    public static void sendCloseBlocking(final int code, String reason, final WebSocketChannel wsChannel) throws IOException {
        sendCloseBlocking(new CloseMessage(code, reason), wsChannel);
    }
    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     */
    public static void sendCloseBlocking(final ByteBuffer data, final WebSocketChannel wsChannel) throws IOException {
        sendCloseBlocking(new CloseMessage(data), wsChannel);
    }

    /**
     * Sends a complete close message, invoking the callback when complete
     *
     * @param data
     * @param wsChannel
     */
    public static void sendCloseBlocking(final ByteBuffer[] data, final WebSocketChannel wsChannel) throws IOException {
        sendCloseBlocking(new CloseMessage(data), wsChannel);
    }

    private static void sendInternal(final ByteBuffer data, WebSocketFrameType type, final WebSocketChannel wsChannel, final WebSocketCallback<Void> callback, long timeoutmillis) {
        try {
            StreamSinkFrameChannel channel = wsChannel.send(type);
            // TODO chunk data into some MTU-like thing to control packet size
            if(!channel.send(new ImmediatePooled<>(data))) {
                throw WebSocketMessages.MESSAGES.unableToSendOnNewChannel();
            }
            flushChannelAsync(wsChannel, callback, channel, null, timeoutmillis);
        } catch (IOException e) {
            if (callback != null) {
                callback.onError(wsChannel, null, e);
            } else {
                IoUtils.safeClose(wsChannel);
            }
        }
    }

    private static <T> void flushChannelAsync(final WebSocketChannel wsChannel, final WebSocketCallback<T> callback, StreamSinkFrameChannel channel, final T context, long timeoutmillis) throws IOException {
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
            if(timeoutmillis > 0) {
                setupTimeout(channel, timeoutmillis);
            }
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

    private static void setupTimeout(final StreamSinkFrameChannel channel, long timeoutmillis) {
        final XnioExecutor.Key key = channel.getIoThread().executeAfter(new Runnable() {
            @Override
            public void run() {
                if (channel.isOpen()) {
                    IoUtils.safeClose(channel);
                }
            }
        }, timeoutmillis, TimeUnit.MILLISECONDS);
        channel.getCloseSetter().set(new ChannelListener<StreamSinkFrameChannel>() {
            @Override
            public void handleEvent(StreamSinkFrameChannel channel) {
                key.remove();
            }
        });
    }

    private static void sendBlockingInternal(final ByteBuffer data, WebSocketFrameType type, final WebSocketChannel wsChannel) throws IOException {
        StreamSinkFrameChannel channel = wsChannel.send(type);
        // TODO chunk data into some MTU-like thing to control packet size
        if(!channel.send(new ImmediatePooled<>(data))) {
            throw WebSocketMessages.MESSAGES.unableToSendOnNewChannel();
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
