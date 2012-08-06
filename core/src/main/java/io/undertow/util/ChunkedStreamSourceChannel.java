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

package io.undertow.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.ConcurrentStreamChannelAccessException;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * Channel to de-chunkify data
 *
 * @author Stuart Douglas
 */
public class ChunkedStreamSourceChannel implements StreamSourceChannel {
    private final PushBackStreamChannel delegate;
    private final boolean configurable;
    private final Pool<ByteBuffer> bufferPool;


    private final ChannelListener<? super ChunkedStreamSourceChannel> finishListener;
    private final ChannelListener.SimpleSetter<ChunkedStreamSourceChannel> readSetter = new ChannelListener.SimpleSetter<ChunkedStreamSourceChannel>();
    private final ChannelListener.SimpleSetter<ChunkedStreamSourceChannel> closeSetter = new ChannelListener.SimpleSetter<ChunkedStreamSourceChannel>();

    @SuppressWarnings("unused")
    private volatile long state;

    private static final long FLAG_READ_ENTERED = 1L << 63L;
    private static final long FLAG_CLOSED = 1L << 62L;
    private static final long FLAG_SUS_RES_SHUT = 1L << 61L;
    private static final long FLAG_FINISHED = 1L << 60L;
    private static final long FLAG_READING_LENGTH = 1L << 59L;
    private static final long FLAG_READING_TILL_END_OF_LINE = 1L << 58L;
    private static final long FLAG_READING_NEWLINE = 1L << 57L;
    private static final long MASK_COUNT = longBitMask(0, 56);

    private static final AtomicLongFieldUpdater<ChunkedStreamSourceChannel> stateUpdater = AtomicLongFieldUpdater.newUpdater(ChunkedStreamSourceChannel.class, "state");

    public ChunkedStreamSourceChannel(final PushBackStreamChannel delegate, final ChannelListener<? super ChunkedStreamSourceChannel> finishListener, final Pool<ByteBuffer> bufferPool) {
        this(delegate, false, bufferPool, finishListener);
    }

