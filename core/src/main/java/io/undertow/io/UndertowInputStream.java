package io.undertow.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import org.xnio.Buffers;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.EmptyStreamSourceChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Input stream that reads from the underlying channel. This stream delays creation
 * of the channel till it is actually used.
 *
 * @author Stuart Douglas
 */
public class UndertowInputStream extends InputStream {

    private final Pool<ByteBuffer> bufferPool;
    private final StreamSourceChannel channel;
    private boolean closed;
    private Pooled<ByteBuffer> pooled;

    public UndertowInputStream(final HttpServerExchange exchange) {
        this.bufferPool = exchange.getConnection().getBufferPool();
        if (exchange.isRequestChannelAvailable()) {
            this.channel = exchange.getRequestChannel();
        } else {
            this.channel = new EmptyStreamSourceChannel(exchange.getIoThread());
        }
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
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        readIntoBuffer();
        if (closed) {
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
        if (pooled == null && !closed) {
            pooled = bufferPool.allocate();
            int res = Channels.readBlocking(channel, pooled.getResource());
            pooled.getResource().flip();
            if (res == -1) {
                closed = true;
                pooled.free();
                pooled = null;
            }

        }
    }

    @Override
    public int available() throws IOException {
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        readIntoBuffer();
        if (closed) {
            return -1;
        }
        return pooled.getResource().remaining();
    }

    @Override
    public void close() throws IOException {
        if(closed) {
            return;
        }
        while (!closed) {
            readIntoBuffer();
            if(pooled != null) {
                pooled.free();
                pooled = null;
            }
        }
    }
}
