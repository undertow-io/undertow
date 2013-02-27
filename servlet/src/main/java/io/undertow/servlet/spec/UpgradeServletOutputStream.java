package io.undertow.servlet.spec;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import io.undertow.servlet.UndertowServletMessages;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Output stream used for upgraded requests. This is different to {@link ServletOutputStreamImpl}
 * as it does no buffering, and it not tied to an exchange.
 *
 * @author Stuart Douglas
 */
public class UpgradeServletOutputStream extends ServletOutputStream {

    private final StreamSinkChannel channel;

    private volatile WriteListener listener;

    /**
     * If this stream is ready for a write
     */
    private static final int FLAG_READY = 1;
    private static final int FLAG_CLOSED = 1 << 1;
    private static final int FLAG_DELEGATE_SHUTDOWN = 1 << 2;

    private volatile int state;

    /**
     * The buffer that is in the process of being written out
     */
    private volatile ByteBuffer buffer;

    protected UpgradeServletOutputStream(final StreamSinkChannel channel) {
        this.channel = channel;
    }

    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        if (listener == null) {
            Channels.writeBlocking(channel, ByteBuffer.wrap(b, off, len));
        } else {
            if (anyAreClear(state, FLAG_READY)) {
                throw UndertowServletMessages.MESSAGES.streamNotReady();
            }
            int res;
            ByteBuffer buffer = ByteBuffer.wrap(b);
            do {
                res = channel.write(buffer);
                if (res == 0) {
                    this.buffer = buffer;
                    state = state & ~FLAG_READY;
                    channel.resumeWrites();
                    return;
                }
            } while (buffer.hasRemaining());
        }
    }

    @Override
    public void write(final int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void flush() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        if (listener == null) {
            Channels.flushBlocking(channel);
        }
    }

    @Override
    public void close() throws IOException {
        state |= FLAG_CLOSED;
        state &= ~FLAG_READY;
        if (listener == null) {
            channel.shutdownWrites();
            state |= FLAG_DELEGATE_SHUTDOWN;
            Channels.flushBlocking(channel);
        } else {
            if (buffer == null) {
                channel.shutdownWrites();
                state |= FLAG_DELEGATE_SHUTDOWN;
                if (!channel.flush()) {
                    channel.resumeWrites();
                }
            }
        }
    }

    void closeBlocking() throws IOException {
        state |= FLAG_CLOSED;
        try {
            if (buffer != null) {
                Channels.writeBlocking(channel, buffer);
            }
            channel.shutdownWrites();
            Channels.flushBlocking(channel);
        } finally {
            channel.close();
        }
    }

    @Override
    public boolean isReady() {
        if (listener == null) {
            //TODO: is this the correct behaviour?
            throw UndertowServletMessages.MESSAGES.streamNotInAsyncMode();
        }
        return anyAreSet(state, FLAG_READY);
    }

    @Override
    public void setWriteListener(final WriteListener writeListener) {
        listener = writeListener;
        channel.getWriteSetter().set(new WriteChannelListener());
        channel.resumeWrites();
    }

    private class WriteChannelListener implements ChannelListener<StreamSinkChannel> {

        @Override
        public void handleEvent(final StreamSinkChannel channel) {
            //flush the channel if it is closed
            if (anyAreSet(state, FLAG_DELEGATE_SHUTDOWN)) {
                try {
                    //either it will work, and the channel is closed
                    //or it won't, and we continue with writes resumed
                    channel.flush();
                    return;
                } catch (IOException e) {
                    handleError(channel, e);
                }
            }
            //if there is data still to write
            if (buffer != null) {
                int res;
                do {
                    try {
                        res = channel.write(buffer);
                        if (res == 0) {
                            return;
                        }
                    } catch (IOException e) {
                        handleError(channel, e);
                    }
                } while (buffer.hasRemaining());
                buffer = null;
            }
            if (anyAreSet(state, FLAG_CLOSED)) {
                try {
                    channel.shutdownWrites();
                    state |= FLAG_DELEGATE_SHUTDOWN;
                    channel.flush(); //if this does not succeed we are already resumed anyway
                } catch (IOException e) {
                    handleError(channel, e);
                }

            } else {
                state |= FLAG_READY;
                channel.suspendWrites();
                channel.getWorker().submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listener.onWritePossible();
                        } catch (IOException e) {
                            IoUtils.safeClose(channel);
                        }
                    }
                });
            }
        }

        private void handleError(final StreamSinkChannel channel, final IOException e) {
            try {
                listener.onError(e);
            } finally {
                IoUtils.safeClose(channel);
            }
        }
    }

}
