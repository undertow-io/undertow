package io.undertow.channels;

import io.undertow.UndertowMessages;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * Stream sink channel. When this channel is considered detached it will no longer forward
 * calls to the delegate
 *
 * @author Stuart Douglas
 */
public abstract class DetachableStreamSinkChannel implements StreamSinkChannel {


    protected final ConduitStreamSinkChannel delegate;
    protected ChannelListener.SimpleSetter<DetachableStreamSinkChannel> writeSetter;
    protected ChannelListener.SimpleSetter<DetachableStreamSinkChannel> closeSetter;

    public DetachableStreamSinkChannel(final ConduitStreamSinkChannel delegate) {
        this.delegate = delegate;
    }

    protected abstract boolean isFinished();

    @Override
    public void suspendWrites() {
        if (isFinished()) {
            return;
        }
        delegate.suspendWrites();
    }


    @Override
    public boolean isWriteResumed() {
        if (isFinished()) {
            return false;
        }
        return delegate.isWriteResumed();
    }

    @Override
    public void shutdownWrites() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.shutdownWrites();
    }

    @Override
    public void awaitWritable() throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        delegate.awaitWritable();
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        delegate.awaitWritable(time, timeUnit);
    }

    @Override
    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    @Override
    public boolean isOpen() {
        return !isFinished() && delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (isFinished()) return;
        delegate.close();
    }

    @Override
    public boolean flush() throws IOException {
        if (isFinished()) {
            return true;
        }
        return delegate.flush();
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.transferFrom(src, position, count);
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.transferFrom(source, count, throughBuffer);
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        if (writeSetter == null) {
            writeSetter = new ChannelListener.SimpleSetter<DetachableStreamSinkChannel>();
            if (!isFinished()) {
                delegate.setWriteListener(ChannelListeners.delegatingChannelListener(this, writeSetter));
            }
        }
        return writeSetter;
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        if (closeSetter == null) {
            closeSetter = new ChannelListener.SimpleSetter<DetachableStreamSinkChannel>();
            if (!isFinished()) {
                delegate.setCloseListener(ChannelListeners.delegatingChannelListener(this, closeSetter));
            }
        }
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.write(srcs, offset, length);
    }

    @Override
    public long write(final ByteBuffer[] srcs) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.write(srcs);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.writeFinal(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.writeFinal(srcs, offset, length);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.writeFinal(srcs);
    }

    @Override
    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.getOption(option);
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.setOption(option, value);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.write(src);
    }

    @Override
    public void resumeWrites() {
        if (isFinished()) {
            return;
        }
        delegate.resumeWrites();
    }

    @Override
    public void wakeupWrites() {
        if (isFinished()) {
            return;
        }
        delegate.wakeupWrites();
    }

    public void responseDone() {
        delegate.setCloseListener(null);
        delegate.setWriteListener(null);
        if (delegate.isWriteResumed()) {
            delegate.suspendWrites();
        }
    }
}
