package io.undertow.websockets.core;

import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A buffered binary message.
 *
 * @author Stuart Douglas
 */
public class BufferedBinaryMessage {

    private final List<Pooled<ByteBuffer>> data = new ArrayList<Pooled<ByteBuffer>>(1);
    private Pooled<ByteBuffer> current;
    private boolean closed = false;
    private final long maxMessageSize;
    long currentSize;

    public BufferedBinaryMessage(long maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public BufferedBinaryMessage() {
        this(-1);
    }

    public void readBlocking(StreamSourceFrameChannel channel) throws IOException {
        if (closed) {
            throw WebSocketMessages.MESSAGES.dataHasBeenReleased();
        }
        if (current == null) {
            current = channel.getWebSocketChannel().getBufferPool().allocate();
        }
        for (; ; ) {
            int res = channel.read(current.getResource());
            if (res == -1) {
                return;
            } else if (res == 0) {
                channel.awaitReadable();
            }
            checkMaxSize(channel, res);
            dealWithFullBuffer(channel);
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
        if (closed) {
            throw WebSocketMessages.MESSAGES.dataHasBeenReleased();
        }

        if (current == null) {
            current = channel.getWebSocketChannel().getBufferPool().allocate();
        }
        try {
            for (; ; ) {
                int res = channel.read(current.getResource());
                if (res == -1) {
                    callback.complete(channel.getWebSocketChannel(), this);
                    return;
                } else if (res == 0) {
                    channel.getReadSetter().set(new ChannelListener<StreamSourceFrameChannel>() {
                        @Override
                        public void handleEvent(StreamSourceFrameChannel channel) {
                            try {
                                for (; ; ) {
                                    int res = channel.read(current.getResource());
                                    if (res == -1) {
                                        channel.suspendReads();
                                        callback.complete(channel.getWebSocketChannel(), BufferedBinaryMessage.this);
                                        return;
                                    } else if (res == 0) {
                                        return;
                                    }

                                    checkMaxSize(channel, res);
                                    dealWithFullBuffer(channel);
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
                dealWithFullBuffer(channel);
            }
        } catch (IOException e) {
            callback.onError(channel.getWebSocketChannel(), this, e);
        }
    }

    private void checkMaxSize(StreamSourceFrameChannel channel, int res) throws IOException {
        currentSize += res;
        if (maxMessageSize > 0 && currentSize > maxMessageSize) {
            WebSockets.sendClose(new CloseMessage(CloseMessage.MSG_TOO_BIG, WebSocketMessages.MESSAGES.messageToBig(maxMessageSize)).toByteBuffer(), channel.getWebSocketChannel(), null);
            throw new IOException(WebSocketMessages.MESSAGES.messageToBig(maxMessageSize));
        }
    }

    public ByteBuffer[] getData() {
        if (closed) {
            throw WebSocketMessages.MESSAGES.dataHasBeenReleased();
        }
        if (current == null) {
            return new ByteBuffer[0];
        }
        if (data.isEmpty()) {
            return new ByteBuffer[]{getCurrentFlipped()};
        }
        ByteBuffer[] ret = new ByteBuffer[data.size() + 1];
        for (int i = 0; i < data.size(); ++i) {
            ret[i] = data.get(i).getResource().duplicate();
        }
        ret[data.size()] = getCurrentFlipped();
        return ret;
    }

    public byte[] toByteArray() {
        ByteBuffer[] payload = getData();
        int size = (int) Buffers.remaining(payload);
        byte[] buffer = new byte[size];
        int i = 0;
        for (ByteBuffer buf : payload) {
            while (buf.hasRemaining()) {
                buffer[i++] = buf.get();
            }
        }
        return buffer;
    }

    private ByteBuffer getCurrentFlipped() {
        ByteBuffer copy = current.getResource().duplicate();
        copy.flip();
        return copy;

    }

    public void release() {
        if (closed) {
            return;
        }
        if (current != null) {
            current.free();
        }
        for (Pooled<ByteBuffer> pooled : data) {
            pooled.free();
        }
    }
}
