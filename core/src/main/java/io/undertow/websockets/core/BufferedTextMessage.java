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
import io.undertow.connector.PooledByteBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A buffered text message.
 *
 * @author Stuart Douglas
 */
public class BufferedTextMessage {

    private final UTF8Output data = new UTF8Output();

    private final boolean bufferFullMessage;
    private final long maxMessageSize;
    private boolean complete;
    long currentSize;

    /**
     * @param maxMessageSize    The maximum message size
     * @param bufferFullMessage If the complete message should be buffered
     */
    public BufferedTextMessage(long maxMessageSize, boolean bufferFullMessage) {
        this.maxMessageSize = maxMessageSize;
        this.bufferFullMessage = bufferFullMessage;
    }

    public BufferedTextMessage(boolean bufferFullMessage) {
        this(-1, bufferFullMessage);
    }

    private void checkMaxSize(StreamSourceFrameChannel channel, int res) throws IOException {
        if(res > 0) {
            currentSize += res;
        }
        if (maxMessageSize > 0 && currentSize > maxMessageSize) {
            WebSockets.sendClose(new CloseMessage(CloseMessage.MSG_TOO_BIG, WebSocketMessages.MESSAGES.messageToBig(maxMessageSize)), channel.getWebSocketChannel(), null);
            throw new IOException(WebSocketMessages.MESSAGES.messageToBig(maxMessageSize));
        }
    }

    public void readBlocking(StreamSourceFrameChannel channel) throws IOException {
        PooledByteBuffer pooled = channel.getWebSocketChannel().getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getBuffer();
        try {
            for (; ; ) {
                int res = channel.read(buffer);
                if (res == -1) {
                    buffer.flip();
                    data.write(buffer);
                    this.complete = true;
                    return;
                } else if (res == 0) {
                    channel.awaitReadable();
                }
                checkMaxSize(channel, res);
                if (!buffer.hasRemaining()) {
                    buffer.flip();
                    data.write(buffer);
                    buffer.compact();
                    if (!bufferFullMessage) {
                        //if we are not reading the full message we return
                        return;
                    }
                }
            }
        } finally {
            pooled.close();
        }
    }

    public void read(final StreamSourceFrameChannel channel, final WebSocketCallback<BufferedTextMessage> callback) {
        PooledByteBuffer pooled = channel.getWebSocketChannel().getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getBuffer();
        try {
            try {
                for (; ; ) {
                    int res = channel.read(buffer);
                    if (res == -1) {
                        this.complete = true;
                        buffer.flip();
                        data.write(buffer);
                        callback.complete(channel.getWebSocketChannel(), this);
                        return;
                    } else if (res == 0) {
                        buffer.flip();
                        if (buffer.hasRemaining()) {
                            data.write(buffer);
                            if (!bufferFullMessage) {
                                callback.complete(channel.getWebSocketChannel(), this);
                            }
                        }
                        channel.getReadSetter().set(new ChannelListener<StreamSourceFrameChannel>() {
                            @Override
                            public void handleEvent(StreamSourceFrameChannel channel) {
                                if(complete ) {
                                    return;
                                }
                                PooledByteBuffer pooled = channel.getWebSocketChannel().getBufferPool().allocate();
                                final ByteBuffer buffer = pooled.getBuffer();
                                try {
                                    try {
                                        for (; ; ) {
                                            int res = channel.read(buffer);
                                            if (res == -1) {
                                                checkMaxSize(channel, res);
                                                buffer.flip();
                                                data.write(buffer);
                                                complete = true;
                                                callback.complete(channel.getWebSocketChannel(), BufferedTextMessage.this);
                                                return;
                                            } else if (res == 0) {
                                                buffer.flip();
                                                if (buffer.hasRemaining()) {
                                                    data.write(buffer);
                                                    if (!bufferFullMessage) {
                                                        callback.complete(channel.getWebSocketChannel(), BufferedTextMessage.this);
                                                    }
                                                }
                                                return;
                                            }
                                            if (!buffer.hasRemaining()) {
                                                buffer.flip();
                                                data.write(buffer);
                                                buffer.clear();
                                                if (!bufferFullMessage) {
                                                    callback.complete(channel.getWebSocketChannel(), BufferedTextMessage.this);
                                                }
                                            }
                                        }
                                    } catch (IOException e) {
                                        callback.onError(channel.getWebSocketChannel(), BufferedTextMessage.this, e);
                                    }
                                } finally {
                                    pooled.close();
                                }
                            }
                        });
                        channel.resumeReads();
                        return;
                    }
                    checkMaxSize(channel, res);
                    if (!buffer.hasRemaining()) {
                        buffer.flip();
                        data.write(buffer);
                        buffer.clear();
                        if (!bufferFullMessage) {
                            callback.complete(channel.getWebSocketChannel(), this);
                        }
                    }
                }
            } catch (IOException e) {
                callback.onError(channel.getWebSocketChannel(), this, e);
            }
        } finally {
            pooled.close();
        }
    }

    /**
     * Gets the buffered data and clears the buffered text message. If this is not called on a UTF8
     * character boundary there may be partial code point data that is still buffered.
     *
     * @return The data
     */
    public String getData() {
        return data.extract();
    }

    public boolean isComplete() {
        return complete;
    }
}
