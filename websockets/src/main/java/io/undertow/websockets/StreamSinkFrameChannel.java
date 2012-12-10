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

    private volatile boolean writesSuspended = true;

    //todo: I don't think this belongs here
    private int rsv;
    private boolean finalFragment = true;


    /**
     * Buffer that holds the frame start
     */
    private ByteBuffer start;

    /**
     * buffer that holds the frame end
     */
    private ByteBuffer end;

    private boolean frameStartWritten = false;

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
     * Create the {@link ByteBuffer} that will be written as start of the frame.
     * <p/>
     *
     * @return The {@link ByteBuffer} which will be used to start a frame
     */
    protected abstract ByteBuffer createFrameStart();

    /**
     * Create the {@link ByteBuffer} that marks the end of the frame
     *
     * @return The {@link ByteBuffer} that marks the end of the frame
     */
    protected abstract ByteBuffer createFrameEnd();

    public boolean isFragmentationSupported() {
        return false;
    }

    public boolean areExtensionsSupported() {
        return false;
    }

    /**
     * todo: when we get serious about performance we will need to make sure we use direct buffers
     * and a gathering write for this, so we can write out the whole message with a single write()
     * call
     * @return true if the frame start was written
     * @throws IOException
     */
    private boolean writeFrameStart() throws IOException {
        if (!frameStartWritten) {
            if (start == null) {
                start = createFrameStart();
                start.flip();
            }
            while (start.hasRemaining()) {
                final int result = channel.write(start);
                if (result == -1) {
                    throw WebSocketMessages.MESSAGES.channelClosed();
                } else if (result == 0) {
                    return false;
                }
            }
            frameStartWritten = true;
            start = null;
        }
        return true;
    }


    protected boolean flush0() throws IOException {
        if (writeFrameStart()) {
            if (getState() == ChannelState.SHUTDOWN) {

                //we know end has not been written yet, or the state would be CLOSED
                if (end == null) {
                    end = createFrameEnd();
                    end.flip();
                }

                while (end.hasRemaining()) {
                    int b = channel.write(end);

                    if (b == -1) {
                        throw WebSocketMessages.MESSAGES.channelClosed();
                    } else if (b == 0) {
                        return false;
                    }
                }
                return true;
            } else {
                return true;
            }
        }
        return false;
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
                    } else {
                        channel.wakeupWrites();
                    }
                } else {
                    //if the underlying channel has closed then we just invoke the write listener directly
                    queueWriteListener();
                }
            }
        }
    }

    private void queueWriteListener() {
        getWriteThread().execute(new Runnable() {
            @Override
            public void run() {
                WebSocketLogger.REQUEST_LOGGER.debugf("Invoking directly queued write listener");
                ChannelListeners.invokeChannelListener(StreamSinkFrameChannel.this, writeSetter.get());
            }
        });
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
            WebSocketLogger.REQUEST_LOGGER.closedBeforeFinishedWriting(this);
            wsChannel.markBroken();
        } finally {
            ChannelListeners.invokeChannelListener(this, closeSetter.get());
        }
    }

    @Override
    public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        long toWrite = toWrite();
        if (toWrite < 1) {
            return -1;
        }
        if (!writeFrameStart()) {
            return 0;
        }
        int i = offset;
        int oldLimit = -1;
        for (; i < length; i++) {
            ByteBuffer src = srcs[i];
            if (toWrite < src.remaining()) {
                oldLimit = src.limit();
                src.limit((int) toWrite);
                i++;
                break;
            }
        }
        try {
            long result = write0(srcs, offset, i);
            if (result > 0) {
                this.written += result;
            }
            return result;
        } finally {
            if (oldLimit != -1) {
                srcs[offset + i].limit(oldLimit);
            }
        }
    }

    /**
     * @see {@link StreamSinkChannel#write(ByteBuffer[], int, int)}
     */
    protected long write0(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return channel.write(srcs, offset, length);
    }

    @Override
    public final long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public final int write(ByteBuffer src) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        if (!writeFrameStart()) {
            return 0;
        }
        int oldLimit = src.limit();
        long toWrite = toWrite();
        if (toWrite < 1) {
            return -1;
        }
        if (toWrite < src.remaining()) {
            src.limit((int) toWrite + src.position());
        }
        try {
            int result = write0(src);
            if (result > 0) {
                this.written += result;
            }
            return result;
        } finally {
           src.limit(oldLimit);
        }
    }

    /**
     * @see StreamSinkChannel#write(ByteBuffer)
     */
    protected int write0(ByteBuffer src) throws IOException {
        return channel.write(src);
    }


    @Override
    public final long transferFrom(FileChannel src, long position, long count) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }
        if (!writeFrameStart()) {
            return 0;
        }
        long toWrite = toWrite();
        if (toWrite < 1) {
            return -1;
        }
        if (toWrite < count) {
            count = toWrite;
        }
        long result = transferFrom0(src, position, count);
        if (result > 0) {
            this.written += result;
        }
        return result;
    }

    /**
     * @see StreamSinkChannel#transferFrom(FileChannel, long, long)
     */
    protected long transferFrom0(FileChannel src, long position, long count) throws IOException {
        return channel.transferFrom(src, position, count);
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        checkClosed();

        throughBuffer.clear();
        if (!isActive()) {
            return 0;
        }
        if (!writeFrameStart()) {
            return 0;
        }
        long toWrite = toWrite();
        if (toWrite < 1) {
            return -1;
        }
        if (toWrite < count) {
            count = toWrite;
        }
        long result = transferFrom0(source, count, throughBuffer);
        if (result > 0) {
            this.written += result;
        }
        return result;
    }

    /**
     * @see StreamSinkChannel#transferFrom(StreamSourceChannel, long, ByteBuffer)
     */
    protected long transferFrom0(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return channel.transferFrom(source, count, throughBuffer);
    }


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
        writesSuspended = true;
        if (isActive()) {
            channel.suspendWrites();
        }
    }


    @Override
    public synchronized void resumeWrites() {
        writesSuspended = false;
        ChannelState state = stateUpdater.get(this);
        if (state == ChannelState.ACTIVE || state == ChannelState.SHUTDOWN) {
            channel.resumeWrites();
        } else if(state == ChannelState.CLOSED) {
            queueWriteListener();
        }
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
        queueWriteListener();
        resumeWrites();
    }

    @Override
    public void shutdownWrites() throws IOException {
        ChannelState oldState;
        do {
            oldState = state;
            if (oldState == ChannelState.SHUTDOWN || oldState == ChannelState.CLOSED) {
                return;
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
                wsChannel.complete(this);
            } finally {
                ChannelListeners.invokeChannelListener(this, closeSetter.get());
            }
        }
        return flushed;
    }

    private long toWrite() {
        return payloadSize - written;
    }

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
