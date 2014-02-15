package io.undertow.websockets.core;

import org.xnio.ChannelListener;
import org.xnio.Pooled;

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
            WebSockets.sendClose(new CloseMessage(CloseMessage.MSG_TOO_BIG, WebSocketMessages.MESSAGES.messageToBig(maxMessageSize)).toByteBuffer(), channel.getWebSocketChannel(), null);
            throw new IOException(WebSocketMessages.MESSAGES.messageToBig(maxMessageSize));
        }
    }

    public void readBlocking(StreamSourceFrameChannel channel) throws IOException {
        Pooled<ByteBuffer> pooled = channel.getWebSocketChannel().getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getResource();
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
            pooled.free();
        }
    }

    public void read(final StreamSourceFrameChannel channel, final WebSocketCallback<BufferedTextMessage> callback) {
        Pooled<ByteBuffer> pooled = channel.getWebSocketChannel().getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getResource();
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
                                Pooled<ByteBuffer> pooled = channel.getWebSocketChannel().getBufferPool().allocate();
                                final ByteBuffer buffer = pooled.getResource();
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
                                    pooled.free();
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
            pooled.free();
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
