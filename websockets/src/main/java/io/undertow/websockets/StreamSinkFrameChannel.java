package io.undertow.websockets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.undertow.websockets.frame.WebSocketFrameType;

import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

public abstract class StreamSinkFrameChannel implements StreamSinkChannel {

    private final WebSocketFrameType type;
    private final StreamSinkChannel channel;
    private final WebSocketChannel wsChannel;
    SimpleSetter<StreamSinkFrameChannel> closeSetter = new SimpleSetter<StreamSinkFrameChannel>();
    SimpleSetter<StreamSinkFrameChannel> writeSetter = new SimpleSetter<StreamSinkFrameChannel>();
    
    private volatile boolean closed;
    private AtomicBoolean writesDone = new AtomicBoolean();

    public StreamSinkFrameChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type) {
        this.channel = channel;
        this.wsChannel = wsChannel;
        this.type = type;
    }

    
    /**
     * Return the {@link WebSocketFrameType} or <code>null</code> if its not known at the calling time.
     * 
     * 
     */
    public WebSocketFrameType getType() {
        return type;
    }


    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public void close() throws IOException {
        if (wsChannel.currentSender.remove(this)) {
            flush();
            closed = true;
        }
        // TODO: Implement me
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        checkClosed();
        if (!isWriteResumed()) {
            return 0;
        }
        return 0;
    }


    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        checkClosed();
        if (!isWriteResumed()) {
            return 0;
        }
        return 0;
    }


    @Override
    public int write(ByteBuffer src) throws IOException {
        checkClosed();
        if (!isWriteResumed()) {
            return 0;
        }
        return 0;
    }


    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        checkClosed();
        if (!isWriteResumed()) {
            return 0;
        }
        return 0;
    }


    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        checkClosed();
        if (!isWriteResumed()) {
            return 0;
        }
        return 0;
    }


    @Override
    public boolean isOpen() {
        return !closed && channel.isOpen();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return channel.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return channel.setOption(option, value);
    }

    @Override
    public Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public void suspendWrites() {
        if (wsChannel.currentSender.peek() == this) {
            channel.suspendWrites();
        }
    }


    @Override
    public void resumeWrites() {
        if (wsChannel.currentSender.peek() == this) {
            channel.suspendWrites();
        }        
    }


    @Override
    public boolean isWriteResumed() {
        if (wsChannel.currentSender.peek() == this) {
            return channel.isWriteResumed();
        } else {
            return false;
        }
    }


    @Override
    public void wakeupWrites() {
        if (wsChannel.currentSender.peek() == this) {
            channel.wakeupWrites();
        }
        ChannelListeners.invokeChannelListener(this, writeSetter.get());

    }


    @Override
    public void shutdownWrites() throws IOException {
        if (writesDone.compareAndSet(false, true)) {
            flush();
        }
    }


    @Override
    public void awaitWritable() throws IOException {
        if (wsChannel.currentSender.peek() == this) {
            channel.awaitWritable();
        }
        // TODO: handle this
    }


    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (wsChannel.currentSender.peek() == this) {
            channel.awaitWritable(time, timeUnit);
        }
        // TODO: handle this        
    }


    @Override
    public XnioExecutor getWriteThread() {
        return channel.getWriteThread();
    }


    @Override
    public boolean flush() throws IOException {
        if (wsChannel.currentSender.peek() == this) {
            return channel.flush();
        }
        return false;
    }

    private void checkClosed() throws IOException {
        if (!isOpen()) {
            throw new IOException("Channel already closed");
        }
    }
}
