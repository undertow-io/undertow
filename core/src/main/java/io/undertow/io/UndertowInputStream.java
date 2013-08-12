package io.undertow.io;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import org.xnio.Buffers;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.EmptyStreamSourceChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Input stream that reads from the underlying channel. This stream delays creation
 * of the channel till it is actually used.
 *
 * @author Stuart Douglas
 */
public class UndertowInputStream extends InputStream {

    private final StreamSourceChannel channel;
    private final Pool<ByteBuffer> bufferPool;

    /**
     * If this stream is ready for a read
     */
    private static final int FLAG_CLOSED = 1;
    private static final int FLAG_FINISHED = 1 << 1;

    private int state;
    private Pooled<ByteBuffer> pooled;

    public UndertowInputStream(final HttpServerExchange exchange) {
        if (exchange.isRequestChannelAvailable()) {
            this.channel = exchange.getRequestChannel();
        } else {
            this.channel = new EmptyStreamSourceChannel(exchange.getIoThread());
        }
        this.bufferPool = exchange.getConnection().getBufferPool();
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int read = read(b);
        if (read == -1) {
            return -1;
        }
        return b[0] & 0xff;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        readIntoBuffer();
        if (anyAreSet(state, FLAG_FINISHED)) {
            return -1;
        }
        if (len == 0) {
            return 0;
        }
        ByteBuffer buffer = pooled.getResource();
        int copied = Buffers.copy(ByteBuffer.wrap(b, off, len), buffer);
        if (!buffer.hasRemaining()) {
            pooled.free();
            pooled = null;
        }
        return copied;
    }

    private void readIntoBuffer() throws IOException {
        if (pooled == null && !anyAreSet(state, FLAG_FINISHED)) {
            pooled = bufferPool.allocate();

            int res = Channels.readBlocking(channel, pooled.getResource());
            pooled.getResource().flip();
            if (res == -1) {
                state |= FLAG_FINISHED;
                pooled.free();
                pooled = null;
            }
        }
    }

    private void readIntoBufferNonBlocking() throws IOException {
        if (pooled == null && !anyAreSet(state, FLAG_FINISHED)) {
            pooled = bufferPool.allocate();
            int res = channel.read(pooled.getResource());
            if (res == 0) {
                pooled.free();
                pooled = null;
                return;
            }
            pooled.getResource().flip();
            if (res == -1) {
                state |= FLAG_FINISHED;
                pooled.free();
                pooled = null;
            }
        }
    }

    @Override
    public int available() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        readIntoBufferNonBlocking();
        if (anyAreSet(state, FLAG_FINISHED)) {
            return -1;
        }
        if (pooled == null) {
            return 0;
        }
        return pooled.getResource().remaining();
    }

    @Override
    public void close() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            return;
        }
        while (allAreClear(state, FLAG_FINISHED)) {
            readIntoBuffer();
            if (pooled != null) {
                pooled.free();
                pooled = null;
            }
        }
        if (pooled != null) {
            pooled.free();
            pooled = null;
        }
        channel.shutdownReads();
        state |= FLAG_FINISHED | FLAG_CLOSED;
    }
}
