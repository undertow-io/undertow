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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * A 'gated' stream sink channel.
 * <p>
 * This channel has a gate which starts of closed. When the gate is closed writes will return 0. When the gate is opened
 * writes will resume as normal.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class GatedStreamSinkChannel implements StreamSinkChannel {
    private final StreamSinkChannel delegate;
    private final ChannelListener.SimpleSetter<GatedStreamSinkChannel> writeSetter = new ChannelListener.SimpleSetter<>();
    private final ChannelListener.SimpleSetter<GatedStreamSinkChannel> closeSetter = new ChannelListener.SimpleSetter<>();

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to wrap
     */
    public GatedStreamSinkChannel(final StreamSinkChannel delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings("unused")
    private int state;

    private static final int FLAG_GATE_OPEN = 1 << 0;
    private static final int FLAG_WRITES_RESUMED = 1 << 1;
    private static final int FLAG_CLOSE_REQUESTED = 1 << 2;
    private static final int FLAG_CLOSED = 1 << 3;

    /**
     * Open the gate and allow data to flow.  Once opened, the gate cannot be closed other than closing the channel.
     * <p>
     * If the shutdownWrites() or close() method has already been called this will result it in being invoked on the
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
                delegate.shutdownWrites();
            }
            if (allAreSet(val, FLAG_WRITES_RESUMED)) {
                delegate.wakeupWrites();
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

    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        if (handleGate()) {
            return 0;
        }
        return delegate.writeFinal(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (handleGate()) {
            return 0;
        }
        return delegate.writeFinal(srcs, offset, length);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        if (handleGate()) {
            return 0;
        }
        return delegate.writeFinal(srcs);
    }

    public int write(final ByteBuffer src) throws IOException {
        if (handleGate()) {
            return 0;
        }
        return delegate.write(src);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (handleGate()) {
            return 0;
        }
        return delegate.write(srcs, offset, length);
    }

    private boolean handleGate() throws ClosedChannelException {
        int val = state;
        if (anyAreSet(val, FLAG_CLOSE_REQUESTED)) {
            throw new ClosedChannelException();
        }
        if (anyAreClear(val, FLAG_GATE_OPEN)) {
            return true;
        }
        return false;
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (handleGate()) {
            return 0;
        }
        return delegate.transferFrom(src, position, count);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (handleGate()) {
            return 0;
        }
        return delegate.transferFrom(source, count, throughBuffer);
    }

    public boolean flush() throws IOException {
        if (anyAreClear(state, FLAG_GATE_OPEN)) {
            return false;
        }
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw new ClosedChannelException();
        }
        if (anyAreSet(state, FLAG_CLOSE_REQUESTED)) {
            boolean result = delegate.flush();
            if (result) {
                state |= FLAG_CLOSED;
            }
            return result;
        }
        return delegate.flush();
    }

    public void suspendWrites() {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.suspendWrites();
        } else {
            state &= ~FLAG_WRITES_RESUMED;
        }
    }

    public void resumeWrites() {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.resumeWrites();
        } else {
            state |= FLAG_WRITES_RESUMED;
        }
    }

    public boolean isWriteResumed() {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            return delegate.isWriteResumed();
        } else {
            return anyAreSet(state, FLAG_WRITES_RESUMED);
        }
    }

    public void wakeupWrites() {
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.wakeupWrites();
        } else {
            state |= FLAG_WRITES_RESUMED;
            getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    ChannelListeners.invokeChannelListener(GatedStreamSinkChannel.this, writeSetter.get());
                }
            });
        }
    }

    public void shutdownWrites() throws IOException {
        state |= FLAG_CLOSE_REQUESTED;
        if (anyAreSet(state, FLAG_GATE_OPEN)) {
            delegate.shutdownWrites();
        }
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

    public void awaitWritable() throws IOException {
        if (allAreClear(state, FLAG_GATE_OPEN)) {
            throw new IllegalStateException();//we don't allow this, as it results in thread safety issues
        }
        delegate.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        if (allAreClear(state, FLAG_GATE_OPEN)) {
            throw new IllegalStateException();//we don't allow this, as it results in thread safety issues
        }
        delegate.awaitWritable(time, timeUnit);
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
    public StreamSinkChannel getChannel() {
        return allAreSet(state, FLAG_GATE_OPEN) ? delegate : this;
    }
}
