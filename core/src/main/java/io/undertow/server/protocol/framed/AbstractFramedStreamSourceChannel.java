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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pooled;
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
 */
public abstract class AbstractFramedStreamSourceChannel<C extends AbstractFramedChannel<C, R, S>, R extends AbstractFramedStreamSourceChannel<C, R, S>, S extends AbstractFramedStreamSinkChannel<C, R, S>> implements StreamSourceChannel {

    private final ChannelListener.SimpleSetter<? extends R> readSetter = new ChannelListener.SimpleSetter();
    private final ChannelListener.SimpleSetter<? extends R> closeSetter = new ChannelListener.SimpleSetter();

    private final C framedChannel;
    private final Deque<FrameData> pendingFrameData = new LinkedList<>();

    private int state = 0;

    private static final int STATE_DONE = 1 << 1;
    private static final int STATE_READS_RESUMED = 1 << 2;
    private static final int STATE_CLOSED = 1 << 3;
    private static final int STATE_LAST_FRAME = 1 << 4;
    private static final int STATE_IN_LISTENER_LOOP = 1 << 5;
    private static final int STATE_STREAM_BROKEN = 1 << 6;


    /**
     * The backing data for the current frame.
     */
    private Pooled<ByteBuffer> data;

    /**
     * The amount of data left in the frame. If this is larger than the data in the backing buffer then
     */
    private long frameDataRemaining;

    private final Object lock = new Object();
    private int waiters;
    private volatile boolean waitingForFrame;
    private int readFrameCount = 0;
    private long maxStreamSize = -1;
    private long currentStreamSize;

    public AbstractFramedStreamSourceChannel(C framedChannel) {
        this.framedChannel = framedChannel;
        this.waitingForFrame = true;
    }

