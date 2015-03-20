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
import io.undertow.util.ImmediatePooled;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pooled;
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

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;

/**
 * Framed Stream Sink Channel.
 * <p/>
 * Thread safety notes:
 * <p/>
 * The general contract is that this channel is only to be used by a single thread at a time. The only exception to this is
 * during flush. A flush will only happen when {@link #readyForFlush} is set, and while this bit is set the buffer
 * must not be modified.
 *
 * @author Stuart Douglas
 */
public abstract class AbstractFramedStreamSinkChannel<C extends AbstractFramedChannel<C, R, S>, R extends AbstractFramedStreamSourceChannel<C, R, S>, S extends AbstractFramedStreamSinkChannel<C, R, S>> implements StreamSinkChannel {

    private static final Pooled<ByteBuffer> EMPTY_BYTE_BUFFER = new ImmediatePooled<>(ByteBuffer.allocateDirect(0));

    private Pooled<ByteBuffer> buffer;
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

    private SendFrameHeader header;
    private Pooled<ByteBuffer> trailer;

    private static final int STATE_CLOSED = 1;
    private static final int STATE_WRITES_RESUMED = 1 << 1;
    private static final int STATE_WRITES_SHUTDOWN = 1 << 2;
    private static final int STATE_IN_LISTENER_LOOP = 1 << 3;
    private static final int STATE_FIRST_DATA_WRITTEN = 1 << 4;


    protected AbstractFramedStreamSinkChannel(C channel) {
        this.channel = channel;
        this.buffer = channel.getBufferPool().allocate();
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, this);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    @Override
    public void suspendWrites() {
        state &= ~STATE_WRITES_RESUMED;
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

    /**
     * Returns the footer for the current frame.
     *
     * @return The footer for the current frame, or null
     */
    final ByteBuffer getFrameFooter() {
        if (trailer == null) {
            trailer = createFrameFooter();
            if (trailer == null) {
                trailer = EMPTY_BYTE_BUFFER;
            }
        }
        return trailer.getResource();
    }

    protected Pooled<ByteBuffer> createFrameFooter() {
        return null;
    }

    @Override
    public boolean isWriteResumed() {
        return anyAreSet(state, STATE_WRITES_RESUMED);
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
        boolean alreadyResumed = anyAreSet(state, STATE_WRITES_RESUMED);
        if(!wakeup && alreadyResumed) {
            return;
        }
        state |= STATE_WRITES_RESUMED;
        if(readyForFlush && !wakeup) {
            //we already have data queued to be flushed
            return;
        }

        if (!anyAreSet(state, STATE_IN_LISTENER_LOOP)) {
            state |= STATE_IN_LISTENER_LOOP;
            getIoThread().execute(new Runnable() {

                int loopCount = 0;

                @Override
                public void run() {
                    try {
                        ChannelListener<? super S> listener = getWriteListener();
                        if (listener == null || !isWriteResumed()) {
                            return;
                        }
                        if(loopCount++ == 100) {
                            //should never happen
                            UndertowLogger.ROOT_LOGGER.listenerNotProgressing();
                            IoUtils.safeClose(AbstractFramedStreamSinkChannel.this);
                            return;
                        }
                        ChannelListeners.invokeChannelListener((S) AbstractFramedStreamSinkChannel.this, listener);
                        //if writes are shutdown or we become active then we stop looping
                        //we stop when writes are shutdown because we can't flush until we are active
                        //although we may be flushed as part of a batch

                        if (allAreSet(state, STATE_WRITES_RESUMED) && allAreClear(state, STATE_CLOSED) && !broken && !readyForFlush && !fullyFlushed) {
                            getIoThread().execute(this);
                        }
                    } finally {
                        state &= ~STATE_IN_LISTENER_LOOP;
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
        state |= STATE_WRITES_SHUTDOWN;
        queueFinalFrame();
    }

    private void queueFinalFrame() throws IOException {
        if (!readyForFlush && !fullyFlushed && allAreClear(state, STATE_CLOSED)  && !broken && !finalFrameQueued) {
            readyForFlush = true;
            buffer.getResource().flip();
            state |=  STATE_FIRST_DATA_WRITTEN;
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
        if(buffer != null && buffer.getResource().position() > 0) {
            handleBufferFull();
            return !readyForFlush;
        }
        return true;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        int state = this.state;
        if (readyForFlush) {
            return 0; //we can't do anything, we are waiting for a flush
        }
        if (anyAreSet(state, STATE_CLOSED | STATE_WRITES_SHUTDOWN) || broken) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        long copied = Buffers.copy(this.buffer.getResource(), srcs, offset, length);
        if (!buffer.getResource().hasRemaining()) {
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
        int state = this.state;
        if (readyForFlush) {
            return 0; //we can't do anything, we are waiting for a flush
        }
        if (anyAreSet(state, STATE_CLOSED | STATE_WRITES_SHUTDOWN) || broken) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        int copied = Buffers.copy(this.buffer.getResource(), src);
        if (!buffer.getResource().hasRemaining()) {
            handleBufferFull();
        }
        return copied;
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
        if (!readyForFlush) {
            readyForFlush = true;
            getBuffer().flip();
            state |= STATE_FIRST_DATA_WRITTEN;
            channel.queueFrame((S) this);
        }
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
            buffer.free();
            buffer = null;
            if (header != null && header.getByteBuffer() != null) {
                header.getByteBuffer().free();
            }
            if (trailer != null) {
                trailer.free();
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
        return buffer.getResource();
    }

    /**
     * Method that is invoked when a frame has been fully flushed. This method is only invoked by the IO thread
     */
    final void flushComplete() throws IOException {
        try {
            int remaining = header.getRemainingInBuffer();
            boolean finalFrame = finalFrameQueued;
            boolean channelClosed = finalFrame && remaining == 0 && !header.isAnotherFrameRequired();
            if(remaining > 0) {
                buffer.getResource().limit(buffer.getResource().limit() + remaining);
                if(finalFrame) {
                    //we clear the final frame flag, as it could not actually be written out
                    //note that we don't attempt to requeue, as whatever stopped it from being written will likely still
                    //be an issue
                    this.finalFrameQueued = false;
                }
            } else if(header.isAnotherFrameRequired()) {
                this.finalFrameQueued = false;
            }
            if (channelClosed) {
                fullyFlushed = true;
                buffer.free();
                buffer = null;
            } else {
                buffer.getResource().compact();
            }
            if (header.getByteBuffer() != null) {
                header.getByteBuffer().free();
            }
            trailer.free();
            header = null;
            trailer = null;

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
            if(header != null && header.getByteBuffer() != null) {
                header.getByteBuffer().free();
            }
            if(trailer != null) {
                trailer.free();
            }
            if(buffer != null) {
                buffer.free();
                buffer = null;
            }
        }
    }

    ChannelListener<? super S> getWriteListener() {
        return writeSetter.get();
    }

    private void wakeupWaiters() {
        if(waiterCount > 0) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    public C getChannel() {
        return channel;
    }

    public boolean isBroken() {
        return broken;
    }
}
