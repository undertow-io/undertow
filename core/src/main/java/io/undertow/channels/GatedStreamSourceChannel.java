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

package io.undertow.channels;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * A 'gated' stream source channel.
 * <p>
 * This channel has a gate which starts of closed. When the gate is closed reads will return 0. When the gate is opened
 * reads will resume as normal.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class GatedStreamSourceChannel implements StreamSourceChannel {
    private final StreamSourceChannel delegate;
    private final ChannelListener.SimpleSetter<GatedStreamSourceChannel> readSetter = new ChannelListener.SimpleSetter<>();
    private final ChannelListener.SimpleSetter<GatedStreamSourceChannel> closeSetter = new ChannelListener.SimpleSetter<>();

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to wrap
     */
    public GatedStreamSourceChannel(final StreamSourceChannel delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings("unused")
    private int state;

    private static final int FLAG_GATE_OPEN = 1 << 0;
    private static final int FLAG_READS_RESUMED = 1 << 1;
    private static final int FLAG_CLOSE_REQUESTED = 1 << 2;
    private static final int FLAG_CLOSED = 1 << 3;

    /**
     * Open the gate and allow data to flow.  Once opened, the gate cannot be closed other than closing the channel.
     * <p>
     * If the shutdownReads() or close() method has already been called this will result it in being invoked on the
     * delegate.
     */
    public void openGate() throws IOException {
        int val = state;
        if (allAreSet(val, FLAG_GATE_OPEN)) {
            return;
        }
        state |= FLAG_GATE_OPEN;
        if (allAreSet(val, FLAG_CLOSED)) {
            delegate.close();
        } else {
            if (allAreSet(val, FLAG_CLOSE_REQUESTED)) {
                delegate.shutdownReads();
            }
            if (allAreSet(val, FLAG_READS_RESUMED)) {
                delegate.wakeupReads();
            }
        }
    }

    public boolean isGateOpen() {
        return allAreSet(state, FLAG_GATE_OPEN);
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        int val = state;
        if (anyAreSet(val, FLAG_CLOSE_REQUESTED)) {
            return -1;
        }
        if (anyAreClear(val, FLAG_GATE_OPEN)) {
            return 0;
        }
        return delegate.transferTo(position, count, target);
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        int val = state;
        if (anyAreSet(val, FLAG_CLOSE_REQUESTED)) {
            return -1;
        }
        if (anyAreClear(val, FLAG_GATE_OPEN)) {
            return 0;
        }
        return delegate.transferTo(count, throughBuffer, target);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        int val = state;
        if (anyAreSet(val, FLAG_CLOSE_REQUESTED)) {
            return -1;
        }
        if (anyAreClear(val, FLAG_GATE_OPEN)) {
            return 0;
        }
        return delegate.read(dsts, offset, length);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        int val = state;
        if (anyAreSet(val, FLAG_CLOSE_REQUESTED)) {
            return -1;
        }
        if (anyAreClear(val, FLAG_GATE_OPEN)) {
            return 0;
        }
        return delegate.read(dsts);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int val = state;
        if (anyAreSet(val, FLAG_CLOSE_REQUESTED)) {
            return -1;
        }
        if (anyAreClear(val, FLAG_GATE_OPEN)) {
            return 0;
        }
        return delegate.read(dst);
    }
    @Override
    public void suspendReads() {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.suspendReads();
        } else {
            state &= ~FLAG_READS_RESUMED;
        }
    }

    @Override
    public void resumeReads() {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.resumeReads();
        } else {
            state |= FLAG_READS_RESUMED;
        }
    }

    @Override
    public boolean isReadResumed() {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            return delegate.isReadResumed();
        } else {
            return anyAreSet(state, FLAG_READS_RESUMED);
        }
    }

    @Override
    public void wakeupReads() {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.resumeReads();
        } else {
            state |= FLAG_READS_RESUMED;
            getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    ChannelListeners.invokeChannelListener(GatedStreamSourceChannel.this, readSetter.get());
                }
            });
        }
    }

    @Override
    public void shutdownReads() throws IOException {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.shutdownReads();
        } else {
            state |= FLAG_CLOSE_REQUESTED;
        }
    }

    @Override
    public void awaitReadable() throws IOException {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.awaitReadable();
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.awaitReadable(time, timeUnit);
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public XnioExecutor getReadThread() {
        return delegate.getIoThread();
    }

    @Override
    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

    public void close() throws IOException {
        if (allAreSet(state, FLAG_CLOSED)) {
            return;
        }
        state |= FLAG_CLOSED;
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.close();
        }
    }

    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSED);
    }

    public boolean supportsOption(final Option<?> option) {
        return false;
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return null;
    }

    /**
     * Get the underlying channel if the gate is open, else return this channel.
     *
     * @return the underlying channel, or this channel if the gate is not open
     */
    public StreamSourceChannel getChannel() {
        return allAreSet(state, FLAG_GATE_OPEN) ? delegate : this;
    }

}
