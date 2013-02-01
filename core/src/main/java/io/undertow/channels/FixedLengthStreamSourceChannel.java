/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

package io.undertow.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.ProtectedWrappedChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static java.lang.Math.min;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * A channel which reads data of a fixed length and calls a finish listener.  When the finish listener is called,
 * it should examine the result of {@link #getRemaining()} to see if more bytes were pending when the channel was
 * closed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
/*
 * Implementation notes
 * --------------------
 * The {@code exhausted} flag is set once a method returns -1 and signifies that the read listener should no longer be
 * called.  The {@code finishListener} is called when remaining is reduced to 0 or when the channel is closed explicitly.
 * If there are 0 remaining bytes but {@code FLAG_FINISHED} has not yet been set, the channel is considered "ready" until
 * the EOF -1 value is read or the channel is closed.  Since this is a half-duplex channel, shutting down reads is
 * identical to closing the channel.
 */
public final class FixedLengthStreamSourceChannel implements StreamSourceChannel, ProtectedWrappedChannel<StreamSourceChannel> {
    private final StreamSourceChannel delegate;
    private final boolean configurable;
    private final Object guard;

    private final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener;
    private final ChannelListener.SimpleSetter<FixedLengthStreamSourceChannel> readSetter = new ChannelListener.SimpleSetter<FixedLengthStreamSourceChannel>();
    private final ChannelListener.SimpleSetter<FixedLengthStreamSourceChannel> closeSetter = new ChannelListener.SimpleSetter<FixedLengthStreamSourceChannel>();

    @SuppressWarnings("unused")
    private long state;

    private static final long FLAG_CLOSED = 1L << 63L;
    private static final long FLAG_FINISHED = 1L << 62L;
    private static final long MASK_COUNT = longBitMask(0, 61);

    /**
     * Construct a new instance.  The given listener is called once all the bytes are read from the stream
     * <b>or</b> the stream is closed.  This listener should cause the remaining data to be drained from the
     * underlying stream via the {@link #drain()} method if the underlying stream is to be reused.
     * <p/>
     * Calling this constructor will replace the read listener of the underlying channel.  The listener should be
     * restored from the {@code finishListener} object.  The underlying stream should not be closed while this wrapper
     * stream is active.
     *
     * @param delegate       the stream source channel to read from
     * @param contentLength  the amount of content to read
     * @param finishListener the listener to call once the stream is exhausted or closed
     * @param guard          the guard object to use
     */
    public FixedLengthStreamSourceChannel(final StreamSourceChannel delegate, final long contentLength, final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener, final Object guard) {
        this(delegate, contentLength, false, finishListener, guard);
    }

    /**
     * Construct a new instance.  The given listener is called once all the bytes are read from the stream
     * <b>or</b> the stream is closed.  This listener should cause the remaining data to be drained from the
     * underlying stream via the {@link #drain()} method if the underlying stream is to be reused.
     * <p/>
     * Calling this constructor will replace the read listener of the underlying channel.  The listener should be
     * restored from the {@code finishListener} object.  The underlying stream should not be closed while this wrapper
     * stream is active.
     *
     * @param delegate       the stream source channel to read from
     * @param contentLength  the amount of content to read
     * @param configurable   {@code true} to allow options to pass through to the delegate, {@code false} otherwise
     * @param finishListener the listener to call once the stream is exhausted or closed
     * @param guard          the guard object to use
     */
    public FixedLengthStreamSourceChannel(final StreamSourceChannel delegate, final long contentLength, final boolean configurable, final ChannelListener<? super FixedLengthStreamSourceChannel> finishListener, final Object guard) {
        this.guard = guard;
        this.finishListener = finishListener;
        if (contentLength < 0L) {
            throw new IllegalArgumentException("Content length must be greater than or equal to zero");
        } else if (contentLength > MASK_COUNT) {
            throw new IllegalArgumentException("Content length is too long");
        }
        this.delegate = delegate;
        state = contentLength;
        delegate.getReadSetter().set(ChannelListeners.delegatingChannelListener(FixedLengthStreamSourceChannel.this, readSetter));
        this.configurable = configurable;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long val = state;
        if (anyAreSet(val, FLAG_CLOSED | FLAG_FINISHED) || allAreClear(val, MASK_COUNT)) {
            return 0L;
        }
        long res = 0L;
        try {
            return res = delegate.transferTo(position, min(count, val), target);
        } finally {
            exitRead(res);
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        if (count == 0L) {
            return 0L;
        }
        long val = state;
        if (anyAreSet(val, FLAG_CLOSED | FLAG_FINISHED) || allAreClear(val, MASK_COUNT)) {
            return -1;
        }
        long res = 0L;
        try {
            if (allAreSet(val, FLAG_CLOSED) || val == 0L) {
                return -1L;
            }
            return res = delegate.transferTo(min(count, val), throughBuffer, target);
        } finally {
            exitRead(res == -1L ? val & MASK_COUNT : res);
        }
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0L;
        } else if (length == 1) {
            return read(dsts[offset]);
        }
        long val = state;
        if (allAreSet(val, FLAG_CLOSED) || allAreClear(val, MASK_COUNT)) {
            return -1;
        }
        long res = 0L;
        try {
            if ((val & MASK_COUNT) == 0L) {
                return -1L;
            }
            int lim;
            // The total amount of buffer space discovered so far.
            long t = 0L;
            for (int i = 0; i < length; i++) {
                final ByteBuffer buffer = dsts[i + offset];
                // Grow the discovered buffer space by the remaining size of the current buffer.
                // We want to capture the limit so we calculate "remaining" ourselves.
                t += (lim = buffer.limit()) - buffer.position();
                if (t > (val & MASK_COUNT)) {
                    // only read up to this point, and trim the last buffer by the number of extra bytes
                    buffer.limit(lim - (int) (t - (val & MASK_COUNT)));
                    try {
                        return res = delegate.read(dsts, offset, i + 1);
                    } finally {
                        // restore the original limit
                        buffer.limit(lim);
                    }
                }
            }
            // the total buffer space is less than the remaining count.
            return res = delegate.read(dsts, offset, length);
        } finally {
            exitRead(res == -1L ? val & MASK_COUNT : res);
        }
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    public int read(final ByteBuffer dst) throws IOException {
        long val = state;
        if (allAreSet(val, FLAG_CLOSED) || allAreClear(val, MASK_COUNT)) {
            return -1;
        }
        int res = 0;
        final long remaining = val & MASK_COUNT;
        try {
            final int lim = dst.limit();
            final int pos = dst.position();
            if (lim - pos > remaining) {
                dst.limit((int) (remaining + (long) pos));
                try {
                    return res = delegate.read(dst);
                } finally {
                    dst.limit(lim);
                }
            } else {
                return res = delegate.read(dst);
            }
        } finally {
            exitRead(res == -1 ? remaining : (long) res);
        }
    }

    public void suspendReads() {
        long val = state;
        if (anyAreSet(val, FLAG_CLOSED | FLAG_FINISHED) || allAreClear(val, MASK_COUNT)) {
            return;
        }
        delegate.suspendReads();
    }

    public void resumeReads() {
        long val = state;
        if (anyAreSet(val, FLAG_CLOSED | FLAG_FINISHED) || allAreClear(val, MASK_COUNT)) {
            return;
        }
        if (val == 0L) {
            delegate.wakeupReads();
        } else {
            delegate.resumeReads();
        }
    }

    public boolean isReadResumed() {
        return allAreClear(state, FLAG_CLOSED) && delegate.isReadResumed();
    }

    public void wakeupReads() {
        long val = state;
        if (anyAreSet(val, FLAG_CLOSED | FLAG_FINISHED) || allAreClear(val, MASK_COUNT)) {
            return;
        }
        delegate.wakeupReads();
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

    public StreamSourceChannel getChannel(final Object guard) {
        final Object ourGuard = this.guard;
        if (ourGuard == null || guard == ourGuard) {
            return delegate;
        } else {
            return null;
        }
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
        oldVal = state;
        if (anyAreSet(oldVal, FLAG_CLOSED)) {
            return oldVal;
        }
        newVal = oldVal | FLAG_CLOSED;
        state = newVal;
        return oldVal;
    }

    private void exitShutdownReads(long oldVal) {
        if (!allAreClear(oldVal, MASK_COUNT)) {
            callFinish();
        }
        callClosed();
    }

    /**
     * Exit a read method.
     *
     * @param consumed the number of bytes consumed by this call (may be 0)
     */
    private void exitRead(long consumed) {
        long oldVal = state;
        long newVal = oldVal - consumed;
        state = newVal;
        if (anyAreSet(oldVal, MASK_COUNT) && allAreClear(newVal, MASK_COUNT)) {
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
