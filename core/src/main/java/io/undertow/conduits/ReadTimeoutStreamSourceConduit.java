package io.undertow.conduits;

import io.undertow.UndertowLogger;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper for read timeout. This should always be the first wrapper applied to the underlying channel.
 *
 * @author Stuart Douglas
 * @see org.xnio.Options#READ_TIMEOUT
 */
public final class ReadTimeoutStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private XnioExecutor.Key handle;
    private final StreamConnection connection;

    private static final int FUZZ_FACTOR = 50; //we add 50ms to the timeout to make sure the underlying channel has actually timed out

    private final Runnable timeoutCommand = new Runnable() {
        @Override
        public void run() {
            UndertowLogger.REQUEST_LOGGER.tracef("Timing out channel %s due to inactivity");
            IoUtils.safeClose(connection);
            if (connection.getSourceChannel().isReadResumed()) {
                ChannelListeners.invokeChannelListener(connection.getSourceChannel(), connection.getSourceChannel().getReadListener());
            }
            if(connection.getSinkChannel().isWriteResumed()) {
                ChannelListeners.invokeChannelListener(connection.getSinkChannel(), connection.getSinkChannel().getWriteListener());
            }
        }
    };

    public ReadTimeoutStreamSourceConduit(final StreamSourceConduit delegate, StreamConnection connection) {
        super(delegate);
        this.connection = connection;
    }

    private void handleReadTimeout(final long ret) throws IOException {
        Integer readTimeout = connection.getOption(Options.READ_TIMEOUT);
        if (readTimeout != null && readTimeout > 0) {
            if (ret == 0 && handle == null) {
                handle = super.getReadThread().executeAfter(timeoutCommand, readTimeout + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
            } else if (ret > 0 && handle != null) {
                handle.remove();
            }
        }
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long ret = super.transferTo(position, count, target);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        long ret = super.transferTo(count, throughBuffer, target);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long ret = super.read(dsts, offset, length);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        int ret = super.read(dst);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public void awaitReadable() throws IOException {
        Integer timeout = connection.getOption(Options.READ_TIMEOUT);
        if (timeout != null && timeout > 0) {
            super.awaitReadable(timeout + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
        } else {
            super.awaitReadable();
        }
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        Integer timeout = connection.getOption(Options.READ_TIMEOUT);
        if (timeout != null && timeout > 0) {
            long millis = timeUnit.toMillis(time);
            super.awaitReadable(Math.min(millis, timeout + FUZZ_FACTOR), TimeUnit.MILLISECONDS);
        } else {
            super.awaitReadable(time, timeUnit);
        }
    }
}
