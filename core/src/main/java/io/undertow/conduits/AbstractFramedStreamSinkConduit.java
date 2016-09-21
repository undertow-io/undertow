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

package io.undertow.conduits;

import io.undertow.UndertowMessages;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Utility class to ease the implementation of framed protocols. This call provides a queue of frames, and a callback
 * that can be invoked when a frame event occurs.
 * <p>
 * When a write takes place all frames are attempted to be written out at once via a gathering write. Frames can be
 * queued via {@link #queueFrame(io.undertow.conduits.AbstractFramedStreamSinkConduit.FrameCallBack, java.nio.ByteBuffer...)}.
 *
 * @author Stuart Douglas
 */
public class AbstractFramedStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final Deque<Frame> frameQueue = new ArrayDeque<>();
    /**
     * The total amount of data that has been queued to be written out
     */
    private long queuedData = 0;
    /**
     * The total number of buffers that have been queued to be written out
     */
    private int bufferCount = 0;

    private int state;

    private static final int FLAG_WRITES_TERMINATED = 1;
    private static final int FLAG_DELEGATE_SHUTDOWN = 2;

    /**
     * Construct a new instance.
     *
     * @param next the delegate conduit to set
     */
    protected AbstractFramedStreamSinkConduit(StreamSinkConduit next) {
        super(next);
    }

    /**
     * Queues a frame for sending.
     *
     * @param callback
     * @param data
     */
    protected void queueFrame(FrameCallBack callback, ByteBuffer... data) {
        queuedData += Buffers.remaining(data);
        bufferCount += data.length;
        frameQueue.add(new Frame(callback, data, 0, data.length));
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_TERMINATED)) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return (int) doWrite(new ByteBuffer[]{src}, 0, 1);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        if (anyAreSet(state, FLAG_WRITES_TERMINATED)) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return doWrite(srcs, offs, len);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offs, int len) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offs, len);
    }


    private long doWrite(ByteBuffer[] additionalData, int offs, int len) throws IOException {
        ByteBuffer[] buffers = new ByteBuffer[bufferCount + (additionalData == null ? 0 : len)];
        int count = 0;
        for (Frame frame : frameQueue) {
            for (int i = frame.offs; i < frame.offs + frame.len; ++i) {
                buffers[count++] = frame.data[i];
            }
        }

        if (additionalData != null) {
            for (int i = offs; i < offs + len; ++i) {
                buffers[count++] = additionalData[i];
            }
        }
        try {
            long written = next.write(buffers, 0, buffers.length);
            if (written > this.queuedData) {
                this.queuedData = 0;
            } else {
                this.queuedData -= written;
            }
            long toAllocate = written;
            Frame frame = frameQueue.peek();
            while (frame != null) {
                if (frame.remaining > toAllocate) {
                    frame.remaining -= toAllocate;
                    return 0;
                } else {
                    frameQueue.poll(); //this frame is done, remove it
                    //note that after we start calling done() we can't re-use the buffers[] array
                    //as pooled buffers may have been returned to the pool and re-used
                    FrameCallBack cb = frame.callback;
                    if (cb != null) {
                        cb.done();
                    }
                    bufferCount -= frame.len;
                    toAllocate -= frame.remaining;
                }
                frame = frameQueue.peek();
            }
            return toAllocate;

        } catch (IOException e) {
            //on exception we fail every item in the frame queue
            try {
                for (Frame frame : frameQueue) {
                    FrameCallBack cb = frame.callback;
                    if (cb != null) {
                        cb.failed(e);
                    }
                }
                frameQueue.clear();
                bufferCount = 0;
                queuedData = 0;
            } finally {
                throw e;
            }
        }
    }

    protected long queuedDataLength() {
        return queuedData;
    }


    @Override
    public void terminateWrites() throws IOException {
        if (anyAreSet(state, FLAG_WRITES_TERMINATED)) {
            return;
        }
        queueCloseFrames();
        state |= FLAG_WRITES_TERMINATED;
        if (queuedData == 0) {
            state |= FLAG_DELEGATE_SHUTDOWN;
            doTerminateWrites();
            finished();
        }
    }

    protected void doTerminateWrites() throws IOException {
        next.terminateWrites();
    }

    protected boolean flushQueuedData() throws IOException {
        if (queuedData > 0) {
            doWrite(null, 0, 0);
        }
        if (queuedData > 0) {
            return false;
        }
        if (anyAreSet(state, FLAG_WRITES_TERMINATED) && allAreClear(state, FLAG_DELEGATE_SHUTDOWN)) {
            doTerminateWrites();
            state |= FLAG_DELEGATE_SHUTDOWN;
            finished();
        }
        return next.flush();
    }

    @Override
    public void truncateWrites() throws IOException {
        for (Frame frame : frameQueue) {
            FrameCallBack cb = frame.callback;
            if (cb != null) {
                cb.failed(UndertowMessages.MESSAGES.channelIsClosed());
            }
        }
    }

    protected boolean isWritesTerminated() {
        return anyAreSet(state, FLAG_WRITES_TERMINATED);
    }

    protected void queueCloseFrames() {

    }

    protected void finished() {

    }

    /**
     * Interface that is called when a frame event takes place. The events are:
     * <p>
     * <ul>
     * <li>
     * Done - The fame has been written out
     * </li>
     * <li>
     * Failed - The frame write failed
     * </li>
     * </ul>
     */
    public interface FrameCallBack {

        void done();

        void failed(final IOException e);

    }

    private static class Frame {

        final FrameCallBack callback;
        final ByteBuffer[] data;
        final int offs;
        final int len;
        long remaining;

        private Frame(FrameCallBack callback, ByteBuffer[] data, int offs, int len) {
            this.callback = callback;
            this.data = data;
            this.offs = offs;
            this.len = len;
            this.remaining = Buffers.remaining(data, offs, len);
        }
    }

    protected static class PooledBufferFrameCallback implements FrameCallBack {

        private final PooledByteBuffer buffer;

        public PooledBufferFrameCallback(PooledByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void done() {
            buffer.close();
        }

        @Override
        public void failed(IOException e) {
            buffer.close();
        }
    }


    protected static class PooledBuffersFrameCallback implements FrameCallBack {

        private final PooledByteBuffer[] buffers;

        public PooledBuffersFrameCallback(PooledByteBuffer... buffers) {
            this.buffers = buffers;
        }

        @Override
        public void done() {
            for (PooledByteBuffer buffer : buffers) {
                buffer.close();
            }
        }

        @Override
        public void failed(IOException e) {
            done();
        }
    }
}
