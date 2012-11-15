/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class StreamSinkFrameChannel implements StreamSinkChannel {

    private final WebSocketFrameType type;
    protected final StreamSinkChannel channel;
    protected final WebSocketChannel wsChannel;
    private final SimpleSetter<StreamSinkFrameChannel> closeSetter = new SimpleSetter<StreamSinkFrameChannel>();
    private final SimpleSetter<StreamSinkFrameChannel> writeSetter = new SimpleSetter<StreamSinkFrameChannel>();

    /**
     * The payload size
     */
    protected final long payloadSize;

    /**
     * The number of payload bytes that have been written. Does not include protocol bytes
     */
    private long written;

    private final Object writeWaitLock = new Object();
    private int waiters = 0;

    private boolean writesSuspended;

    //todo: I don't think this belongs here
    private int rsv;
    private boolean finalFragment = true;

    private static final AtomicReferenceFieldUpdater<StreamSinkFrameChannel, ChannelState> stateUpdater = AtomicReferenceFieldUpdater.newUpdater(StreamSinkFrameChannel.class, ChannelState.class, "state");
    private volatile ChannelState state = ChannelState.WAITING;

    public StreamSinkFrameChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize) {
        this.channel = channel;
        this.wsChannel = wsChannel;
        this.type = type;
        this.payloadSize = payloadSize;
    }

    @Override
    public SimpleSetter<? extends StreamSinkFrameChannel> getWriteSetter() {
        return writeSetter;
    }

    public long getPayloadSize() {
        return payloadSize;
    }

    /**
     * Return the RSV for the extension. Default is 0.
     */
    public int getRsv() {
        return rsv;
    }

    /**
     * Return <code>true</code> if this {@link StreamSinkFrameChannel} is the final fragement
     */
    public boolean isFinalFragment() {
        return finalFragment;
    }

    /**
     * Set if this {@link StreamSinkFrameChannel} is the final fragement.
     *
     * This can only be set before any write or transfer operations where passed
     * to the wrapped {@link StreamSinkChannel}, after that an {@link IllegalStateException} will be thrown.
     *
     * @param finalFragment
     */
    public void setFinalFragment(boolean finalFragment) {
        if (!isFragmentationSupported() && !finalFragment)   {
            throw WebSocketMessages.MESSAGES.fragmentationNotSupported();
        }
        if (written > 0) {
            throw WebSocketMessages.MESSAGES.writeInProgress();
        }
        this.finalFragment = finalFragment;
    }

    public abstract boolean isFragmentationSupported();

    public abstract boolean areExtensionsSupported();

    /**
     * Set the RSV which is used for extensions.
     *
     * This can only be set before any write or transfer operations where passed
     * to the wrapped {@link StreamSinkChannel}, after that an {@link IllegalStateException} will be thrown.
     *
     * @param rsv
     */
    public void setRsv(int rsv) {
        if (!areExtensionsSupported() && rsv != 0)   {
            throw WebSocketMessages.MESSAGES.extensionsNotSupported();
        }
        if (written > 0) {
            throw WebSocketMessages.MESSAGES.writeInProgress();
        }
        this.rsv = rsv;
    }

    /**
     * Mark this channel as active
     */
    protected final void activate() {
        ChannelState old = state;
        if (old == ChannelState.WAITING) {
            if (!stateUpdater.compareAndSet(this, ChannelState.WAITING, ChannelState.ACTIVE)) {
                old = state;
            }
        }

        // now notify the waiters if any
        synchronized (writeWaitLock) {
            if (waiters > 0) {
                writeWaitLock.notifyAll();
            }
        }

        if (old == ChannelState.CLOSED) {
            //the channel was closed with nothing being written
            //we simply activate the next channel.
            wsChannel.complete(this);
            return;
        }

        synchronized (this) {
            if (writesSuspended) {
                if (channel.isWriteResumed()) {
                    channel.suspendWrites();
                }
            } else {
                if (channel.isOpen()) {
                    if (!channel.isWriteResumed()) {
                        channel.resumeWrites();
                    }
                } else {
                    //if the underlying channel has closed then we just invoke the write listener directly
                    ChannelListeners.invokeChannelListener(this, writeSetter.get());
                }
            }
        }
    }

    /**
     * Return the {@link WebSocketFrameType} for which the {@link StreamSinkFrameChannel} was obtained.
     */
    public WebSocketFrameType getType() {
        return type;
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    /**
     * Closes the channel.
     * <p/>
     * If this channel has not been previously closed and fully flushed then
     * this will result in the web socket channel becoming broken and unusable.
     * <p/>
     * As per the XNIO contract clients should use {@link #shutdownWrites()} and {@link #flush()}
     * for normal shutdowns.
     */
    @Override
    public final void close() throws IOException {
        ChannelState oldState;
        do {
            oldState = state;
            if (oldState == ChannelState.CLOSED) {
                return;
            }
        } while (stateUpdater.compareAndSet(this, oldState, ChannelState.CLOSED));

        if (oldState == ChannelState.WAITING) {
            // now notify the waiter
            synchronized (writeWaitLock) {
                if (waiters > 0) {
                    writeWaitLock.notifyAll();
                }
            }
        }
        try {
            close0();
            WebSocketLogger.REQUEST_LOGGER.closedBeforeFinishedWriting(this);
            wsChannel.markBroken();
        } finally {
            ChannelListeners.invokeChannelListener(this, closeSetter.get());
        }
    }


    /**
     * @throws IOException Get thrown if an problem during the close operation is detected
     */
    protected abstract void close0() throws IOException;

    @Override
    public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        long result = write0(srcs, offset, length);
        this.written += result;
        return result;
    }

    /**
     * @see {@link StreamSinkChannel#write(ByteBuffer[], int, int)}
     */
    protected abstract long write0(ByteBuffer[] srcs, int offset, int length) throws IOException;

    @Override
    public final long write(ByteBuffer[] srcs) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        long result = write0(srcs);
        this.written += result;
        return result;
    }

    /**
     * @see StreamSinkChannel#write(ByteBuffer[])
     */
    protected abstract long write0(ByteBuffer[] srcs) throws IOException;

    @Override
    public final int write(ByteBuffer src) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        int result = write0(src);
        this.written += result;
        return result;
    }

    /**
     * @see StreamSinkChannel#write(ByteBuffer)
     */
    protected abstract int write0(ByteBuffer src) throws IOException;


    @Override
    public final long transferFrom(FileChannel src, long position, long count) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        long result = transferFrom0(src, position, count);
        this.written += result;
        return result;
    }

    /**
     * @see StreamSinkChannel#transferFrom(FileChannel, long, long)
     */
    protected abstract long transferFrom0(FileChannel src, long position, long count) throws IOException;

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        long result = transferFrom0(source, count, throughBuffer);
        this.written += result;
        return result;
    }

    /**
     * @see StreamSinkChannel#transferFrom(StreamSourceChannel, long, ByteBuffer)
     */
    protected abstract long transferFrom0(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException;


    @Override
    public boolean isOpen() {
        return state != ChannelState.CLOSED && state != ChannelState.SHUTDOWN;
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
    public synchronized void suspendWrites() {
        if (isActive()) {
            channel.suspendWrites();
        }
        writesSuspended = true;
    }


    @Override
    public synchronized void resumeWrites() {
        if (isActive()) {
            channel.resumeWrites();
        }
        writesSuspended = false;
    }

    /**
     * Return <code>true</code> if this {@link StreamSinkFrameChannel} is currently in use.
     */
    protected final boolean isActive() {
        return state != ChannelState.WAITING;
    }

    @Override
    public boolean isWriteResumed() {
        return !writesSuspended;
    }

    @Override
    public void wakeupWrites() {
        resumeWrites();
        ChannelListeners.invokeChannelListener(this, writeSetter.get());
    }

    @Override
    public void shutdownWrites() throws IOException {
        ChannelState oldState;
        do {
            oldState = state;
            if (oldState == ChannelState.SHUTDOWN || oldState == ChannelState.CLOSED) {
                return;
            }
            if (written != payloadSize) {
                //we have not fully written out our payload
                //so throw an IOException
                throw WebSocketMessages.MESSAGES.notAllPayloadDataWritten(written, payloadSize);
            }
        } while (stateUpdater.compareAndSet(this, oldState, ChannelState.SHUTDOWN));

        //if we have blocked threads we should wake them up just in case
        if (oldState == ChannelState.WAITING) {
            // now notify the waiter
            synchronized (writeWaitLock) {
                if (waiters > 0) {
                    writeWaitLock.notifyAll();
                }
            }
        }
    }

    @Override
    public void awaitWritable() throws IOException {
        ChannelState currentState = state;
        if (currentState == ChannelState.ACTIVE) {
            channel.awaitWritable();
        } else if (currentState == ChannelState.WAITING) {
            try {
                synchronized (writeWaitLock) {
                    if (state == ChannelState.WAITING) {
                        waiters++;
                        try {
                            writeWaitLock.wait();
                        } finally {
                            waiters--;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }
        //otherwise we just return, next attempt to write should throw an exception
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        ChannelState currentState = state;
        if (currentState == ChannelState.ACTIVE) {
            channel.awaitWritable();
        } else if (currentState == ChannelState.WAITING) {
            try {
                synchronized (writeWaitLock) {
                    if (state == ChannelState.WAITING) {
                        waiters++;
                        try {
                            writeWaitLock.wait(timeUnit.toMillis(time));
                        } finally {
                            waiters--;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }
        //otherwise we just return, next attempt to write should throw an exception
    }

    protected long getWritten() {
        return written;
    }

    protected ChannelState getState() {
        return state;
    }

    @Override
    public XnioExecutor getWriteThread() {
        return channel.getWriteThread();
    }

    @Override
    public boolean flush() throws IOException {
        if (!isActive()) {
            return false;
        }
        if (state == ChannelState.CLOSED) {
            throw WebSocketMessages.MESSAGES.channelClosed();
        }
        boolean flushed = flush0();
        if (flushed && state == ChannelState.SHUTDOWN) {
            state = ChannelState.CLOSED;
            try {
                close0();
                wsChannel.complete(this);
            } finally {
                ChannelListeners.invokeChannelListener(this, closeSetter.get());
            }
        }
        return flushed;
    }

    protected abstract boolean flush0() throws IOException;

    /**
     * Throws an {@link IOException} if the {@link #isOpen()} returns <code>false</code>
     */
    protected final void checkClosed() throws IOException {
        final ChannelState state = this.state;
        if (state == ChannelState.CLOSED || state == ChannelState.SHUTDOWN) {
            throw WebSocketMessages.MESSAGES.channelClosed();
        }
    }

    public static enum ChannelState {
        /**
         * channel is waiting to be the active writer
         */
        WAITING,
        /**
         * channel is the active writer
         */
        ACTIVE,
        /**
         * writes have been shutdown
         */
        SHUTDOWN,
        /**
         * channel is closed
         */
        CLOSED,
    }
}
