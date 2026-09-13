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

import static java.lang.System.getProperty;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PrivilegedAction;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pooled;

import io.undertow.UndertowOptions;

/**
 * A receive listener that performs a callback when it receives a message
 *
 * @author Stuart Douglas
 * @author baranowb
 */
public abstract class AbstractReceiveListener implements ChannelListener<WebSocketChannel> {
    public static final String WEB_SOCKETS_MAX_READ_FRAMES_PROPERTY = "io.undertow.websockets.core.WEB_SOCKETS_MAX_READ_FRAMES";
    /**
     * Default value for max read frames. -1 - unbounded.
     */
    public static final int DEFAULT_WEB_SOCKETS_MAX_READ_FRAMES = -1;

    /**
     * Default size of data frames. -1 - unbounded.
     * https://datatracker.ietf.org/doc/html/rfc6455#section-5.6
     */
    public static final int DEFAULT_WEB_SOCKETS_MESSAGE_SIZE = -1;

    /**
     * Maximum control frame size: https://datatracker.ietf.org/doc/html/rfc6455#section-5.5
     */
    public static final int DEFAULT_WEB_SOCKETS_CONTROL_FRAME_SIZE = 125;
    /**
     * Maximum size of TEXT message.
     */
    public static final String WEB_SOCKETS_SIZE_TEXT_PROPERTY = "io.undertow.websockets.core.WEB_SOCKETS_SIZE_TEXT";


    /**
     * Maximum size of BINARY message.
     */
    public static final String WEB_SOCKETS_SIZE_BINARY_PROPERTY = "io.undertow.websockets.core.WEB_SOCKETS_SIZE_BINARY";
    /**
     * Default numbers of acceptable pings per window.
     */
    public static final int DEFAULT_WEB_SOCKETS_PING_MAX_PER_WINDOW = 300;

    /**
     * Option controlling max pings count during window; Defaults to {@link UndertowOptions#DEFAULT_WEB_SOCKETS_PING_MAX_PER_WINDOW}
     */
    public static final String WEB_SOCKETS_PING_MAX_PER_WINDOW_PROPERTY = "io.undertow.websockets.core.WEB_SOCKETS_PING_MAX_PER_WINDOW";

    /**
     * Default length of websocket ping window in milliseconds.
     */
    public static final int DEFAULT_WEB_SOCKETS_PING_WINDOW = 60 * 1000;

    /**
     * Option controlling ping window duration. Defaults to {@link UndertowOptions#DEFAULT_WEB_SOCKETS_PING_WINDOW}
    */
    public static final String WEB_SOCKETS_PING_WINDOW_PROPERTY = "io.undertow.websockets.core.WEB_SOCKETS_PING_WINDOW";

