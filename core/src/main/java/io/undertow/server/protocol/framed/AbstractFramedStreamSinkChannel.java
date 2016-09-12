/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.protocol.framed;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.util.ImmediatePooledByteBuffer;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Framed Stream Sink Channel.
 * <p>
 * Thread safety notes:
 * <p>
 * The general contract is that this channel is only to be used by a single thread at a time. The only exception to this is
 * during flush. A flush will only happen when {@link #readyForFlush} is set, and while this bit is set the buffer
 * must not be modified.
 *
 * @author Stuart Douglas
 */
public abstract class AbstractFramedStreamSinkChannel<C extends AbstractFramedChannel<C, R, S>, R extends AbstractFramedStreamSourceChannel<C, R, S>, S extends AbstractFramedStreamSinkChannel<C, R, S>> implements StreamSinkChannel {

    private static final PooledByteBuffer EMPTY_BYTE_BUFFER = new ImmediatePooledByteBuffer(ByteBuffer.allocateDirect(0));

    private final C channel;
    private final ChannelListener.SimpleSetter<S> writeSetter = new ChannelListener.SimpleSetter<>();
    private final ChannelListener.SimpleSetter<S> closeSetter = new ChannelListener.SimpleSetter<>();

    private final Object lock = new Object();

    /**
     * the state variable, this must only be access by the thread that 'owns' the channel
     */
    private volatile int state = 0;
    /**
     * If this channel is ready for flush, updated by multiple threads. In general it will be set by the thread
     * that 'owns' the channel, and cleared by the IO thread
     */
    private volatile boolean readyForFlush;

    /**
     * If all the data has been written out and the channel has been fully flushed
     */
    private volatile boolean fullyFlushed;

    /**
     * If the last frame has been queued.
     *
     * Note that this may not actually be the final frame in some circumstances, e.g. if the final frame
     * is two large to fit in the flow control window. In this case the flag may be cleared after flush is complete.
     */
    private volatile boolean finalFrameQueued;

    /**
     * If this channel is broken, updated by multiple threads
     */
    private volatile boolean broken;

    private volatile int waiterCount = 0;

    private volatile SendFrameHeader header;
    private volatile PooledByteBuffer writeBuffer;
    private volatile PooledByteBuffer body;

    private static final int STATE_CLOSED = 1;
    private static final int STATE_WRITES_SHUTDOWN = 1 << 1;
    private static final int STATE_FIRST_DATA_WRITTEN = 1 << 2;
    private static final int STATE_PRE_WRITE_CALLED = 1 << 3;

    private volatile boolean bufferFull;
    private volatile boolean writesResumed;
    @SuppressWarnings("unused")
    private volatile int inListenerLoop;

    private static final AtomicIntegerFieldUpdater<AbstractFramedStreamSinkChannel> inListenerLoopUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractFramedStreamSinkChannel.class, "inListenerLoop");

    protected AbstractFramedStreamSinkChannel(C channel) {
        this.channel = channel;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, this);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    @Override
    public void suspendWrites() {
        writesResumed = false;
    }

    /**
     * Returns the header for the current frame.
     *
     * This consists of the frame data, and also an integer specifying how much data is remaining in the buffer.
     * If this is non-zero then this method must adjust the buffers limit accordingly.
     *
     * It is expected that this will be used when limits on the size of a data frame prevent the whole buffer from
     * being sent at once.
     *
     *
     * @return The header for the current frame, or null
     */
    final SendFrameHeader getFrameHeader() throws IOException {
        if (header == null) {
            header = createFrameHeader();
            if (header == null) {
                header = new SendFrameHeader(0, null);
            }
        }
        return header;
    }

    protected SendFrameHeader createFrameHeader() throws IOException{
        return null;
    }

    protected PooledByteBuffer createFrameFooter() {
        return null;
    }

    final void preWrite() {
        if(allAreClear(state, STATE_PRE_WRITE_CALLED)) {
            state |= STATE_PRE_WRITE_CALLED;
            body = preWriteTransform(body);
        }
    }

    protected PooledByteBuffer preWriteTransform(PooledByteBuffer body) {
        return body;
    }

    @Override
    public boolean isWriteResumed() {
        return writesResumed;
    }

    @Override
    public void wakeupWrites() {
        resumeWritesInternal(true);
    }

    @Override
    public void resumeWrites() {
        resumeWritesInternal(false);
    }

    protected void resumeWritesInternal(boolean wakeup) {
        boolean alreadyResumed = writesResumed;
        if(!wakeup && alreadyResumed) {
            return;
        }
        writesResumed = true;
        if(readyForFlush && !wakeup) {
            //we already have data queued to be flushed
            return;
        }

        if (inListenerLoopUpdater.compareAndSet(this, 0, 1)) {
            getChannel().runInIoThread(new Runnable() {

                int loopCount = 0;

                @Override
                public void run() {
                    try {
                        ChannelListener<? super S> listener = getWriteListener();
                        if (listener == null || !isWriteResumed()) {
                            return;
                        }
                        if (loopCount++ == 100) {
                            //should never happen
                            UndertowLogger.ROOT_LOGGER.listenerNotProgressing();
                            IoUtils.safeClose(AbstractFramedStreamSinkChannel.this);
                            return;
                        }
                        ChannelListeners.invokeChannelListener((S) AbstractFramedStreamSinkChannel.this, listener);

                    } finally {
                        inListenerLoopUpdater.set(AbstractFramedStreamSinkChannel.this, 0);
                    }
                    //if writes are shutdown or we become active then we stop looping
                    //we stop when writes are shutdown because we can't flush until we are active
                    //although we may be flushed as part of a batch
                    if (writesResumed && allAreClear(state, STATE_CLOSED) && !broken && !readyForFlush && !fullyFlushed) {
                        if (inListenerLoopUpdater.compareAndSet(AbstractFramedStreamSinkChannel.this, 0, 1)) {
                            getIoThread().execute(this);
                        }
                    }
                }
            });
        }

    }

    @Override
    public void shutdownWrites() throws IOException {
        if(anyAreSet(state, STATE_WRITES_SHUTDOWN) || broken ) {
            return;
        }
        // Queue prior to shutting down writes, since we might send the write buffer
        queueFinalFrame();
        state |= STATE_WRITES_SHUTDOWN;
    }

    private void queueFinalFrame() throws IOException {
        if (!readyForFlush && !fullyFlushed && allAreClear(state, STATE_CLOSED)  && !broken && !finalFrameQueued) {
            if( null == body && null != writeBuffer) {
                sendWriteBuffer();
            } else if (null == body) {
                body = EMPTY_BYTE_BUFFER;
            }
            readyForFlush = true;
            state |= STATE_FIRST_DATA_WRITTEN;
            state |= STATE_WRITES_SHUTDOWN; // Mark writes as shutdown as well, since we want that set prior to queueing
            finalFrameQueued = true;
            channel.queueFrame((S) this);
        }
    }

    protected boolean isFinalFrameQueued() {
        return finalFrameQueued;
    }

    @Override
    public void awaitWritable() throws IOException {
        if(Thread.currentThread() == getIoThread()) {
            throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
        }
        synchronized (lock) {
            if (anyAreSet(state, STATE_CLOSED) || broken) {
                return;
            }
            if (readyForFlush) {
                try {
                    waiterCount++;
                    //we need to re-check after incrementing the waiters count

                    if(readyForFlush && !anyAreSet(state, STATE_CLOSED) && !broken) {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                } finally {
                    waiterCount--;
                }
            }
        }
    }

    @Override
    public void awaitWritable(long l, TimeUnit timeUnit) throws IOException {
        if(Thread.currentThread() == getIoThread()) {
            throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
        }
        synchronized (lock) {
            if (anyAreSet(state, STATE_CLOSED) || broken) {
                return;
            }
            if (readyForFlush) {
                try {
                    waiterCount++;
                    if(readyForFlush && !anyAreSet(state, STATE_CLOSED) && !broken) {
                        lock.wait(timeUnit.toMillis(l));
                    }
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                } finally {
                    waiterCount--;
                }
            }
        }
    }

    @Override
    public XnioExecutor getWriteThread() {
        return channel.getIoThread();
    }

    @Override
    public ChannelListener.Setter<? extends S> getWriteSetter() {
        return writeSetter;
    }

    @Override
    public ChannelListener.Setter<? extends S> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return channel.getIoThread();
    }

    @Override
    public boolean flush() throws IOException {
        if(anyAreSet(state, STATE_CLOSED)) {
            return true;
        }
        if (broken) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }

        if (readyForFlush) {
            return false;
        }
        if (fullyFlushed) {
            state |= STATE_CLOSED;
            return true;
        }
        if (anyAreSet(state, STATE_WRITES_SHUTDOWN) && !finalFrameQueued) {
            queueFinalFrame();
            return false;
        }
        if(anyAreSet(state, STATE_WRITES_SHUTDOWN)) {
            return false;
        }
        if(isFlushRequiredOnEmptyBuffer() || (writeBuffer != null && writeBuffer.getBuffer().position() > 0)) {
            handleBufferFull();
            return !readyForFlush;
        }
        return true;
    }

    protected boolean isFlushRequiredOnEmptyBuffer() {
        return false;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if(!safeToSend()) {
            return 0;
        }
        if(writeBuffer == null) {
            writeBuffer = getChannel().getBufferPool().allocate();
        }
        ByteBuffer buffer = writeBuffer.getBuffer();
        int copied = Buffers.copy(buffer, srcs, offset, length);
        if(!buffer.hasRemaining()) {
            handleBufferFull();
        }
        return copied;
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if(!safeToSend()) {
            return 0;
        }
        if(writeBuffer == null) {
            writeBuffer = getChannel().getBufferPool().allocate();
        }
        ByteBuffer buffer = writeBuffer.getBuffer();
        int copied = Buffers.copy(buffer, src);
        if(!buffer.hasRemaining()) {
            handleBufferFull();
        }
        return copied;
    }

    /**
     * Send a buffer to this channel.
     *
     * @param pooled Pooled ByteBuffer to send. The buffer should have data available. This channel will free the buffer
     *               after sending data
     * @return true if the buffer was accepted; false if the channel needs to first be flushed
     * @throws IOException if this channel is closed
     */
    public boolean send(PooledByteBuffer pooled) throws IOException {
        if(isWritesShutdown()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return sendInternal(pooled);
    }

    protected boolean sendInternal(PooledByteBuffer pooled) throws IOException {
        if (safeToSend()) {
            this.body = pooled;
            return true;
        }
        return false;
    }

    protected boolean safeToSend() throws IOException {
        int state = this.state;
        if (readyForFlush) {
            return false; //we can't do anything, we are waiting for a flush
        }
        if( null != this.body) {
            return false; // already have a pooled buffer
        }
        if (anyAreSet(state, STATE_CLOSED) || broken) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return true;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Channels.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        return writeFinal(srcs, 0, srcs.length);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Channels.writeFinalBasic(this, src);
    }

    private void handleBufferFull() throws IOException {
        bufferFull = true;
        if (!readyForFlush) {
            sendWriteBuffer();
            readyForFlush = true;
            state |= STATE_FIRST_DATA_WRITTEN;
            channel.queueFrame((S) this);
        }
    }

    private void sendWriteBuffer() throws IOException {
        if(writeBuffer == null) {
            writeBuffer = EMPTY_BYTE_BUFFER;
        }
        writeBuffer.getBuffer().flip();
        if(!sendInternal(writeBuffer)) {
            throw UndertowMessages.MESSAGES.failedToSendAfterBeingSafe();
        }
        writeBuffer = null;
    }

    /**
     * @return <code>true</code> If this is the last frame that will be sent on this connection
     */
    protected abstract boolean isLastFrame();

    /**
     * @return true if the channel is ready to be flushed. When a channel is ready to be flushed nothing should modify the buffer,
     *         as it may be written out by another thread.
     */
    public boolean isReadyForFlush() {
        return readyForFlush;
    }

    /**
     * Returns true writes have been shutdown
     */
    public boolean isWritesShutdown() {
        return anyAreSet(state, STATE_WRITES_SHUTDOWN);
    }

    @Override
    public boolean isOpen() {
        return allAreClear(state, STATE_CLOSED);
    }

    @Override
    public void close() throws IOException {
        if(fullyFlushed || anyAreSet(state, STATE_CLOSED)) {
            return;
        }
        try {
            state |= STATE_CLOSED;
            if(writeBuffer != null) {
                writeBuffer.close();
                writeBuffer = null;
            }
            if(body != null) {
                body.close();
                body = null;
            }
            if (header != null && header.getByteBuffer() != null) {
                header.getByteBuffer().close();
                header = null;
            }
            if (anyAreSet(state, STATE_FIRST_DATA_WRITTEN)) {
                channelForciblyClosed();
            }
            //we need to wake up/invoke the write listener
            if (isWriteResumed()) {
                ChannelListeners.invokeChannelListener(getIoThread(), this, (ChannelListener) getWriteListener());
            }
            wakeupWrites();
        } finally {
            wakeupWaiters();
        }
    }

    /**
     * Called when a channel has been forcibly closed, and data (frames) have already been written.
     *
     * The action this should take is protocol dependent, e.g. for SPDY a RST_STREAM should be sent,
     * for websockets the channel should be closed.
     *
     * By default this will just close the underlying channel
     *
     * @throws IOException
     */
    protected void channelForciblyClosed() throws IOException {
        if(isFirstDataWritten()) {
            getChannel().markWritesBroken(null);
        }
        wakeupWaiters();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return false;
    }

    @Override
    public <T> T getOption(Option<T> tOption) throws IOException {
        return null;
    }

    @Override
    public <T> T setOption(Option<T> tOption, T t) throws IllegalArgumentException, IOException {
        return null;
    }

    public ByteBuffer getBuffer() {
        if(anyAreSet(state, STATE_CLOSED)) {
            throw new IllegalStateException();
        }
        if(body == null) {
            // TODO should we IllegalState here? we expect a buffer to already exist
            body = EMPTY_BYTE_BUFFER;
        }
        return body.getBuffer();
    }

    /**
     * Method that is invoked when a frame has been fully flushed. This method is only invoked by the IO thread
     */
    final void flushComplete() throws IOException {
        try {
            bufferFull = false;
            int remaining = header.getRemainingInBuffer();
            boolean finalFrame = finalFrameQueued;
            boolean channelClosed = finalFrame && remaining == 0 && !header.isAnotherFrameRequired();
            if(remaining > 0) {
                body.getBuffer().limit(body.getBuffer().limit() + remaining);
                if(finalFrame) {
                    //we clear the final frame flag, as it could not actually be written out
                    //note that we don't attempt to requeue, as whatever stopped it from being written will likely still
                    //be an issue
                    this.finalFrameQueued = false;
                }
            } else if(header.isAnotherFrameRequired()) {
                this.finalFrameQueued = false;
                if(body != null) {
                    body.close();
                    body = null;
                    state &= ~STATE_PRE_WRITE_CALLED;
                }
            } else if(body != null){
                body.close();
                body = null;
                state &= ~STATE_PRE_WRITE_CALLED;
            }
            if (channelClosed) {
                fullyFlushed = true;
                if(body != null) {
                    body.close();
                    body = null;
                    state &= ~STATE_PRE_WRITE_CALLED;
                }
            } else if (body != null) {
                // We still have a body, but since we just flushed, we transfer it to the write buffer.
                // This works as long as you call write() again
                body.getBuffer().compact();
                writeBuffer = body;
                body = null;
                state &= ~STATE_PRE_WRITE_CALLED;
            }

            if (header.getByteBuffer() != null) {
                header.getByteBuffer().close();
            }
            header = null;

            readyForFlush = false;
            if (isWriteResumed() && !channelClosed) {
                wakeupWrites();
            } else if(isWriteResumed()) {
                //we need to execute the write listener one last time
                //we need to dispatch it back to the IO thread, so we don't invoke it recursivly
                ChannelListeners.invokeChannelListener(getIoThread(), (S)this, getWriteListener());
            }

            final ChannelListener<? super S> closeListener = this.closeSetter.get();
            if (channelClosed && closeListener != null) {
                ChannelListeners.invokeChannelListener(getIoThread(), (S) AbstractFramedStreamSinkChannel.this, closeListener);
            }
            handleFlushComplete(channelClosed);
        } finally {
            wakeupWaiters();
        }
    }

    protected void handleFlushComplete(boolean finalFrame) {

    }

    protected boolean isFirstDataWritten() {
        return anyAreSet(state, STATE_FIRST_DATA_WRITTEN);
    }

    public void markBroken() {
        this.broken = true;
        try {
            wakeupWrites();
            wakeupWaiters();
            if (isWriteResumed()) {
                ChannelListener<? super S> writeListener = this.writeSetter.get();
                if (writeListener != null) {
                    ChannelListeners.invokeChannelListener(getIoThread(), (S) this, writeListener);
                }
            }
            ChannelListener<? super S> closeListener = this.closeSetter.get();
            if (closeListener != null) {
                ChannelListeners.invokeChannelListener(getIoThread(), (S) this, closeListener);
            }
        } finally {
            if(header != null) {
                if( header.getByteBuffer() != null) {
                    header.getByteBuffer().close();
                    header = null;
                }
            }
            if(body != null) {
                body.close();
                body = null;
            }
            if(writeBuffer != null) {
                writeBuffer.close();
                writeBuffer = null;
            }
        }
    }

    ChannelListener<? super S> getWriteListener() {
        return writeSetter.get();
    }

    private void wakeupWaiters() {
        if(waiterCount > 0) {
            synchronized (lock) {
                // It is possible that waiter count would be updated before gaining the lock, lets check one more
                // time whether the condition wasn't changed in the meantime.
                if (waiterCount > 0) {
                    lock.notifyAll();
                }
            }
        }
    }

    public C getChannel() {
        return channel;
    }

    public boolean isBroken() {
        return broken;
    }

    public boolean isBufferFull() {
        return bufferFull;
    }
}
