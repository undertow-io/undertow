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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;

import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * A conduit that discards all data written to it. This allows head requests to 'just work', as all data written
 * will be discarded.
 *
 * @author Stuart Douglas
 */
public final class HeadStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final ConduitListener<? super HeadStreamSinkConduit> finishListener;

    private int state;
    private final boolean shutdownDelegate;

    private static final int FLAG_CLOSE_REQUESTED = 1;
    private static final int FLAG_CLOSE_COMPLETE = 1 << 1;
    private static final int FLAG_FINISHED_CALLED = 1 << 2;

    /**
     * Construct a new instance.
     *
     * @param next           the next channel
     * @param finishListener the listener to call when the channel is closed or the length is reached
     */
    public HeadStreamSinkConduit(final StreamSinkConduit next, final ConduitListener<? super HeadStreamSinkConduit> finishListener) {
        this(next, finishListener, false);
    }

    /**
     * Construct a new instance.
     *
     * @param next           the next channel
     * @param finishListener the listener to call when the channel is closed or the length is reached
     */
    public HeadStreamSinkConduit(final StreamSinkConduit next, final ConduitListener<? super HeadStreamSinkConduit> finishListener, boolean shutdownDelegate) {
        super(next);
        this.finishListener = finishListener;
        this.shutdownDelegate = shutdownDelegate;
    }

    public int write(final ByteBuffer src) throws IOException {
        if (anyAreSet(state, FLAG_CLOSE_COMPLETE)) {
            throw new ClosedChannelException();
        }
        int remaining = src.remaining();
        src.position(src.position() + remaining);
        return remaining;
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (anyAreSet(state, FLAG_CLOSE_COMPLETE)) {
            throw new ClosedChannelException();
        }
        long total = 0;
        for (int i = offset; i < offset + length; ++i) {
            ByteBuffer src = srcs[i];
            int remaining = src.remaining();
            total += remaining;
            src.position(src.position() + remaining);
        }
        return total;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (anyAreSet(state, FLAG_CLOSE_COMPLETE)) {
            throw new ClosedChannelException();
        }
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (anyAreSet(state, FLAG_CLOSE_COMPLETE)) {
            throw new ClosedChannelException();
        }
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    public boolean flush() throws IOException {
        int val = state;
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
        int oldVal, newVal;
        oldVal = state;
        if (anyAreSet(oldVal, FLAG_CLOSE_REQUESTED | FLAG_CLOSE_COMPLETE)) {
            // no action necessary
            return;
        }
        newVal = oldVal | FLAG_CLOSE_REQUESTED;
        state = newVal;
        if(shutdownDelegate) {
            next.terminateWrites();
        }
    }

    private void exitFlush(int oldVal, boolean flushed) {
        int newVal = oldVal;
        boolean callFinish = false;
        if (anyAreSet(oldVal, FLAG_CLOSE_REQUESTED) && flushed) {
            newVal |= FLAG_CLOSE_COMPLETE;
            if (!anyAreSet(oldVal, FLAG_FINISHED_CALLED)) {
                newVal |= FLAG_FINISHED_CALLED;
                callFinish = true;
            }
            state = newVal;
            if (callFinish) {
                if (finishListener != null) {
                    finishListener.handleEvent(this);
                }
            }
        }
    }


}