    static String getSystemProperty(final String key, final String def) {
        return System.getSecurityManager() == null ? getProperty(key,def) : java.security.AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> getProperty(key,def));
    }

    protected long windowLength;
    protected int countInWindow;
    protected int maxCountInWindow = -1;
    protected long windowTStampThreshold = -1;

    protected void countPing(final WebSocketChannel channel) throws IOException {
        if (this.maxCountInWindow == -1) {
            this.maxCountInWindow = getMaxPingsPerWindow();
            this.windowLength = getPingWindowLength();
        }

        final long now = System.currentTimeMillis();
        if (now > this.windowTStampThreshold) {
            this.windowTStampThreshold = now + this.windowLength;
            this.countInWindow = 1;
            return;
        } else if (this.countInWindow++ > this.maxCountInWindow) {
            WebSockets.sendClose(
                    new CloseMessage(CloseMessage.MSG_VIOLATES_POLICY, WebSocketMessages.MESSAGES.tooManyPings(this.countInWindow)),
                    channel, null);
            final IOException throwThis = new IOException(WebSocketMessages.MESSAGES.tooManyPings(this.countInWindow));
            channel.markReadsBroken(throwThis);
            AbstractReceiveListener.this.onError(channel, throwThis); //to trigger onError in favor of below throw.
        }
    }

    protected long getMaxBinaryBufferSize( ) {
        try {
            return Long.parseLong(getSystemProperty(WEB_SOCKETS_SIZE_BINARY_PROPERTY, Long.toString(DEFAULT_WEB_SOCKETS_MESSAGE_SIZE)));
        } catch(NumberFormatException nfe) {
            return DEFAULT_WEB_SOCKETS_MESSAGE_SIZE;
        }
    }

    protected final long getMaxPongBufferSize() {
        return DEFAULT_WEB_SOCKETS_CONTROL_FRAME_SIZE;
    }

    protected final long getMaxCloseBufferSize() {
        return DEFAULT_WEB_SOCKETS_CONTROL_FRAME_SIZE;
    }

    protected final long getMaxPingBufferSize() {
        return DEFAULT_WEB_SOCKETS_CONTROL_FRAME_SIZE;
    }

    protected long getMaxTextBufferSize() {
        try {
            return Long.parseLong(getSystemProperty(WEB_SOCKETS_SIZE_TEXT_PROPERTY, Long.toString(DEFAULT_WEB_SOCKETS_MESSAGE_SIZE)));
        } catch(NumberFormatException nfe) {
            return DEFAULT_WEB_SOCKETS_MESSAGE_SIZE;
        }
    }

    protected int getMaxPingsPerWindow() {
        try {
            return Integer.parseInt(getSystemProperty(WEB_SOCKETS_PING_MAX_PER_WINDOW_PROPERTY, Integer.toString(DEFAULT_WEB_SOCKETS_PING_MAX_PER_WINDOW)));
        } catch(NumberFormatException nfe) {
            return DEFAULT_WEB_SOCKETS_PING_MAX_PER_WINDOW;
        }
    }

    /**
     * REturn sliding window length in milliseconds.
     * @return
     */
    protected long getPingWindowLength() {
        try {
            return Integer.parseInt(getSystemProperty(WEB_SOCKETS_PING_WINDOW_PROPERTY, Integer.toString(DEFAULT_WEB_SOCKETS_PING_WINDOW)));
        } catch(NumberFormatException nfe) {
            return DEFAULT_WEB_SOCKETS_PING_WINDOW;
        }

    }

    static int getMaxReadFrames() {
        try {
            return Integer.parseInt(getSystemProperty(WEB_SOCKETS_MAX_READ_FRAMES_PROPERTY, Integer.toString(DEFAULT_WEB_SOCKETS_MAX_READ_FRAMES)));
        } catch(NumberFormatException nfe) {
            return DEFAULT_WEB_SOCKETS_MAX_READ_FRAMES;
        }

    }

    @Override
    public void handleEvent(final WebSocketChannel channel) {
        try {
            final StreamSourceFrameChannel result = channel.receive();
            if (result == null) {
                return;
            } else if (result.getType() == WebSocketFrameType.BINARY) {
                onBinary(channel, result);
            } else if (result.getType() == WebSocketFrameType.TEXT) {
                onText(channel, result);
            } else if (result.getType() == WebSocketFrameType.PONG) {
                onPong(channel, result);
            } else if (result.getType() == WebSocketFrameType.PING) {
                countPing(channel);
                onPing(channel, result);
            } else if (result.getType() == WebSocketFrameType.CLOSE) {
                onClose(channel, result);
            }
        } catch (IOException e) {
            onError(channel, e);
        }
    }

    protected void onPing(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
        bufferFullMessage(channel);
    }

    protected void onClose(WebSocketChannel webSocketChannel, StreamSourceFrameChannel channel) throws IOException {
        bufferFullMessage(channel);
    }

    protected void onPong(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        bufferFullMessage(messageChannel);
    }

    protected void onText(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        bufferFullMessage(messageChannel);
    }

    protected void onBinary(WebSocketChannel webSocketChannel, StreamSourceFrameChannel messageChannel) throws IOException {
        bufferFullMessage(messageChannel);
    }

    protected void onError(WebSocketChannel channel, Throwable error) {
        IoUtils.safeClose(channel);
    }

    /**
     * Utility method that reads a full text or binary message, including all fragmented parts. Once the full message is
     * read then the {@link #onFullTextMessage(WebSocketChannel, BufferedTextMessage)} or
     * {@link #onFullBinaryMessage(WebSocketChannel, BufferedBinaryMessage)} method will be invoked.
     *
     * @param messageChannel The message channel
     */
    protected final void bufferFullMessage(StreamSourceFrameChannel messageChannel) {
        if (messageChannel.getType() == WebSocketFrameType.TEXT) {
            readBufferedText(messageChannel, new BufferedTextMessage(getMaxTextBufferSize(), true));
        } else if (messageChannel.getType() == WebSocketFrameType.BINARY) {
            readBufferedBinary(messageChannel, false, new BufferedBinaryMessage(getMaxBinaryBufferSize(), true));
        } else if (messageChannel.getType() == WebSocketFrameType.PONG) {
            readBufferedBinary(messageChannel, true, new BufferedBinaryMessage(getMaxPongBufferSize(), true));
        } else if (messageChannel.getType() == WebSocketFrameType.PING) {
            readBufferedBinary(messageChannel, true, new BufferedBinaryMessage(getMaxPingBufferSize(), true));
        } else if (messageChannel.getType() == WebSocketFrameType.CLOSE) {
            readBufferedBinary(messageChannel, true, new BufferedBinaryMessage(getMaxCloseBufferSize(), true));
        }
    }

    private void readBufferedBinary(final StreamSourceFrameChannel messageChannel, final boolean controlFrame, final BufferedBinaryMessage buffer) {

        buffer.read(messageChannel, new WebSocketCallback<BufferedBinaryMessage>() {
            @Override
            public void complete(WebSocketChannel channel, BufferedBinaryMessage context) {
                try {
                    WebSocketFrameType type = messageChannel.getType();
                    if (!controlFrame) {
                        onFullBinaryMessage(channel, buffer);
                    } else if (type == WebSocketFrameType.PONG) {
                        onFullPongMessage(channel, buffer);
                    } else if (type == WebSocketFrameType.PING) {
                        onFullPingMessage(channel, buffer);
                    } else if (type == WebSocketFrameType.CLOSE) {
                        onFullCloseMessage(channel, buffer);
                    }
                } catch (IOException e) {
                    AbstractReceiveListener.this.onError(channel, e);
                }
            }

            @Override
            public void onError(WebSocketChannel channel, BufferedBinaryMessage context, Throwable throwable) {
                context.getData().close();
                AbstractReceiveListener.this.onError(channel, throwable);
            }
        });
    }

    private void readBufferedText(StreamSourceFrameChannel messageChannel, final BufferedTextMessage textMessage) {
        textMessage.read(messageChannel, new WebSocketCallback<BufferedTextMessage>() {
            @Override
            public void complete(WebSocketChannel channel, BufferedTextMessage context) {
                try {
                    onFullTextMessage(channel, textMessage);
                } catch (IOException e) {
                    AbstractReceiveListener.this.onError(channel, e);
                }
            }

            @Override
            public void onError(WebSocketChannel channel, BufferedTextMessage context, Throwable throwable) {
                AbstractReceiveListener.this.onError(channel, throwable);
            }
        });
    }

    protected void onFullTextMessage(final WebSocketChannel channel, BufferedTextMessage message) throws IOException {
    }

    protected void onFullBinaryMessage(final WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        message.getData().free();
    }

    protected void onFullPingMessage(final WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        final Pooled<ByteBuffer[]> data = message.getData();
        WebSockets.sendPong(data.getResource(), channel, new FreeDataCallback(data));
    }

    protected void onFullPongMessage(final WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        message.getData().free();
    }

    protected void onFullCloseMessage(final WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
        Pooled<ByteBuffer[]> data = message.getData();
        try {
            CloseMessage cm = new CloseMessage(data.getResource());
            onCloseMessage(cm, channel);
            if (!channel.isCloseFrameSent()) {
                WebSockets.sendClose(cm, channel, null);
            }
        } finally {
            data.close();
        }
    }

    protected void onCloseMessage(CloseMessage cm, WebSocketChannel channel) {
    }

    private static class FreeDataCallback implements WebSocketCallback<Void> {
        private final Pooled<ByteBuffer[]> data;

        FreeDataCallback(Pooled<ByteBuffer[]> data) {
            this.data = data;
        }

        @Override
        public void complete(WebSocketChannel channel, Void context) {
            data.close();
        }

        @Override
        public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
            data.close();
        }
    }
}
