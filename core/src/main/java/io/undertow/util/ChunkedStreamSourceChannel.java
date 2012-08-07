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

    //byte buffer for raw unchunked data that has been read from the channel
    private volatile Pooled<ByteBuffer> rawData;


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
        final long oldVal = enterRead();
        //we have read the last chunk, we just return EOF
        if (anyAreSet(oldVal, FLAG_FINISHED)) {
            return -1;
        }
        if (anyAreClear(oldVal, FLAG_CLOSED)) {
            throw new ClosedChannelException();
        }
        long newVal;

        if (anyAreSet(oldVal, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE | FLAG_READING_NEWLINE | FLAG_FINISHED)) {
            //we are in the process of reading chunking overhead
            newVal = readRawData(oldVal);
        } else {
            assert (oldVal & MASK_COUNT) != 0;
            //otherwise we still have some raw data we can read, either from the buffer
            //or directly form the underlying stream
            //note that chunkRemaining will never be zero here
            newVal = oldVal;
        }
        long chunkRemaining = newVal & MASK_COUNT;
        try {
            long pos = position;
            long remaining = count;
            if (anyAreSet(newVal, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE | FLAG_READING_NEWLINE | FLAG_FINISHED)) {
                //we did not manage to read anything except chunking overhead
                return 0;
            }
            //now we may have some stuff in the raw buffer
            //or the raw buffer may be exhausted, and we should read directly into the destination buffer
            //from the delegate

            int read = 0;
            final Pooled<ByteBuffer> buffer = rawData;
            if (buffer != null) {
                final ByteBuffer buf = buffer.getResource();
                if (buf.remaining() > count) {
                    //it won't fit
                    int orig = buf.limit();
                    buf.limit((int) (buf.position() + count));
                    int written = 0;
                    long c;
                    do {
                        c = target.write(buf, pos);
                        written += c;
                        pos += c;
                    } while (buf.hasRemaining() && c > 0);
                    buf.limit(orig);
                    chunkRemaining -= written;
                    return written;
                } else if (buf.hasRemaining()) {
                    int written = 0;
                    long c;
                    do {
                        c = target.write(buf, pos);
                        written += c;
                        pos += c;
                    } while (buf.hasRemaining() && c > 0);
                    chunkRemaining -= written;
                    if (buf.hasRemaining()) {
                        return written;
                    }
                    read += written;
                    remaining -= written;
                }
            }
            //there is still more to read
            //we attempt to just use the delegates transferTo method
            if (chunkRemaining > 0) {
                long c = 0;
                remaining = Math.min(chunkRemaining, remaining);
                do {
                    c = delegate.transferTo(pos, remaining, target);
                    if (c > 0) {
                        read += c;
                        chunkRemaining -= c;
                        pos += c;
                        remaining -= c;
                    }
                } while (c > 0 && remaining > 0);
                if (c == -1) {
                    newVal |= FLAG_FINISHED;
                }
                if (chunkRemaining == 0) {
                    newVal |= FLAG_READING_NEWLINE;
                }
            }
            return read;

        } finally {
            //buffer will be freed if not needed in exitRead
            exitRead(oldVal, chunkRemaining, newVal & (FLAG_FINISHED | FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE), ~newVal & (FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE));
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        final long oldVal = enterRead();
        //we have read the last chunk, we just return EOF
        if (anyAreSet(oldVal, FLAG_FINISHED)) {
            return -1;
        }
        if (anyAreClear(oldVal, FLAG_CLOSED)) {
            throw new ClosedChannelException();
        }
        long newVal;

        if (anyAreSet(oldVal, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE | FLAG_READING_NEWLINE | FLAG_FINISHED)) {
            //we are in the process of reading chunking overhead
            newVal = readRawData(oldVal);
        } else {
            assert (oldVal & MASK_COUNT) != 0;
            //otherwise we still have some raw data we can read, either from the buffer
            //or directly form the underlying stream
            //note that chunkRemaining will never be zero here
            newVal = oldVal;
        }
        long chunkRemaining = newVal & MASK_COUNT;
        try {
            long remaining = count;
            if (anyAreSet(newVal, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE | FLAG_READING_NEWLINE | FLAG_FINISHED)) {
                //we did not manage to read anything except chunking overhead
                return 0;
            }
            //now we may have some stuff in the raw buffer
            //or the raw buffer may be exhausted, and we should read directly into the destination buffer
            //from the delegate

            int read = 0;
            final Pooled<ByteBuffer> buffer = rawData;
            if (buffer != null) {
                final ByteBuffer buf = buffer.getResource();
                if (buf.remaining() > count) {
                    //it won't fit
                    int orig = buf.limit();
                    buf.limit((int) (buf.position() + count));
                    int written = 0;
                    long c = 0;
                    do {
                        c = target.write(buf);
                        written += c;
                    } while (buf.hasRemaining() && c > 0);
                    buf.limit(orig);
                    chunkRemaining -= written;
                    return written;
                } else if (buf.hasRemaining()) {
                    int written = 0;
                    long c = 0;
                    do {
                        c = target.write(buf);
                        written += c;
                    } while (buf.hasRemaining() && c > 0);
                    chunkRemaining -= written;
                    if (buf.hasRemaining()) {
                        return written;
                    }
                    read += written;
                    remaining -= written;
                }
            }
            //there is still more to read
            //we attempt to just use the delegates transferTo method
            if (chunkRemaining > 0) {
                long c = 0;
                remaining = Math.min(chunkRemaining, remaining);
                do {
                    c = delegate.transferTo(remaining, throughBuffer, target);
                    if (c > 0) {
                        read += c;
                        chunkRemaining -= c;
                        remaining -= c;
                    }
                } while (c > 0 && remaining > 0);
                if (c == -1) {
                    newVal |= FLAG_FINISHED;
                }
                if (chunkRemaining == 0) {
                    newVal |= FLAG_READING_NEWLINE;
                }
            }
            return read;

        } finally {
            //buffer will be freed if not needed in exitRead
            exitRead(oldVal, chunkRemaining, newVal & (FLAG_FINISHED | FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE), ~newVal & (FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE));
        }
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
        //we have read the last chunk, we just return EOF
        if (anyAreSet(oldVal, FLAG_FINISHED)) {
            return -1;
        }
        if (anyAreClear(oldVal, FLAG_CLOSED)) {
            throw new ClosedChannelException();
        }
        long newVal;

        if (anyAreSet(oldVal, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE | FLAG_READING_NEWLINE | FLAG_FINISHED)) {
            //we are in the process of reading chunking overhead
            newVal = readRawData(oldVal);
        } else {
            assert (oldVal & MASK_COUNT) != 0;
            //otherwise we still have some raw data we can read, either from the buffer
            //or directly form the underlying stream
            //note that chunkRemaining will never be zero here
            newVal = oldVal;
        }
        long chunkRemaining = newVal & MASK_COUNT;

        final int originalLimit = dst.limit();
        try {
            if (anyAreSet(newVal, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE | FLAG_READING_NEWLINE | FLAG_FINISHED)) {
                //we did not manage to read anything except chunking overhead
                return 0;
            }
            //now we may have some stuff in the raw buffer
            //or the raw buffer may be exhausted, and we should read directly into the destination buffer
            //from the delegate

            int read = 0;
            final Pooled<ByteBuffer> buffer = rawData;
            if (buffer != null) {
                final ByteBuffer buf = buffer.getResource();
                int remaining = dst.remaining();
                if (buf.remaining() > remaining) {
                    //it won't fit
                    int orig = buf.limit();
                    buf.limit(buf.position() + remaining);
                    dst.put(buf);
                    buf.limit(orig);
                    chunkRemaining -= remaining;
                    return remaining;
                } else {
                    chunkRemaining -= buf.remaining();
                    read += buf.remaining();
                    dst.put(buf);
                }
            }
            //there is still more to read
            //we attempt to just read it directly into the destination buffer
            //adjusting the limit as nessesary to make sure we do not read too much
            if (chunkRemaining > 0) {
                if (chunkRemaining < dst.remaining()) {
                    dst.limit((int) (dst.position() + chunkRemaining));
                }
                int c = 0;
                do {
                    c = delegate.read(dst);
                    if (c > 0) {
                        read += c;
                        chunkRemaining -= c;
                    }
                } while (c > 0 && chunkRemaining > 0);
                if (c == -1) {
                    newVal |= FLAG_FINISHED;
                }
                if (chunkRemaining == 0) {
                    newVal |= FLAG_READING_NEWLINE;
                }
            }
            return read;

        } finally {
            //buffer will be freed if not needed in exitRead
            dst.limit(originalLimit);
            exitRead(oldVal, chunkRemaining, newVal & (FLAG_FINISHED | FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE), ~newVal & (FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE));
        }
    }

    /**
     * Reads raw data from the stream, dealing with chunking as nessesary.
     * <p/>
     * Any chunking overhead bytes will be consumed, and the raw data buffer will be left in a state where
     * it can be read up till the given number of bytes specified in the return value (once the return value has
     * been suitable masked).
     * <p/>
     * The caller must check any flags set in the return value and act accordingly.
     *
     * @return The new state value
     * @throws IOException
     */
    public long readRawData(long oldVal) throws IOException {
        long newVal = oldVal;
        long chunkRemaining = newVal & MASK_COUNT;
        Pooled<ByteBuffer> buffer = this.rawData;
        if (buffer == null) {
            buffer = this.rawData = bufferPool.allocate();
        }
        ByteBuffer buf = buffer.getResource();
        buf.compact();

        if (allAreClear(newVal, FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE) && chunkRemaining == 0) {
            newVal |= FLAG_FINISHED;
            return newVal;
        }
        while (anyAreSet(newVal, FLAG_READING_NEWLINE)) {
            while (buf.hasRemaining()) {
                byte b = buf.get();
                if (b == '\n') {
                    newVal = newVal & ~FLAG_READING_NEWLINE | FLAG_READING_LENGTH;
                    break;
                }
            }
            if (anyAreSet(newVal, FLAG_READING_NEWLINE)) {
                int c = delegate.read(buf);
                buf.flip();
                if (c == -1) {
                    newVal |= FLAG_FINISHED;
                    return newVal;
                } else if (c == 0) {
                    return newVal;
                }
            }
        }

        while (anyAreSet(newVal, FLAG_READING_LENGTH)) {
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
            if (anyAreSet(newVal, FLAG_READING_LENGTH)) {
                buf.compact();
                int c = delegate.read(buf);
                buf.flip();
                if (c == -1) {
                    newVal |= FLAG_FINISHED;
                    return newVal;
                } else if (c == 0) {
                    return newVal;
                }
            }
        }
        while (anyAreSet(newVal, FLAG_READING_TILL_END_OF_LINE)) {
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
                    return newVal;
                } else if (c == 0) {
                    return newVal;
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
        }
        //ok, we are done, return the state so the real read method can handle the chunked data
        //however it feels like
        return newVal;
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
        if (rawData != null && !rawData.getResource().hasRemaining()) {
            rawData.free();
            rawData = null;
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
