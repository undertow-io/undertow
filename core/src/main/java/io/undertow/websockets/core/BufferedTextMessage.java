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

    private final long maxMessageSize;
    long currentSize;

    public BufferedTextMessage(long maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public BufferedTextMessage() {
        this(-1);
    }

    private void checkMaxSize(StreamSourceFrameChannel channel, int res) throws IOException {
        currentSize += res;
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
                    return;
                } else if (res == 0) {
                    channel.awaitReadable();
                }
                checkMaxSize(channel, res);
                buffer.flip();
                data.write(buffer);
                buffer.compact();
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
                        callback.complete(channel.getWebSocketChannel(), this);
                        return;
                    } else if (res == 0) {
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
                                                callback.complete(channel.getWebSocketChannel(), BufferedTextMessage.this);
                                                return;
                                            } else if (res == 0) {
                                                return;
                                            }
                                            checkMaxSize(channel, res);
                                            buffer.flip();
                                            data.write(buffer);
                                            buffer.compact();
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
                    buffer.flip();
                    data.write(buffer);
                    buffer.compact();
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
     * @return The data
     */
    public String getData() {
        return data.extract();
    }
}
