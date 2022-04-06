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

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.UndertowMessages;

/**
 * Source channel, used to receive framed messages.
 *
 * @author Stuart Douglas
 * @author Flavia Rainone
 */
public abstract class AbstractFramedStreamSourceChannel<C extends AbstractFramedChannel<C, R, S>, R extends AbstractFramedStreamSourceChannel<C, R, S>, S extends AbstractFramedStreamSinkChannel<C, R, S>> implements StreamSourceChannel {

    private final ChannelListener.SimpleSetter<? extends R> readSetter = new ChannelListener.SimpleSetter();
    private final ChannelListener.SimpleSetter<? extends R> closeSetter = new ChannelListener.SimpleSetter();

    private final C framedChannel;
    private final Deque<FrameData> pendingFrameData = new LinkedList<>();

    private int state = 0;

    private static final int STATE_DONE = 1 << 1;
    private static final int STATE_READS_RESUMED = 1 << 2;
    private static final int STATE_READS_AWAKEN = 1 << 3;
    private static final int STATE_CLOSED = 1 << 4;
    private static final int STATE_LAST_FRAME = 1 << 5;
    private static final int STATE_IN_LISTENER_LOOP = 1 << 6;
    private static final int STATE_STREAM_BROKEN = 1 << 7;
    private static final int STATE_RETURNED_MINUS_ONE = 1 << 8;
    private static final int STATE_WAITNG_MINUS_ONE = 1 << 9;

    /**
     * The backing data for the current frame.
     */
    private volatile PooledByteBuffer data;
    private int currentDataOriginalSize;

    /**
     * The amount of data left in the frame. If this is larger than the data in the backing buffer then
     */
    private long frameDataRemaining;

    private final Object lock = new Object();
    // Guarded by lock
    private int waiters;
    private volatile boolean waitingForFrame;
    private int readFrameCount = 0;
    private long maxStreamSize = -1;
    private long currentStreamSize;
    private ChannelListener[] closeListeners = null;

    public AbstractFramedStreamSourceChannel(C framedChannel) {
        this.framedChannel = framedChannel;
        this.waitingForFrame = true;
    }

    public AbstractFramedStreamSourceChannel(C framedChannel, PooledByteBuffer data, long frameDataRemaining) {
        this.framedChannel = framedChannel;
        this.waitingForFrame = data == null && frameDataRemaining <= 0;
        this.frameDataRemaining = frameDataRemaining;
        this.currentStreamSize = frameDataRemaining;
        if (data != null) {
            if (!data.getBuffer().hasRemaining()) {
                data.close();
                this.data = null;
                this.waitingForFrame = frameDataRemaining <= 0;
            } else {
                dataReady(null, data);
            }
        }
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        if (anyAreSet(state, STATE_DONE)) {
            return -1;
        }
        beforeRead();
        if (waitingForFrame) {
            return 0;
        }
        try {
            if (frameDataRemaining == 0 && anyAreSet(state, STATE_LAST_FRAME)) {
                synchronized (lock) {
                    state |= STATE_RETURNED_MINUS_ONE;
                    return -1;
                }
            } else if (data != null) {
                int old = data.getBuffer().limit();
                try {
                    if (count < data.getBuffer().remaining()) {
                        data.getBuffer().limit((int) (data.getBuffer().position() + count));
                    }
                    return target.write(data.getBuffer(), position);
                } finally {
                    data.getBuffer().limit(old);
                    decrementFrameDataRemaining();
                }
            }
            return 0;
        } finally {
            exitRead();
        }
    }

    private void decrementFrameDataRemaining() {
        if(!data.getBuffer().hasRemaining()) {
            frameDataRemaining -= currentDataOriginalSize;
        }
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel streamSinkChannel) throws IOException {
        if (anyAreSet(state, STATE_DONE)) {
            return -1;
        }
        beforeRead();
        if (waitingForFrame) {
            throughBuffer.position(throughBuffer.limit());
            return 0;
        }
        try {
            if (frameDataRemaining == 0 && anyAreSet(state, STATE_LAST_FRAME)) {
                synchronized (lock) {
                    state |= STATE_RETURNED_MINUS_ONE;
                    return -1;
                }
            } else if (data != null && data.getBuffer().hasRemaining()) {
                int old = data.getBuffer().limit();
                try {
                    if (count < data.getBuffer().remaining()) {
                        data.getBuffer().limit((int) (data.getBuffer().position() + count));
                    }
                    int written = streamSinkChannel.write(data.getBuffer());
                    if(data.getBuffer().hasRemaining()) {
                        //we can still add more data
                        //stick it it throughbuffer, otherwise transfer code will continue to attempt to use this method
                        throughBuffer.clear();
                        Buffers.copy(throughBuffer, data.getBuffer());
                        throughBuffer.flip();
                    } else {
                        throughBuffer.position(throughBuffer.limit());
                    }
                    return written;
                } finally {
                    data.getBuffer().limit(old);
                    decrementFrameDataRemaining();
                }
            } else {
                throughBuffer.position(throughBuffer.limit());
            }
            return 0;
        } finally {
            exitRead();
        }
    }

