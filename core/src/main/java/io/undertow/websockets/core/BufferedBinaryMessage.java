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
import org.xnio.ChannelListener;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A buffered binary message.
 *
 * @author Stuart Douglas
 */
public class BufferedBinaryMessage {

    private final boolean bufferFullMessage;
    private List<Pooled<ByteBuffer>> data = new ArrayList<>(1);
    private Pooled<ByteBuffer> current;
    private final long maxMessageSize;
    private long currentSize;
    private boolean complete;
    private int frameCount;


    public BufferedBinaryMessage(long maxMessageSize, boolean bufferFullMessage) {
        this.bufferFullMessage = bufferFullMessage;
        this.maxMessageSize = maxMessageSize;
    }

    public BufferedBinaryMessage(boolean bufferFullMessage) {
        this(-1, bufferFullMessage);
    }

    public void readBlocking(StreamSourceFrameChannel channel) throws IOException {
        if (current == null) {
            current = channel.getWebSocketChannel().getBufferPool().allocate();
        }
        for (; ; ) {
            int res = channel.read(current.getResource());
            if (res == -1) {
                complete = true;
                return;
            } else if (res == 0) {
                channel.awaitReadable();
            }
            checkMaxSize(channel, res);
            if (bufferFullMessage) {
                dealWithFullBuffer(channel);
            } else if (!current.getResource().hasRemaining()) {
                return;
            }
        }
    }

    private void dealWithFullBuffer(StreamSourceFrameChannel channel) {
        if (!current.getResource().hasRemaining()) {
            current.getResource().flip();
            data.add(current);
            current = channel.getWebSocketChannel().getBufferPool().allocate();
        }
    }

    public void read(final StreamSourceFrameChannel channel, final WebSocketCallback<BufferedBinaryMessage> callback) {
        try {
            for (; ; ) {
                if (current == null) {
                    current = channel.getWebSocketChannel().getBufferPool().allocate();
                }
                int res = channel.read(current.getResource());
                if (res == -1) {
                    this.complete = true;
                    callback.complete(channel.getWebSocketChannel(), this);
                    return;
                } else if (res == 0) {
                    channel.getReadSetter().set(new ChannelListener<StreamSourceFrameChannel>() {
                        @Override
                        public void handleEvent(StreamSourceFrameChannel channel) {
                            if(complete ) {
                                return;
                            }
                            try {
                                for (; ; ) {
                                    if (current == null) {
                                        current = channel.getWebSocketChannel().getBufferPool().allocate();
                                    }
                                    int res = channel.read(current.getResource());
                                    if (res == -1) {
                                        complete = true;
                                        channel.suspendReads();
                                        callback.complete(channel.getWebSocketChannel(), BufferedBinaryMessage.this);
                                        return;
                                    } else if (res == 0) {
                                        return;
                                    }

                                    checkMaxSize(channel, res);
                                    if (bufferFullMessage) {
                                        dealWithFullBuffer(channel);
                                    } else if (!current.getResource().hasRemaining()) {
                                        callback.complete(channel.getWebSocketChannel(), BufferedBinaryMessage.this);
                                    } else {
                                        handleNewFrame(channel, callback);
                                    }
                                }
                            } catch (IOException e) {
                                channel.suspendReads();
                                callback.onError(channel.getWebSocketChannel(), BufferedBinaryMessage.this, e);
                            }
                        }
                    });
                    channel.resumeReads();
                    return;
                }

                checkMaxSize(channel, res);
                if (bufferFullMessage) {
                    dealWithFullBuffer(channel);
                } else if (!current.getResource().hasRemaining()) {
                    callback.complete(channel.getWebSocketChannel(), BufferedBinaryMessage.this);
                } else {
                    handleNewFrame(channel, callback);
                }
            }
        } catch (IOException e) {
            callback.onError(channel.getWebSocketChannel(), this, e);
        }
    }

    private void handleNewFrame(StreamSourceFrameChannel channel, final WebSocketCallback<BufferedBinaryMessage> callback) {
        //TODO: remove this crap
        //basically some bogus web sockets TCK tests assume that messages will be broken up into frames
        //even if we have the full message available.
        if(!bufferFullMessage) {
            if(channel.getWebSocketFrameCount() != frameCount && current != null && !channel.isFinalFragment()) {
                frameCount = channel.getWebSocketFrameCount();
                callback.complete(channel.getWebSocketChannel(), this);
            }
        }
    }

    private void checkMaxSize(StreamSourceFrameChannel channel, int res) throws IOException {
        currentSize += res;
        if (maxMessageSize > 0 && currentSize > maxMessageSize) {
            WebSockets.sendClose(new CloseMessage(CloseMessage.MSG_TOO_BIG, WebSocketMessages.MESSAGES.messageToBig(maxMessageSize)), channel.getWebSocketChannel(), null);
            throw new IOException(WebSocketMessages.MESSAGES.messageToBig(maxMessageSize));
        }
    }

    public Pooled<ByteBuffer[]> getData() {
        if (current == null) {
            return new ImmediatePooled<>(new ByteBuffer[0]);
        }
        if (data.isEmpty()) {
            final Pooled<ByteBuffer> current = this.current;
            current.getResource().flip();
            this.current = null;
            final ByteBuffer[] data = new ByteBuffer[]{current.getResource()};
            return new PooledByteBufferArray(Collections.<Pooled<ByteBuffer>>singletonList(current), data);
        }
        current.getResource().flip();
        data.add(current);
        current = null;
        ByteBuffer[] ret = new ByteBuffer[data.size()];
        for (int i = 0; i < data.size(); ++i) {
            ret[i] = data.get(i).getResource();
        }
        List<Pooled<ByteBuffer>> data = this.data;
        this.data = new ArrayList<>();

        return new PooledByteBufferArray(data, ret);
    }

    public boolean isComplete() {
        return complete;
    }

    private static final class PooledByteBufferArray implements Pooled<ByteBuffer[]> {

        private final List<Pooled<ByteBuffer>> pooled;
        private final ByteBuffer[] data;

        private PooledByteBufferArray(List<Pooled<ByteBuffer>> pooled, ByteBuffer[] data) {
            this.pooled = pooled;
            this.data = data;
        }

        @Override
        public void discard() {
            for (Pooled<ByteBuffer> item : pooled) {
                item.discard();
            }
        }

        @Override
        public void free() {
            for (Pooled<ByteBuffer> item : pooled) {
                item.free();
            }
        }

        @Override
        public ByteBuffer[] getResource() throws IllegalStateException {
            return data;
        }

        @Override
        public void close() {
            free();
        }
    }
}
