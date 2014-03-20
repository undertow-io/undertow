package io.undertow.conduits;

import io.undertow.UndertowLogger;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

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
public final class WriteTimeoutStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

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

    public WriteTimeoutStreamSinkConduit(final StreamSinkConduit delegate, StreamConnection connection) {
        super(delegate);
        this.connection = connection;
    }

    private void handleWriteTimeout(final long ret) throws IOException {
        Integer writeTimout = connection.getOption(Options.WRITE_TIMEOUT);
        if (writeTimout != null && writeTimout > 0) {
            if (ret == 0 && handle == null) {
                handle = super.getWriteThread().executeAfter(timeoutCommand, writeTimout + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
            } else if (ret > 0 && handle != null) {
                handle.remove();
            }
        }
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        int ret = super.write(src);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        long ret = super.write(srcs, offset, length);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        int ret = super.writeFinal(src);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long ret = super.writeFinal(srcs, offset, length);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        long ret = super.transferFrom(src, position, count);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        long ret = super.transferFrom(source, count, throughBuffer);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public void awaitWritable() throws IOException {
        Integer timeout = connection.getOption(Options.WRITE_TIMEOUT);
        if (timeout != null && timeout > 0) {
            super.awaitWritable(timeout + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
        } else {
            super.awaitWritable();
        }
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        Integer timeout = connection.getOption(Options.WRITE_TIMEOUT);
        if (timeout != null && timeout > 0) {
            long millis = timeUnit.toMillis(time);
            super.awaitWritable(Math.min(millis, timeout + FUZZ_FACTOR), TimeUnit.MILLISECONDS);
        } else {
            super.awaitWritable(time, timeUnit);
        }
    }
}