    public long getMaxStreamSize() {
        return maxStreamSize;
    }

    public void setMaxStreamSize(long maxStreamSize) {
        this.maxStreamSize = maxStreamSize;
        if(maxStreamSize > 0) {
            if(maxStreamSize < currentStreamSize) {
                handleStreamTooLarge();
            }
        }
    }

    private void handleStreamTooLarge() {
        IoUtils.safeClose(this);
    }

    @Override
    public void suspendReads() {
        synchronized (lock) {
            state &= ~(STATE_READS_RESUMED | STATE_READS_AWAKEN);
        }
    }

    /**
     * Method that is invoked when all data has been read.
     *
     * @throws IOException
     */
    protected void complete() throws IOException {
        close();
    }

    protected boolean isComplete() {
        return anyAreSet(state, STATE_DONE);
    }

    @Override
    public void resumeReads() {
        resumeReadsInternal(false);
    }

    @Override
    public boolean isReadResumed() {
        return anyAreSet(state, STATE_READS_RESUMED);
    }

    @Override
    public void wakeupReads() {
        resumeReadsInternal(true);
    }

    public void addCloseTask(ChannelListener<R> channelListener) {
        if(closeListeners == null) {
            closeListeners = new ChannelListener[]{channelListener};
        } else {
            ChannelListener[] old = closeListeners;
            closeListeners = new ChannelListener[old.length + 1];
            System.arraycopy(old, 0, closeListeners, 0, old.length);
            closeListeners[old.length] = channelListener;
        }
    }

