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

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A receive listener that performs a callback when it receives a message
 *
 * @author Stuart Douglas
 */
public abstract class AbstractReceiveListener implements ChannelListener<WebSocketChannel> {

    @Override
    public void handleEvent(WebSocketChannel channel) {
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

    protected long getMaxBinaryBufferSize() {
        return -1;
    }

    protected long getMaxPongBufferSize() {
        return -1;
    }

    protected long getMaxCloseBufferSize() {
        return -1;
    }

    protected long getMaxPingBufferSize() {
        return -1;
    }

    protected long getMaxTextBufferSize() {
        return -1;
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
