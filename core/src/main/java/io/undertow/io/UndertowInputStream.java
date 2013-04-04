package io.undertow.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

/**
 * Input stream that reads from the underlying channel. This stream delays creation
 * of the channel till it is actually used.
 *
 * @author Stuart Douglas
 */
public class UndertowInputStream extends InputStream {

    private final HttpServerExchange exchange;
    private StreamSourceChannel channel;
    private boolean closed;

    public UndertowInputStream(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (channel == null) {
            channel = exchange.getRequestChannel();
        }
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        channel.awaitReadable();
        return channel.read(ByteBuffer.wrap(b, off, len));
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (channel == null) {
            channel = exchange.getRequestChannel();
        }

        final Pooled<ByteBuffer> pooled = exchange.getConnection().getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getResource();
        try {
            //drain the channel
            int res;
            do {
                channel.awaitReadable();
                res = channel.read(buffer);
            } while (res != -1);
            channel.shutdownReads();
        } finally {
            pooled.free();
        }
    }

    @Override
    public long skip(final long n) throws IOException {
        return super.skip(n);
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int read = read(b);
        if(read == -1) {
            return -1;
        }
        return b[0];
    }
}
