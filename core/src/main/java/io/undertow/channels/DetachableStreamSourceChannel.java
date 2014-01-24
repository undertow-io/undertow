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
import org.xnio.conduits.ConduitStreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * A stream source channel that can be marked as detached. Once this is marked as detached then
 * calls will no longer be forwarded to the delegate.
 *
 * @author Stuart Douglas
 */
public abstract class DetachableStreamSourceChannel implements StreamSourceChannel{

    protected final ConduitStreamSourceChannel delegate;

    protected ChannelListener.SimpleSetter<DetachableStreamSourceChannel> readSetter;
    protected ChannelListener.SimpleSetter<DetachableStreamSourceChannel> closeSetter;

    public DetachableStreamSourceChannel(final ConduitStreamSourceChannel delegate) {
        this.delegate = delegate;
    }

    protected abstract boolean isFinished();

    @Override
    public void resumeReads() {
        if (isFinished()) {
            return;
        }
        delegate.resumeReads();
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.transferTo(position, count, target);
    }

    public void awaitReadable() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.awaitReadable();
    }

    public void suspendReads() {
        if (isFinished()) {
            return;
        }
        delegate.suspendReads();
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.transferTo(count, throughBuffer, target);
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean isReadResumed() {
        if (isFinished()) {
            return false;
        }
        return delegate.isReadResumed();
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {

        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.setOption(option, value);
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public void shutdownReads() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.shutdownReads();
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        if (readSetter == null) {
            readSetter = new ChannelListener.SimpleSetter<DetachableStreamSourceChannel>();
            if (!isFinished()) {
                delegate.setReadListener(ChannelListeners.delegatingChannelListener(this, readSetter));
            }
        }
        return readSetter;
    }

    public boolean isOpen() {
        if (isFinished()) {
            return false;
        }
        return delegate.isOpen();
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.read(dsts);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.read(dsts, offset, length);
    }

    public void wakeupReads() {
        if (isFinished()) {
            return;
        }
        delegate.wakeupReads();
    }

    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        delegate.awaitReadable(time, timeUnit);
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        if (closeSetter == null) {
            closeSetter = new ChannelListener.SimpleSetter<DetachableStreamSourceChannel>();
            if (!isFinished()) {
                delegate.setCloseListener(ChannelListeners.delegatingChannelListener(this, closeSetter));
            }
        }
        return closeSetter;
    }

    public void close() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.close();
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.getOption(option);
    }

    public int read(final ByteBuffer dst) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.read(dst);
    }

    @Override
    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }
}