    public AbstractFramedStreamSourceChannel(C framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining) {
        this.framedChannel = framedChannel;
        this.waitingForFrame = data == null && frameDataRemaining <= 0;
        this.data = data;
        this.frameDataRemaining = frameDataRemaining;
        this.currentStreamSize = frameDataRemaining;
        if (data != null) {
            if (!data.getResource().hasRemaining()) {
                data.free();
                this.data = null;
                this.waitingForFrame = frameDataRemaining <= 0;
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
                return -1;
            } else if (data != null) {
                int old = data.getResource().limit();
                try {
                    if (count < data.getResource().remaining()) {
                        data.getResource().limit((int) (data.getResource().position() + count));
                    }
                    int written = target.write(data.getResource(), position);
                    frameDataRemaining -= written;
                    return written;
                } finally {
                    data.getResource().limit(old);
                }
            }
            return 0;
        } finally {
            exitRead();
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
                return -1;
            } else if (data != null && data.getResource().hasRemaining()) {
                int old = data.getResource().limit();
                try {
                    if (count < data.getResource().remaining()) {
                        data.getResource().limit((int) (data.getResource().position() + count));
                    }
                    int written = streamSinkChannel.write(data.getResource());
                    frameDataRemaining -= written;
                    if(data.getResource().hasRemaining()) {
                        //we can still add more data
                        //stick it it throughbuffer, otherwise transfer code will continue to attempt to use this method
                        throughBuffer.clear();
                        frameDataRemaining -= Buffers.copy(throughBuffer, data.getResource());
                        throughBuffer.flip();
                    } else {
                        throughBuffer.position(throughBuffer.limit());
                    }
                    return written;
                } finally {
                    data.getResource().limit(old);
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
        state &= ~STATE_READS_RESUMED;
    }

    /**
     * Method that is invoked when all data has been read.
     *
     * @throws IOException
     */
    protected void complete() throws IOException {

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

    /**
     * For this class there is no difference between a resume and a wakeup
     */
    void resumeReadsInternal(boolean wakeup) {
        boolean alreadyResumed = anyAreSet(state, STATE_READS_RESUMED);
        state |= STATE_READS_RESUMED;
        if(!alreadyResumed || wakeup) {
            if (!anyAreSet(state, STATE_IN_LISTENER_LOOP)) {
                state |= STATE_IN_LISTENER_LOOP;
                getIoThread().execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            boolean moreData;
                            do {
                                ChannelListener<? super R> listener = getReadListener();
                                if (listener == null || !isReadResumed()) {
                                    return;
                                }
                                ChannelListeners.invokeChannelListener((R) AbstractFramedStreamSourceChannel.this, listener);
                                //if writes are shutdown or we become active then we stop looping
                                //we stop when writes are shutdown because we can't flush until we are active
                                //although we may be flushed as part of a batch
                                moreData = (frameDataRemaining > 0 &&  data != null) || !pendingFrameData.isEmpty();
                            } while (allAreSet(state, STATE_READS_RESUMED) && allAreClear(state, STATE_CLOSED) && moreData);
                        } finally {
                            state &= ~STATE_IN_LISTENER_LOOP;
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
        state |= STATE_LAST_FRAME;
        waitingForFrame = false;
        if(data == null && pendingFrameData.isEmpty() && frameDataRemaining == 0) {
            state |= STATE_DONE | STATE_CLOSED;
            getFramedChannel().notifyFrameReadComplete(this);
            getFramedChannel().notifyClosed(this);
        }
    }

    @Override
    public void awaitReadable() throws IOException {
        if(Thread.currentThread() == getIoThread()) {
            throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
        }
        if (data == null && pendingFrameData.isEmpty()) {
            synchronized (lock) {
                if (data == null && pendingFrameData.isEmpty()) {
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
        if (data == null) {
            synchronized (lock) {
                if (data == null) {
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
    void dataReady(FrameHeaderData headerData, Pooled<ByteBuffer> frameData) {
        if(anyAreSet(state, STATE_STREAM_BROKEN)) {
            frameData.free();
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

    protected long handleFrameData(Pooled<ByteBuffer> frameData, long frameDataRemaining) {
        return frameDataRemaining;
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
                return -1;
            } else if (data != null) {
                int old = data.getResource().limit();
                try {
                    long count = Buffers.remaining(dsts, offset, length);
                    if (count < data.getResource().remaining()) {
                        data.getResource().limit((int) (data.getResource().position() + count));
                    } else {
                        count = data.getResource().remaining();
                    }
                    int written = Buffers.copy((int) count, dsts, offset, length, data.getResource());
                    frameDataRemaining -= written;
                    return written;
                } finally {
                    data.getResource().limit(old);
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
                return -1;
            } else if (data != null) {
                int old = data.getResource().limit();
                try {
                    int count = dst.remaining();
                    if (count < data.getResource().remaining()) {
                        data.getResource().limit(data.getResource().position() + count);
                    } else {
                        count = data.getResource().remaining();
                    }
                    int written = Buffers.copy(count, dst, data.getResource());
                    frameDataRemaining -= written;
                    return written;
                } finally {
                    data.getResource().limit(old);
                }
            }
            return 0;
        } finally {
            exitRead();
        }
    }

    private void beforeRead() throws ClosedChannelException {
        if (anyAreSet(state, STATE_STREAM_BROKEN)) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        if (data == null) {
            synchronized (lock) {
                FrameData pending = pendingFrameData.poll();
                if (pending != null) {
                    Pooled<ByteBuffer> frameData = pending.getFrameData();
                    boolean hasData = true;
                    if(frameData.getResource().hasRemaining()) {
                        this.data = frameData;
                    } else {
                        frameData.free();
                        hasData = false;
                    }
                    if (pending.getFrameHeaderData() != null) {
                        this.frameDataRemaining = pending.getFrameHeaderData().getFrameLength();
                        handleHeaderData(pending.getFrameHeaderData());
                    }
                    if(hasData) {
                        this.frameDataRemaining = handleFrameData(frameData, frameDataRemaining);
                    }
                }
            }
        }
    }

    private void exitRead() throws IOException {
        if (data != null && !data.getResource().hasRemaining()) {
            data.free();
            data = null;
        }
        if (frameDataRemaining == 0) {
            try {
                synchronized (lock) {
                    readFrameCount++;
                    if (pendingFrameData.isEmpty()) {
                        if (anyAreSet(state, STATE_LAST_FRAME)) {
                            state |= STATE_DONE;
                            getFramedChannel().notifyClosed(this);
                            complete();
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
    public void close() throws IOException {
        if(anyAreSet(state, STATE_CLOSED)) {
            return;
        }
        state |= STATE_CLOSED;
        if (allAreClear(state, STATE_DONE | STATE_LAST_FRAME)) {
            state |= STATE_STREAM_BROKEN;
            getFramedChannel().notifyClosed(this);
            channelForciblyClosed();
        }
        if (data != null) {
            data.free();
            data = null;
        }
        while (!pendingFrameData.isEmpty()) {
            pendingFrameData.poll().frameData.free();
        }
        ChannelListeners.invokeChannelListener(this, (ChannelListener<? super AbstractFramedStreamSourceChannel<C, R, S>>) closeSetter.get());
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
    protected synchronized void markStreamBroken() {
        state |= STATE_STREAM_BROKEN;
        if(data != null) {
            data.free();
            data = null;
        }
        for(FrameData frame : pendingFrameData) {
            frame.frameData.free();
        }
        pendingFrameData.clear();
        if(isReadResumed()) {
            resumeReadsInternal(true);
        }
        if (waiters > 0) {
            lock.notifyAll();
        }
    }

    private class FrameData {

        private final FrameHeaderData frameHeaderData;
        private final Pooled<ByteBuffer> frameData;

        FrameData(FrameHeaderData frameHeaderData, Pooled<ByteBuffer> frameData) {
            this.frameHeaderData = frameHeaderData;
            this.frameData = frameData;
        }

        FrameHeaderData getFrameHeaderData() {
            return frameHeaderData;
        }

        Pooled<ByteBuffer> getFrameData() {
            return frameData;
        }
    }

}