    public ChunkedStreamSourceChannel(final PushBackStreamChannel delegate, final boolean configurable, final Pool<ByteBuffer> bufferPool, final ChannelListener<? super ChunkedStreamSourceChannel> finishListener) {
        this.bufferPool = bufferPool;
        this.finishListener = finishListener;
        this.delegate = delegate;
        delegate.getReadSetter().set(ChannelListeners.delegatingChannelListener(ChunkedStreamSourceChannel.this, readSetter));
        this.configurable = configurable;
        stateUpdater.set(this, FLAG_READING_LENGTH);
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        //TODO: this is not non blocking, is that an issue?
        if (count <= 0) {
            return 0;
        }
        final Pooled<ByteBuffer> buffer = bufferPool.allocate();
        final ByteBuffer buf = buffer.getResource();
        buf.clear();
        buf.limit(Math.max((int) count, buf.limit()));
        long pos = position;
        try {
            int read = read(buf);
            buf.flip();
            int c = 0;
            do {
                c = target.write(buf, pos);
                pos += c;
            } while (buf.hasRemaining());
            return read;
        } finally {
            buffer.free();
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        throw new RuntimeException("Not implemented yet");
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        for (int i = offset; i < length; ++i) {
            if (dsts[i].hasRemaining()) {
                return read(dsts[i]);
            }
        }
        return 0;
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public int read(final ByteBuffer dst) throws IOException {
        final long oldVal = enterRead();
        long newVal = oldVal;
        if (anyAreSet(newVal, FLAG_FINISHED)) {
            return -1;
        }
        if (anyAreClear(newVal, FLAG_CLOSED)) {
            throw new ClosedChannelException();
        }
        int read = 0;
        long chunkRemaining = newVal & MASK_COUNT;
        Pooled<ByteBuffer> buffer = null;
        ByteBuffer buf = null;
        //we have read the last chunk, we just return EOF

        final int originalLimit = dst.limit();
        try {
            if (allAreClear(newVal, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE) && chunkRemaining == 0) {
                newVal |= FLAG_FINISHED;
                return -1;
            }
            for (; ; ) {
                while (anyAreSet(newVal, FLAG_READING_NEWLINE)) {
                    if (buffer == null) {
                        buffer = bufferPool.allocate();
                        buf = buffer.getResource();
                        buf.clear();
                        //we need to make sure we do not read more than can fit in the user supplied buffer
                        buf.limit(Math.min(dst.limit() - dst.position(), buf.capacity()));
                    } else {
                        buf.compact();
                    }
                    int c = delegate.read(buf);
                    buf.flip();
                    if (c == -1) {
                        newVal |= FLAG_FINISHED;
                        return read;
                    } else if (c == 0 && !buf.hasRemaining()) {
                        return read;
                    }
                    while (buf.hasRemaining()) {
                        byte b = buf.get();
                        if (b == '\n') {
                            newVal = newVal & ~FLAG_READING_NEWLINE | FLAG_READING_LENGTH;
                            break;
                        }
                    }
                }

                while (anyAreSet(newVal, FLAG_READING_LENGTH)) {
                    if (buffer == null) {
                        buffer = bufferPool.allocate();
                        buf = buffer.getResource();
                        buf.clear();
                        //we need to make sure we do not read more than can fit in the user supplied buffer
                        buf.limit(Math.min(dst.limit() - dst.position(), buf.capacity()));
                    } else {
                        buf.compact();
                    }
                    int c = delegate.read(buf);
                    buf.flip();
                    if (c == -1) {
                        newVal |= FLAG_FINISHED;
                        return read;
                    } else if (c == 0 && !buf.hasRemaining()) {
                        return read;
                    }
                    while (buf.hasRemaining()) {
                        byte b = buf.get();
                        if ((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b < 'F')) {
                            chunkRemaining <<= 4; //shift it 4 bytes and then add the next value to the end
                            chunkRemaining += Integer.parseInt("" + (char) b, 16);
                        } else {
                            newVal = newVal & ~FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE;
                            break;
                        }
                    }
                }
                while (anyAreSet(newVal, FLAG_READING_TILL_END_OF_LINE)) {
                    if (buffer == null) {
                        buffer = bufferPool.allocate();
                        buf = buffer.getResource();
                        buf.clear();
                        //we need to make sure we do not read more than can fit in the user supplied buffer
                        buf.limit(Math.min(dst.limit() - dst.position(), buf.capacity()));
                    }
                    while (buf.hasRemaining()) {
                        if (buffer.getResource().get() == '\n') {
                            newVal = newVal & ~FLAG_READING_TILL_END_OF_LINE;
                            break;
                        }
                    }
                    if (anyAreSet(newVal, FLAG_READING_TILL_END_OF_LINE)) {
                        int c = delegate.read(buf);
                        buf.flip();
                        if (c == -1) {
                            newVal |= FLAG_FINISHED;
                            return read;
                        } else if (c == 0) {
                            return read;
                        }
                    }
                }
                //we have our chunk size, check to make sure it was not the last chunk
                if (allAreClear(newVal, FLAG_READING_NEWLINE | FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE) && chunkRemaining == 0) {
                    newVal |= FLAG_FINISHED;
                    //we may have read to far
                    if (buf.hasRemaining()) {
                        delegate.unget(buffer);
                        buffer = null;
                    }
                    return read;
                }
                //is we have already read some stuff we need to transfer it to the destination buffer
                //we know it will all fit as we sized our buffer appropriately
                int remaining = buf.limit() - buf.position();
                int old = buf.limit();
                buf.limit((int) Math.min(old, buf.position() + chunkRemaining));
                dst.put(buf);
                buf.limit(old);
                dst.put(buf.get());
                long readBytes = Math.min(chunkRemaining, remaining);
                read += (int) readBytes;
                chunkRemaining -= readBytes;

                //if there is still data remaining then we need to do this whole thing again
                if (buf.hasRemaining()) {
                    newVal |= FLAG_READING_NEWLINE;
                    continue;
                }

                //resize the dest buffer if needed so it does not exceed the chunk size
                int remainingBufferSize = dst.limit() - dst.position();
                if (chunkRemaining < remainingBufferSize) {
                    dst.limit((int) (dst.position() + chunkRemaining));
                }
                int c;
                do {
                    c = delegate.read(dst);
                    if (c > 0) {
                        read += c;
                        chunkRemaining -= c;
                    }
                } while (c > 0);
                if (c == -1) {
                    newVal |= FLAG_FINISHED;
                }
                if (chunkRemaining == 0) {
                    newVal |= FLAG_READING_NEWLINE;
                }
                return read;
            }

        } finally {
            dst.limit(originalLimit);
            if (buffer != null) {
                buffer.free();
            }
            exitRead(oldVal, chunkRemaining, newVal & (FLAG_FINISHED | FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE), ~newVal & (FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE));
        }
    }

    public void suspendReads() {
        long val = enterSuspendResume();
        if (anyAreSet(val, FLAG_CLOSED | FLAG_SUS_RES_SHUT)) {
            return;
        }
        try {
            delegate.suspendReads();
        } finally {
            exitSuspendResume(val);
        }
    }

    public void resumeReads() {
        long val = enterSuspendResume();
        if (anyAreSet(val, FLAG_CLOSED | FLAG_SUS_RES_SHUT)) {
            return;
        }
        try {
            if (val == 0L) {
                delegate.wakeupReads();
            } else {
                delegate.resumeReads();
            }
        } finally {
            exitSuspendResume(val);
        }
    }

    public boolean isReadResumed() {
        return allAreClear(state, FLAG_CLOSED) && delegate.isReadResumed();
    }

    public void wakeupReads() {
        long val = enterSuspendResume();
        if (anyAreSet(val, FLAG_CLOSED | FLAG_SUS_RES_SHUT)) {
            return;
        }
        try {
            delegate.wakeupReads();
        } finally {
            exitSuspendResume(val);
        }
    }

    public void shutdownReads() throws IOException {
        long val = enterShutdownReads();
        if (allAreSet(val, FLAG_CLOSED)) {
            return;
        }
        try {
            if (false) {
                // propagate close if configured to do so
                delegate.shutdownReads();
            }
        } finally {
            // listener(s) called from here
            exitShutdownReads(val);
        }
    }

    public void awaitReadable() throws IOException {
        final long val = state;
        if (allAreSet(val, FLAG_CLOSED) || val == 0L) {
            return;
        }
        delegate.awaitReadable();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        final long val = state;
        if (allAreSet(val, FLAG_CLOSED) || val == 0L) {
            return;
        }
        delegate.awaitReadable(time, timeUnit);
    }

    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSED);
    }

