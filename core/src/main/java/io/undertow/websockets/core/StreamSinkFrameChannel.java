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
package io.undertow.websockets.core;

import org.xnio.Buffers;
import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.FixedLengthOverflowException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class StreamSinkFrameChannel implements StreamSinkChannel, SendChannel {

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
    private int waiters;

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

    private boolean frameStartWritten;
    private boolean frameEndWritten;

    private static final AtomicReferenceFieldUpdater<StreamSinkFrameChannel, ChannelState> stateUpdater = AtomicReferenceFieldUpdater.newUpdater(StreamSinkFrameChannel.class, ChannelState.class, "state");
    private volatile ChannelState state = ChannelState.WAITING;

    protected enum ChannelState {
        /**
         * channel is waiting to be the active writer
         */
        WAITING,
        /**
         * Channel is shut down, but has not been activated yet
         */
        WAITING_SHUTDOWN,
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

    protected StreamSinkFrameChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize) {
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
     * Return {@code true} if this {@link StreamSinkFrameChannel} is the final fragement
     */
    public boolean isFinalFragment() {
        return finalFragment;
    }

    /**
     * Set if this {@link StreamSinkFrameChannel} is the final fragement.
     * <p/>
     * This can only be set before any write or transfer operations where passed
     * to the wrapped {@link StreamSinkChannel}, after that an {@link IllegalStateException} will be thrown.
     *
     */
    public void setFinalFragment(boolean finalFragment) {
        if (!isFragmentationSupported() && !finalFragment) {
            throw WebSocketMessages.MESSAGES.fragmentationNotSupported();
        }
        if (written > 0) {
            throw WebSocketMessages.MESSAGES.writeInProgress();
        }
        this.finalFragment = finalFragment;
    }

    /**
     * Set the RSV which is used for extensions.
     * <p/>
     * This can only be set before any write or transfer operations where passed
     * to the wrapped {@link StreamSinkChannel}, after that an {@link IllegalStateException} will be thrown.
     *
     */
    public void setRsv(int rsv) {
        if (!areExtensionsSupported() && rsv != 0) {
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

    /**
     * {@code true} if fragementation is supported for the {@link WebSocketFrameType}.
     */
    public boolean isFragmentationSupported() {
        return false;
    }

    /**
     * {@code true} if extendsions are supported for the {@link WebSocketFrameType}.
     */
    public boolean areExtensionsSupported() {
        return false;
    }

    private ByteBuffer getFrameStart() {
        if (start == null) {
            start = createFrameStart();
            start.flip();
        }
        return start;
    }

    private ByteBuffer getFrameEnd() {
        if (end == null) {
            end = createFrameEnd();
            end.flip();
        }
        return end;
    }

    private void freeStartAndEndFrame() {
        freeFrameStart();
        freeFrameEnd();
    }

    private void freeFrameStart() {
        if (start != null) {
            if(!start.hasRemaining()) {
                frameStartWritten = true;
                frameStartComplete();
            }
        }
    }

    private void freeFrameEnd() {
        if (end != null) {
            if(!end.hasRemaining()) {
                frameEndWritten = true;
                endFrameComplete();
            }
        }
    }

    /**
     * Is called once the start of the frame was witten. Sub-classes may override this to free up resources
     */
    protected void frameStartComplete() {
        // NOOP
    }

    /**
     * Is called once the end of the frame was witten. Sub-classes may override this to free up resources
     */
    protected void endFrameComplete() {
        // NOOP
    }

    /**
     * Compose a new array of ByteBuffer which contains also the start and end of the frame if possible. This allows
     * us to use gathering writes.
     */
    private ByteBuffer[] composeBuffers(ByteBuffer[] buffers, int offset, int length) {
        boolean needsStart = !frameStartWritten;
        boolean needsEnd = bytesToWrite() <= maxBytes(buffers, offset, length);

        if (!needsStart && !needsEnd) {
            ByteBuffer[] bufs = new ByteBuffer[length];
            System.arraycopy(buffers, offset, bufs, 0, bufs.length);
            return bufs;
        }
        if (!needsStart && needsEnd) {
            ByteBuffer[] bufs = new ByteBuffer[length + 1];
            System.arraycopy(buffers, offset, bufs, 0, length);
            bufs[bufs.length -1] = getFrameEnd();
            return bufs;
        }
        if (needsStart && !needsEnd) {
            ByteBuffer[] bufs = new ByteBuffer[length + 1];
            System.arraycopy(buffers, offset, bufs, 1, length);
            bufs[0] = getFrameStart();
            return bufs;
        }
        if (needsStart && needsEnd) {
            ByteBuffer[] bufs = new ByteBuffer[length + 2];
            System.arraycopy(buffers, offset, bufs, 1, length);
            bufs[0] = getFrameStart();
            bufs[bufs.length -1] = getFrameEnd();
            return bufs;
        }
        throw new IllegalStateException();
    }

    /**
     * Return the max bytes that can be written from the given ByteBuffers with the offset and length
     */
    private static long maxBytes(ByteBuffer[] buffers, int offset, int length) {
        long max = 0;
        for (; offset < length; offset++) {
            max += buffers[offset].remaining();
        }
        return max;
    }

    protected boolean flush0() throws IOException {
        if (payloadSize == 0) {
           // if the payload is 0 it is possible that we need to handle the start and end of the frame
           // in the flush.
           if (!frameStartWritten) {

               // the start of the fame was not written yet, try to use gathering writes to write out the start and
               // end of the frame for performance reasons
               ByteBuffer[] bufs = {getFrameStart(), getFrameEnd()};
               while(!frameStartWritten || !frameEndWritten) {
                   try {
                       long w = channel.write(bufs);
                       if (w == -1) {
                           throw WebSocketMessages.MESSAGES.channelClosed();
                       } else if (w == 0) {
                           return false;
                       }
                   } finally {
                       freeStartAndEndFrame();
                   }
               }
               return true;
           }
        }

        if (frameStartWritten) {
            if (getState() == ChannelState.SHUTDOWN) {

                try {
                    // write the end if needed
                    if (!frameEndWritten) {
                        ByteBuffer end = getFrameEnd();
                        while (end.hasRemaining()) {
                            int b = channel.write(end);

                            if (b == -1) {
                                throw WebSocketMessages.MESSAGES.channelClosed();
                            } else if (b == 0) {
                                return false;
                            }
                        }
                    }

                    return true;
                } finally {
                    freeStartAndEndFrame();
                }

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
        ChannelState old, newState;
        do {
            old = state;
            if(old == ChannelState.WAITING) {
                newState = ChannelState.ACTIVE;
            } else if (old == ChannelState.WAITING_SHUTDOWN) {
                newState = ChannelState.SHUTDOWN;
            } else {
                break;
            }
        } while (!stateUpdater.compareAndSet(this, old, newState));

        // now notify the waiters if any
        notifyWriteWaiters();

        if (old == ChannelState.CLOSED) {
            //the channel was closed with nothing being written
            //we simply activate the next channel.
            wsChannel.complete(this);
            return;
        }

        synchronized (this) {
            if (writesSuspended) {
                channel.suspendWrites();
            } else {
                if (channel.isOpen()) {
                    channel.resumeWrites();
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

    @Override
    public XnioIoThread getIoThread() {
        return channel.getIoThread();
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
    public final void close() {
        ChannelState oldState;
        do {
            oldState = state;
            if (oldState == ChannelState.CLOSED) {
                return;
            }
        } while (stateUpdater.compareAndSet(this, oldState, ChannelState.CLOSED));

        if (oldState == ChannelState.WAITING) {
            // now notify the waiter
            notifyWriteWaiters();
        }
        try {
            WebSocketLogger.REQUEST_LOGGER.closedBeforeFinishedWriting(this);
            wsChannel.markBroken();
        } finally {
            ChannelListeners.invokeChannelListener(this, closeSetter.get());
        }
    }

    private void notifyWriteWaiters() {
        synchronized (writeWaitLock) {
            if (waiters > 0) {
                writeWaitLock.notifyAll();
            }
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        checkClosed();

        if (!isActive()) {
            return 0;
        }
        long toWrite = bytesToWrite();

        if (toWrite < 1) {
            if(Buffers.remaining(srcs) == 0) {
                return 0;
            } else {
                throw new FixedLengthOverflowException();
            }
        }
        ByteBuffer[] bufs = composeBuffers(srcs, offset, length);

        int oldLimit = -1;

        long extra = 0;
        int i = 0;
        int e = 0;
        if (bufs.length == length + 2) {
            i = 1;
            e = bufs.length -1;
            extra = getFrameStart().remaining() + getFrameEnd().remaining();
        } else if (bufs.length == length + 1) {
            if (frameStartWritten) {
                e = bufs.length - 1;
                extra = getFrameEnd().remaining();
            } else {
                i = 1;
                extra = getFrameStart().remaining();
            }
        }

        int last = -1;
        for (; i < e; i++) {
            ByteBuffer src = bufs[i];
            if (toWrite < src.remaining()) {
                oldLimit = src.limit();
                src.limit((int) toWrite);
                last = i;
                break;
            }
            toWrite -= src.remaining();
        }
        try {
            long result = write0(bufs, 0, bufs.length);

            if (result < 1) {
                return result;
            }

            result -= extra;

            if (result < 1) {
                return 0;
            }

            written += result;
            return result;
        } finally {

            if (oldLimit != -1) {
                bufs[last].limit(oldLimit);
            }
            freeStartAndEndFrame();
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
        // TODO: Maybe implement directly to safe array creation
        return (int) write(new ByteBuffer[] {src});
    }

    @Override
    public final long transferFrom(FileChannel src, long position, long count) throws IOException {
        checkClosed();
        if (!isActive()) {
            return 0;
        }

        long toWrite = bytesToWrite();
        if (toWrite < 1) {
            return -1;
        }

        if (!frameStartWritten) {
            ByteBuffer start = getFrameStart();
            while (start.hasRemaining()) {
                int w = channel.write(start);
                if (w == 0) {
                    return 0;
                }
                if (w == -1) {
                    throw WebSocketMessages.MESSAGES.channelClosed();
                }
            }
            // start was written free it.
            freeFrameStart();
        }

        if (toWrite < count) {
            count = toWrite;
        }
        long result = transferFrom0(src, position, count);
        if (result > 0) {
            written += result;
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

        long toWrite = bytesToWrite();
        if (toWrite < 1) {
            return -1;
        }
        if (toWrite < count) {
            count = toWrite;
        }
        return WebSocketUtils.transfer(source, count, throughBuffer, this);
    }

    @Override
    public boolean isOpen() {
        final ChannelState state = this.state;
        return state != ChannelState.CLOSED && state != ChannelState.SHUTDOWN && state != ChannelState.WAITING_SHUTDOWN;
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
    public <T> T setOption(Option<T> option, T value) throws IOException {
        return channel.setOption(option, value);
    }

    @Override
    public Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public synchronized void suspendWrites() {
        writesSuspended = true;
        ChannelState state = this.state;
        if (state == ChannelState.ACTIVE || state == ChannelState.SHUTDOWN) {
            channel.suspendWrites();
        }
    }


    @Override
    public synchronized void resumeWrites() {
        writesSuspended = false;
        ChannelState state = stateUpdater.get(this);
        if (state == ChannelState.ACTIVE || state == ChannelState.SHUTDOWN) {
            channel.resumeWrites();
        } else if (state == ChannelState.CLOSED) {
            queueWriteListener();
        }
    }

    /**
     * Return {@code true} if this {@link StreamSinkFrameChannel} is currently in use.
     */
    protected final boolean isActive() {
        final ChannelState state = this.state;
        return state != ChannelState.WAITING && state != ChannelState.WAITING_SHUTDOWN;
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
        ChannelState oldState, newState;
        do {
            oldState = state;
            if (oldState == ChannelState.SHUTDOWN || oldState == ChannelState.CLOSED || oldState == ChannelState.WAITING_SHUTDOWN) {
                return;
            }
            if(oldState == ChannelState.WAITING) {
                newState = ChannelState.WAITING_SHUTDOWN;
            } else {
                newState = ChannelState.SHUTDOWN;
            }

        } while (stateUpdater.compareAndSet(this, oldState, newState));

        //if we have blocked threads we should wake them up just in case
        if (oldState == ChannelState.WAITING) {
            // now notify the waiter
            notifyWriteWaiters();
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
                    while (state == ChannelState.WAITING) {
                        waiters++;
                        try {
                            writeWaitLock.wait();
                        } finally {
                            waiters--;
                        }
                    }
                }
            } catch (InterruptedException ignore) {
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
                    while (state == ChannelState.WAITING) {
                        waiters++;
                        try {
                            writeWaitLock.wait(timeUnit.toMillis(time));
                        } finally {
                            waiters--;
                        }
                    }
                }
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }
        //otherwise we just return, next attempt to write should throw an exception
    }

    protected ChannelState getState() {
        return state;
    }

    @Override
    public XnioExecutor getWriteThread() {
        return channel.getWriteThread();
    }

    @Override
    public final boolean flush() throws IOException {
        if (!isActive()) {
            return false;
        }
        if (state == ChannelState.CLOSED) {
            throw WebSocketMessages.MESSAGES.channelClosed();
        }
        boolean flushed = flush0();
        if (flushed && state == ChannelState.SHUTDOWN) {
            if(type == WebSocketFrameType.CLOSE) {
                //if this is a close frame we shut down the underlying channel
                channel.shutdownWrites();
                flushed = channel.flush();
                if(!flushed) {
                    return false;
                }
            }
            state = ChannelState.CLOSED;
            try {
                wsChannel.complete(this);
            } finally {
                ChannelListeners.invokeChannelListener(this, closeSetter.get());
            }
        }
        return flushed;
    }

    /**
     * Return the bytes which need to get written before the frame is complete
     */
    protected final long bytesToWrite() {
        return payloadSize - written;
    }

    /**
     * Throws an {@link IOException} if the {@link #isOpen()} returns {@code false}
     */
    protected final void checkClosed() throws IOException {
        final ChannelState state = this.state;
        if (state == ChannelState.CLOSED || state == ChannelState.SHUTDOWN || state == ChannelState.WAITING_SHUTDOWN) {
            throw WebSocketMessages.MESSAGES.channelClosed();
        }
    }

    public WebSocketChannel getWebSocketChannel() {
        return wsChannel;
    }
}
