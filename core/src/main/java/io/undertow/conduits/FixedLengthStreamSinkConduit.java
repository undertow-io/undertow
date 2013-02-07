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

package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.channels.FixedLengthOverflowException;
import org.xnio.channels.FixedLengthUnderflowException;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

import static java.lang.Math.min;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * A channel which writes a fixed amount of data.  A listener is called once the data has been written.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class FixedLengthStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {
    private final int config;
    private final Object guard;

    private final ConduitListener<? super FixedLengthStreamSinkConduit> finishListener;

    private long state;

    private static final int CONF_FLAG_CONFIGURABLE = 1 << 0;
    private static final int CONF_FLAG_PASS_CLOSE = 1 << 1;

    private static final long FLAG_CLOSE_REQUESTED = 1L << 63L;
    private static final long FLAG_CLOSE_COMPLETE = 1L << 62L;
    private static final long FLAG_FINISHED_CALLED = 1L << 61L;
    private static final long MASK_COUNT = longBitMask(0, 60);

    /**
     * Construct a new instance.
     *
     * @param next           the next channel
     * @param contentLength  the content length
     * @param configurable   {@code true} if this instance should pass configuration to the next
     * @param propagateClose {@code true} if this instance should pass close to the next
     * @param finishListener the listener to call when the channel is closed or the length is reached
     * @param guard          the guard object to use
     */
    public FixedLengthStreamSinkConduit(final StreamSinkConduit next, final long contentLength, final boolean configurable, final boolean propagateClose, final ConduitListener<? super FixedLengthStreamSinkConduit> finishListener, final Object guard) {
        super(next);
        if (contentLength < 0L) {
            throw new IllegalArgumentException("Content length must be greater than or equal to zero");
        } else if (contentLength > MASK_COUNT) {
            throw new IllegalArgumentException("Content length is too long");
        }
        this.guard = guard;
        this.finishListener = finishListener;
        config = (configurable ? CONF_FLAG_CONFIGURABLE : 0) | (propagateClose ? CONF_FLAG_PASS_CLOSE : 0);
        this.state = contentLength;
    }


    public int write(final ByteBuffer src) throws IOException {
        if (!src.hasRemaining()) {
            return 0;
        }
        long val = state;
        if (allAreSet(val, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(val, MASK_COUNT)) {
            throw new FixedLengthOverflowException();
        }
        int res = 0;
        final long remaining = val & MASK_COUNT;
        try {
            final int lim = src.limit();
            final int pos = src.position();
            if (lim - pos > remaining) {
                src.limit((int) (remaining - (long) pos));
                try {
                    return res = next.write(src);
                } finally {
                    src.limit(lim);
                }
            } else {
                return res = next.write(src);
            }
        } finally {
            exitWrite(val, (long) res);
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0L;
        } else if (length == 1) {
            return write(srcs[offset]);
        }
        long val = state;
        if (allAreSet(val, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(val, MASK_COUNT)) {
            throw new FixedLengthOverflowException();
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
                final ByteBuffer buffer = srcs[i + offset];
                // Grow the discovered buffer space by the remaining size of the current buffer.
                // We want to capture the limit so we calculate "remaining" ourselves.
                t += (lim = buffer.limit()) - buffer.position();
                if (t > (val & MASK_COUNT)) {
                    // only read up to this point, and trim the last buffer by the number of extra bytes
                    buffer.limit(lim - (int) (t - (val & MASK_COUNT)));
                    try {
                        return res = next.write(srcs, offset, i + 1);
                    } finally {
                        // restore the original limit
                        buffer.limit(lim);
                    }
                }
            }
            if (t == 0L) {
                return 0L;
            }
            // the total buffer space is less than the remaining count.
            return res = next.write(srcs, offset, length);
        } finally {
            exitWrite(val, res);
        }
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (count == 0L) return 0L;
        long val = state;
        if (allAreSet(val, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(val, MASK_COUNT)) {
            throw new FixedLengthOverflowException();
        }
        long res = 0L;
        try {
            return res = next.transferFrom(src, position, min(count, (val & MASK_COUNT)));
        } finally {
            exitWrite(val, res);
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (count == 0L) return 0L;
        long val = state;
        if (allAreSet(val, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (allAreClear(val, MASK_COUNT)) {
            throw new FixedLengthOverflowException();
        }
        long res = 0L;
        try {
            return res = next.transferFrom(source, min(count, (val & MASK_COUNT)), throughBuffer);
        } finally {
            exitWrite(val, res);
        }
    }

    public boolean flush() throws IOException {
        long val = state;
        if (anyAreSet(val, FLAG_CLOSE_COMPLETE)) {
            return true;
        }
        boolean flushed = false;
        try {
            return flushed = next.flush();
        } finally {
            exitFlush(val, flushed);
        }
    }

    public void suspendWrites() {
        long val = state;
        if (anyAreSet(val, FLAG_CLOSE_COMPLETE)) {
            return;
        }
        next.suspendWrites();
    }

    public void resumeWrites() {
        long val = state;
        if (anyAreSet(val, FLAG_CLOSE_COMPLETE)) {
            return;
        }
        next.resumeWrites();
    }

    public boolean isWriteResumed() {
        // not perfect but not provably wrong either...
        return allAreClear(state, FLAG_CLOSE_COMPLETE) && next.isWriteResumed();
    }

    public void wakeupWrites() {
        long val = state;
        if (anyAreSet(val, FLAG_CLOSE_COMPLETE)) {
            return;
        }
        next.wakeupWrites();
    }

    public void terminateWrites() throws IOException {
        final long val = enterShutdown();
        if (anyAreSet(val, MASK_COUNT)) {
            try {
                throw new FixedLengthUnderflowException((val & MASK_COUNT) + " bytes remaining");
            } finally {
                if (allAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                    next.truncateWrites();
                }
            }
        } else if (allAreSet(config, CONF_FLAG_PASS_CLOSE)) {
            next.terminateWrites();
        }

    }

    public void awaitWritable() throws IOException {
        next.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        next.awaitWritable(time, timeUnit);
    }


    /**
     * Get the number of remaining bytes in this fixed length channel.
     *
     * @return the number of remaining bytes
     */
    public long getRemaining() {
        return state & MASK_COUNT;
    }

    private void exitWrite(long oldVal, long consumed) {
        long newVal = oldVal - consumed;
        state = newVal;
    }

    private void exitFlush(long oldVal, boolean flushed) {
        long newVal = oldVal;
        boolean callFinish = false;
        if (anyAreSet(oldVal, FLAG_CLOSE_REQUESTED) && flushed) {
            newVal |= FLAG_CLOSE_COMPLETE;
        }

        if (flushed && !anyAreSet(oldVal, FLAG_FINISHED_CALLED)) {
            newVal |= FLAG_FINISHED_CALLED;
            callFinish = true;
        }
        state = newVal;
        if (callFinish) {
            finishListener.handleEvent(this);
        }
    }

    private long enterShutdown() {
        long oldVal, newVal;
        oldVal = state;
        if (anyAreSet(oldVal, FLAG_CLOSE_REQUESTED | FLAG_CLOSE_COMPLETE)) {
            // no action necessary
            return oldVal;
        }
        newVal = oldVal | FLAG_CLOSE_REQUESTED;
        if (anyAreSet(oldVal, MASK_COUNT)) {
            // error: channel not filled.  set both close flags.
            newVal |= FLAG_CLOSE_COMPLETE;
        }
        state = newVal;
        return oldVal;
    }

    private long enterClose() {
        long oldVal, newVal;
        oldVal = state;
        if (anyAreSet(oldVal, FLAG_CLOSE_COMPLETE)) {
            // no action necessary
            return oldVal;
        }
        newVal = oldVal | FLAG_CLOSE_REQUESTED | FLAG_CLOSE_COMPLETE;
        if (anyAreSet(oldVal, MASK_COUNT)) {
            // error: channel not filled.  set both close flags.
            newVal |= FLAG_CLOSE_REQUESTED | FLAG_CLOSE_COMPLETE;
        }
        state = newVal;
        return oldVal;
    }

    private void exitClose(long oldVal) {
        if (!anyAreSet(oldVal, FLAG_CLOSE_COMPLETE)) {
            finishListener.handleEvent(this);
        }
    }

}
