package io.undertow.servlet.spec;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import io.undertow.servlet.UndertowServletMessages;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 *
 *
 * @author Stuart Douglas
 */
public class UpgradeServletInputStream extends ServletInputStream {

    private final StreamSourceChannel channel;

    private ReadListener listener;

    /**
     * If this stream is ready for a read
     */
    private int FLAG_READY = 1;
    private int FLAG_CLOSED = 1 << 1;
    private int FLAG_FINISHED = 1 << 2;

    private int state;

    protected UpgradeServletInputStream(final StreamSourceChannel channel) {
        super();
        this.channel = channel;
    }

    @Override
    public boolean isFinished() {
        return anyAreSet(state, FLAG_FINISHED);
    }

    @Override
    public boolean isReady() {
        return anyAreSet(state, FLAG_READY);
    }

    @Override
    public void setReadListener(final ReadListener readListener) {
        listener = readListener;
        channel.getReadSetter().set(new UpgradeServletChannelListener());
        channel.resumeReads();
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        read(b);
        return b[0];
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
        if (listener == null) {
            int res = Channels.readBlocking(channel, buffer);
            if (res == -1) {
                state |= FLAG_FINISHED;
            }
            return res;
        } else {
            if (anyAreClear(state, FLAG_READY)) {
                throw UndertowServletMessages.MESSAGES.streamNotReady();
            }
            int res = channel.read(buffer);
            if (res == -1) {
                state |= FLAG_FINISHED;
            } else if (res == 0) {
                state &= ~FLAG_READY;
                channel.resumeReads();
            }
            return res;
        }
    }

    @Override
    public void close() throws IOException {
        channel.shutdownReads();
        state |= FLAG_FINISHED | FLAG_CLOSED;
    }

    private class UpgradeServletChannelListener implements ChannelListener<StreamSourceChannel> {
        @Override
        public void handleEvent(final StreamSourceChannel channel) {
            if (anyAreSet(state, FLAG_FINISHED)) {
                return;
            }
            try {
                listener.onDataAvailable();
            } catch (IOException e) {
                IoUtils.safeClose(channel);
            }
            if (anyAreSet(state, FLAG_FINISHED)) {
                try {
                    listener.onAllDataRead();
                } catch (IOException e) {
                    IoUtils.safeClose(channel);
                }
            }
        }
    }
}