    public void close() throws IOException {
        shutdownReads();
    }

    public boolean supportsOption(final Option<?> option) {
        return configurable && delegate.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return configurable ? delegate.getOption(option) : null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return configurable ? delegate.setOption(option, value) : null;
    }

    public StreamSourceChannel getChannel() {
        return delegate;
    }

    /**
     * Get the number of remaining bytes.
     *
     * @return the number of remaining bytes
     */
    public long getRemaining() {
        return state & MASK_COUNT;
    }

    private long enterShutdownReads() {
        long oldVal, newVal;
        do {
            oldVal = state;
            if (anyAreSet(oldVal, FLAG_CLOSED)) {
                return oldVal;
            }
            newVal = oldVal | FLAG_CLOSED | FLAG_SUS_RES_SHUT;
        } while (!stateUpdater.weakCompareAndSet(this, oldVal, newVal));
        return oldVal;
    }

    private void exitShutdownReads(long oldVal) {
        final boolean wasFinished = allAreSet(oldVal, FLAG_FINISHED);
        final boolean wasInSusRes = allAreSet(oldVal, FLAG_SUS_RES_SHUT);
        final boolean wasEntered = allAreSet(oldVal, FLAG_READ_ENTERED);
        if (!wasInSusRes) {
            long newVal = oldVal & ~FLAG_SUS_RES_SHUT;
            while (!stateUpdater.compareAndSet(this, oldVal, newVal)) {
                oldVal = state;
                newVal = oldVal & ~FLAG_SUS_RES_SHUT;
            }
            if (!wasEntered) {
                if (!wasFinished && allAreClear(newVal, MASK_COUNT)) {
                    callFinish();
                }
                callClosed();
            }
        }
        // else let exitSuspendResume/exitReads handle this
    }