    /**
     * For this class there is no difference between a resume and a wakeup
     */
    void resumeReadsInternal(boolean wakeup) {
        synchronized (lock) {
            state |= STATE_READS_RESUMED;
            // mark state awaken if wakeup is true
            if (wakeup)
                state |= STATE_READS_AWAKEN;
            // if not waked && not resumed, return
            else if (!anyAreSet(state, STATE_READS_RESUMED))
                return;
            if (!anyAreSet(state, STATE_IN_LISTENER_LOOP)) {
                state |= STATE_IN_LISTENER_LOOP;
                getFramedChannel().runInIoThread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            boolean readAgain;
                            do {
                                ChannelListener<? super R> listener = getReadListener();
                                synchronized(lock) {
                                    state &= ~STATE_READS_AWAKEN;
                                    if (listener == null || !isReadResumed()) {
                                        state &= ~STATE_IN_LISTENER_LOOP;
                                        return;
                                    }
                                }
                                ChannelListeners.invokeChannelListener((R) AbstractFramedStreamSourceChannel.this, listener);
                                //if writes are shutdown or we become active then we stop looping
                                //we stop when writes are shutdown because we can't flush until we are active
                                //although we may be flushed as part of a batch
                                synchronized (lock) {
                                    final boolean moreData = (frameDataRemaining > 0 && data != null) || !pendingFrameData.isEmpty() || anyAreSet(state, STATE_WAITNG_MINUS_ONE);
                                    // keep running if either reads are resumed and there is more data to read, or if reads are awaken
                                    readAgain =((isReadResumed() && moreData) || allAreSet(state, STATE_READS_AWAKEN))
                                               // as long as channel is not closed and there is no stream broken
                                               && allAreClear(state,STATE_CLOSED | STATE_STREAM_BROKEN);
                                    if (!readAgain)
                                        state &= ~STATE_IN_LISTENER_LOOP;
                                }
                            } while (readAgain);
                        } catch (RuntimeException | Error e) {
                            synchronized (lock) {
                                state &= ~STATE_IN_LISTENER_LOOP;
                            }
                        }
                    }
                });
            }
        }
    }

    private ChannelListener<? super R> getReadListener() {
        return (ChannelListener<? super R>) readSetter.get();
    }

    @Override
    public void shutdownReads() throws IOException {
        close();
    }

    protected void lastFrame() {
        synchronized (lock) {
            state |= STATE_LAST_FRAME;
        }
        waitingForFrame = false;
        if(data == null && pendingFrameData.isEmpty() && frameDataRemaining == 0) {
            synchronized (lock) {
                state |= STATE_DONE;
            }
            getFramedChannel().notifyFrameReadComplete(this);
            IoUtils.safeClose(this);
        }
    }

    protected boolean isLastFrame() {
        return anyAreSet(state, STATE_LAST_FRAME);
    }

    @Override
    public void awaitReadable() throws IOException {
        if(Thread.currentThread() == getIoThread()) {
            throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
        }
        if (data == null && pendingFrameData.isEmpty() && !anyAreSet(state, STATE_STREAM_BROKEN | STATE_CLOSED)) {
            synchronized (lock) {
                if (data == null && pendingFrameData.isEmpty() && !anyAreSet(state, STATE_STREAM_BROKEN | STATE_CLOSED)) {
                    try {
                        waiters++;
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    } finally {
                        waiters--;
                    }
                }
            }
        }
    }

    @Override
    public void awaitReadable(long l, TimeUnit timeUnit) throws IOException {
        if(Thread.currentThread() == getIoThread()) {
            throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
        }
        if (data == null && pendingFrameData.isEmpty() && !anyAreSet(state, STATE_STREAM_BROKEN | STATE_CLOSED)) {
            synchronized (lock) {
                if (data == null && pendingFrameData.isEmpty() && !anyAreSet(state, STATE_STREAM_BROKEN | STATE_CLOSED)) {
                    try {
                        waiters++;
                        lock.wait(timeUnit.toMillis(l));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    } finally {
                        waiters--;
                    }
                }
            }
        }
    }

    /**
     * Called when data has been read from the underlying channel.
     *
     * @param headerData The frame header data. This may be null if the data is part of a an existing frame
     * @param frameData  The frame data
     */
    protected void dataReady(FrameHeaderData headerData, PooledByteBuffer frameData) {
        if(anyAreSet(state, STATE_STREAM_BROKEN | STATE_CLOSED)) {
            frameData.close();
            return;
        }
        synchronized (lock) {
            boolean newData = pendingFrameData.isEmpty();
            this.pendingFrameData.add(new FrameData(headerData, frameData));
            if (newData) {
                if (waiters > 0) {
                    lock.notifyAll();
                }
            }
            waitingForFrame = false;
        }
        if (anyAreSet(state, STATE_READS_RESUMED)) {
            resumeReadsInternal(true);
        }
        if(headerData != null) {
            currentStreamSize += headerData.getFrameLength();
            if(maxStreamSize > 0 && currentStreamSize > maxStreamSize) {
                handleStreamTooLarge();
            }
        }
    }

    protected long updateFrameDataRemaining(PooledByteBuffer frameData, long frameDataRemaining) {
        return frameDataRemaining;
    }


    protected PooledByteBuffer processFrameData(PooledByteBuffer data, boolean lastFragmentOfFrame) throws IOException {
        return data;
    }

    protected void handleHeaderData(FrameHeaderData headerData) {

    }

    @Override
    public XnioExecutor getReadThread() {
        return framedChannel.getIoThread();
    }

    @Override
    public ChannelListener.Setter<? extends R> getReadSetter() {
        return readSetter;
    }

    @Override
    public ChannelListener.Setter<? extends R> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return framedChannel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return framedChannel.getIoThread();
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

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (anyAreSet(state, STATE_DONE)) {
            return -1;
        }
        beforeRead();
        if (waitingForFrame) {
            return 0;
        }
        try {
            if (frameDataRemaining == 0 && anyAreSet(state, STATE_LAST_FRAME)) {
                synchronized (lock) {
                    state |= STATE_RETURNED_MINUS_ONE;
                }
                return -1;
            } else if (data != null) {
                int old = data.getBuffer().limit();
                try {
                    long count = Buffers.remaining(dsts, offset, length);
                    if (count < data.getBuffer().remaining()) {
                        data.getBuffer().limit((int) (data.getBuffer().position() + count));
                    } else {
                        count = data.getBuffer().remaining();
                    }
                    return Buffers.copy((int) count, dsts, offset, length, data.getBuffer());
                } finally {
                    data.getBuffer().limit(old);
                    decrementFrameDataRemaining();
                }
            }
            return 0;
        } finally {
            exitRead();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (anyAreSet(state, STATE_DONE)) {
            return -1;
        }
        if (!dst.hasRemaining()) {
            return 0;
        }
        beforeRead();
        if (waitingForFrame) {
            return 0;
        }
        try {
            if (frameDataRemaining == 0 && anyAreSet(state, STATE_LAST_FRAME)) {
                synchronized (lock) {
                    state |= STATE_RETURNED_MINUS_ONE;
                }
                return -1;
            } else if (data != null) {
                int old = data.getBuffer().limit();
                try {
                    int count = dst.remaining();
                    if (count < data.getBuffer().remaining()) {
                        data.getBuffer().limit(data.getBuffer().position() + count);
                    } else {
                        count = data.getBuffer().remaining();
                    }
                    return Buffers.copy(count, dst, data.getBuffer());
                } finally {
                    data.getBuffer().limit(old);
                    decrementFrameDataRemaining();
                }
            }
            return 0;
        } finally {
            try {
                exitRead();
            } catch (Throwable e) {
                markStreamBroken();
            }
        }
    }

    private void beforeRead() throws IOException {
        if (anyAreSet(state, STATE_STREAM_BROKEN)) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        if (data == null) {
            synchronized (lock) {
                FrameData pending = pendingFrameData.poll();
                if (pending != null) {
                    PooledByteBuffer frameData = pending.getFrameData();
                    boolean hasData = true;
                    if(!frameData.getBuffer().hasRemaining()) {
                        frameData.close();
                        hasData = false;
                    }
                    if (pending.getFrameHeaderData() != null) {
                        this.frameDataRemaining = pending.getFrameHeaderData().getFrameLength();
                        handleHeaderData(pending.getFrameHeaderData());
                    }
                    if(hasData) {
                        this.frameDataRemaining = updateFrameDataRemaining(frameData, frameDataRemaining);
                        this.currentDataOriginalSize = frameData.getBuffer().remaining();
                        try {
                            this.data = processFrameData(frameData, frameDataRemaining - currentDataOriginalSize == 0);
                        } catch (Throwable e) {
                            frameData.close();
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(e));
                            markStreamBroken();
                        }
                    }
                }
            }
        }
    }

    private void exitRead() throws IOException {
        if (data != null && !data.getBuffer().hasRemaining()) {
            data.close();
            data = null;
        }
        if (frameDataRemaining == 0) {
            try {
                synchronized (lock) {
                    readFrameCount++;
                    if (pendingFrameData.isEmpty()) {
                        if (anyAreSet(state, STATE_RETURNED_MINUS_ONE)) {
                            state |= STATE_DONE;
                            complete();
                            close();
                        } else if(anyAreSet(state, STATE_LAST_FRAME)) {
                            state |= STATE_WAITNG_MINUS_ONE;
                        } else {
                            waitingForFrame = true;
                        }
                    }
                }
            } finally {
                if (pendingFrameData.isEmpty()) {
                    framedChannel.notifyFrameReadComplete(this);
                }
            }
        }
    }

    @Override
    public boolean isOpen() {
        return allAreClear(state, STATE_CLOSED);
    }

    @Override
    public void close() {
        if(anyAreSet(state, STATE_CLOSED)) {
            return;
        }
        synchronized (lock) {
            // Double check to avoid executing the the rest of this method multiple times
            if(anyAreSet(state, STATE_CLOSED)) {
                return;
            }
            state |= STATE_CLOSED;
            if (allAreClear(state, STATE_DONE | STATE_LAST_FRAME)) {
                state |= STATE_STREAM_BROKEN;
                channelForciblyClosed();
            }
            if (data != null) {
                data.close();
                data = null;
            }
            while (!pendingFrameData.isEmpty()) {
                pendingFrameData.poll().frameData.close();
            }

            ChannelListeners.invokeChannelListener(this, (ChannelListener<? super AbstractFramedStreamSourceChannel<C, R, S>>) closeSetter.get());
            if (closeListeners != null) {
                for (int i = 0; i < closeListeners.length; ++i) {
                    closeListeners[i].handleEvent(this);
                }
            }
            // UNDERTOW-1639: Close may be called from an I/O thread while a worker is blocked on awaitReadable.
            // Once the channel is closed, callers must be awoken.
            if (waiters > 0) {
                lock.notifyAll();
            }
        }
    }

    protected void channelForciblyClosed() {
        //TODO: what should be the default action?
        //we can probably just ignore it, as it does not affect the underlying protocol
    }

    protected C getFramedChannel() {
        return framedChannel;
    }

    protected int getReadFrameCount() {
        return readFrameCount;
    }

    /**
     * Called when this stream is no longer valid. Reads from the stream will result
     * in an exception.
     */
    protected void markStreamBroken() {
        if(anyAreSet(state, STATE_STREAM_BROKEN)) {
            return;
        }
        synchronized (lock) {
            state |= STATE_STREAM_BROKEN;
            PooledByteBuffer data = this.data;
            if(data != null) {
                try {
                    data.close(); //may have been closed by the read thread
                } catch (Throwable e) {
                    //ignore
                }
                this.data = null;
            }
            for(FrameData frame : pendingFrameData) {
                frame.frameData.close();
            }
            pendingFrameData.clear();
            if(isReadResumed()) {
                resumeReadsInternal(true);
            }
            if (waiters > 0) {
                lock.notifyAll();
            }
        }
    }

    private class FrameData {

        private final FrameHeaderData frameHeaderData;
        private final PooledByteBuffer frameData;

        FrameData(FrameHeaderData frameHeaderData, PooledByteBuffer frameData) {
            this.frameHeaderData = frameHeaderData;
            this.frameData = frameData;
        }

        FrameHeaderData getFrameHeaderData() {
            return frameHeaderData;
        }

        PooledByteBuffer getFrameData() {
            return frameData;
        }
    }

}