    private long enterSuspendResume() {
        long oldVal, newVal;
        do {
            oldVal = state;
            if (anyAreSet(oldVal, FLAG_CLOSED | FLAG_SUS_RES_SHUT)) {
                return oldVal;
            }
            newVal = oldVal | FLAG_SUS_RES_SHUT;
        } while (!stateUpdater.weakCompareAndSet(this, oldVal, newVal));
        return oldVal;
    }

    private void exitSuspendResume(long oldVal) {
        final boolean wasFinished = allAreSet(oldVal, FLAG_FINISHED);
        final boolean wasClosed = allAreClear(oldVal, FLAG_CLOSED);
        final boolean wasEntered = allAreSet(oldVal, FLAG_READ_ENTERED);
        long newVal = oldVal & ~FLAG_SUS_RES_SHUT;
        while (!stateUpdater.compareAndSet(this, oldVal, newVal)) {
            oldVal = state;
            newVal = oldVal & ~FLAG_SUS_RES_SHUT;
        }
        if (!wasEntered) {
            if (!wasFinished && allAreClear(newVal, MASK_COUNT)) {
                callFinish();
            }
            if (!wasClosed && allAreSet(newVal, FLAG_CLOSED)) {
                callClosed();
            }
        }
    }

    /**
     * Enter the method.  Does not set entered flag if the channel is closed so
     * the caller must return immediately in this case.
     *
     * @return the original state
     */
    private long enterRead() {
        long oldVal, newVal;
        do {
            oldVal = state;
            if (allAreSet(oldVal, FLAG_CLOSED) || allAreSet(oldVal, FLAG_FINISHED)) {
                // do not swap
                return oldVal;
            }
            if (allAreSet(oldVal, FLAG_READ_ENTERED)) {
                throw new ConcurrentStreamChannelAccessException();
            }
            newVal = oldVal | FLAG_READ_ENTERED;
        } while (!stateUpdater.weakCompareAndSet(this, oldVal, newVal));
        return oldVal;
    }

    /**
     * Exit a read method.
     *
     * @param oldVal the original state
     */
    private void exitRead(long oldVal, long chunkSize, long setFlags, long clearFlags) {
        long newVal = oldVal | setFlags & ~clearFlags & (chunkSize | ~MASK_COUNT);
        while (!stateUpdater.compareAndSet(this, oldVal, newVal)) {
            oldVal = state;
            newVal = oldVal | setFlags & ~clearFlags & chunkSize;
        }
        if (allAreSet(newVal, FLAG_CLOSED)) {
            // closed while we were in flight.  Call the listener.
            callClosed();
        }
        if (allAreClear(oldVal, FLAG_FINISHED) && allAreSet(newVal, FLAG_FINISHED)) {
            callFinish();
        }
    }

    private void callFinish() {
        ChannelListeners.invokeChannelListener(this, finishListener);
    }

    private void callClosed() {
        ChannelListeners.invokeChannelListener(this, closeSetter.get());
    }
}
