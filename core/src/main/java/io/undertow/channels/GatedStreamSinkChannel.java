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

package io.undertow.channels;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConcurrentStreamChannelAccessException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static java.util.concurrent.locks.LockSupport.unpark;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.ChannelListeners.delegatingChannelListener;
import static org.xnio.ChannelListeners.invokeChannelListener;
import static org.xnio.IoUtils.safeClose;

/**
 * A stream sink channel which is "gated".
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class GatedStreamSinkChannel implements StreamSinkChannel {
    private final StreamSinkChannel delegate;
    private final Object permit;
    private final ChannelListener.SimpleSetter<GatedStreamSinkChannel> writeSetter = new ChannelListener.SimpleSetter<GatedStreamSinkChannel>();
    private final ChannelListener.SimpleSetter<GatedStreamSinkChannel> closeSetter = new ChannelListener.SimpleSetter<GatedStreamSinkChannel>();
    private final int config;

    /**
     * Construct a new instance.
     *
     * @param delegate the channel to wrap
     * @param permit the permit required to open the gate
     * @param configurable {@code true} to allow configuration of the delegate channel, {@code false} otherwise
     * @param passClose {@code true} to close the underlying channel when this channel is closed, {@code false} otherwise
     */
    public GatedStreamSinkChannel(final StreamSinkChannel delegate, final Object permit, final boolean configurable, final boolean passClose) {
        this.delegate = delegate;
        this.permit = permit;
        config = (configurable ? CONF_FLAG_CONFIGURABLE : 0) | (passClose ? CONF_FLAG_PASS_CLOSE : 0);
    }

    @SuppressWarnings("unused")
    private volatile int state;
    @SuppressWarnings("unused")
    private volatile Thread waiter;
    @SuppressWarnings("unused")
    private volatile Thread lockWaiter;

    private static final AtomicIntegerFieldUpdater<GatedStreamSinkChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(GatedStreamSinkChannel.class, "state");
    private static final AtomicReferenceFieldUpdater<GatedStreamSinkChannel, Thread> waiterUpdater = AtomicReferenceFieldUpdater.newUpdater(GatedStreamSinkChannel.class, Thread.class, "waiter");
    private static final AtomicReferenceFieldUpdater<GatedStreamSinkChannel, Thread> lockWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(GatedStreamSinkChannel.class, Thread.class, "lockWaiter");

    private static final int CONF_FLAG_CONFIGURABLE = 1 << 0;
    private static final int CONF_FLAG_PASS_CLOSE = 1 << 1;

    private static final int FLAG_IN_WRITE = 1 << 0;
    private static final int FLAG_IN = 1 << 1;
    private static final int FLAG_CLOSE_REQ = 1 << 2;
    private static final int FLAG_CLOSE_SENT = 1 << 3;
    private static final int FLAG_CLOSE_DONE = 1 << 4;
    private static final int FLAG_GATE_OPEN = 1 << 5;
    private static final int FLAG_RESUME = 1 << 6;

    private int enter(final int setFlags, final int clearFlags, int skipIfSet, int skipIfClear) {
        final boolean writeIntended = allAreSet(setFlags, FLAG_IN_WRITE);
        final Thread currentThread = currentThread();
        boolean intr = false;
        try {
            int oldVal, newVal;
            do {
                oldVal = state;
                if (writeIntended && allAreSet(oldVal, FLAG_IN_WRITE)) {
                    // concurrent writers are an error
                    throw new ConcurrentStreamChannelAccessException();
                }
                if (anyAreSet(oldVal, skipIfSet) || anyAreClear(oldVal, skipIfClear)) {
                    return oldVal;
                }
                while (anyAreSet(oldVal, FLAG_IN | FLAG_IN_WRITE)) {
                    final Thread waiter = lockWaiterUpdater.getAndSet(this, currentThread);
                    if (anyAreSet(oldVal = state, FLAG_IN | FLAG_IN_WRITE)) {
                        park(this);
                        if (interrupted()) {
                            intr = true;
                        }
                    }
                    safeUnpark(waiter);
                }
                newVal = oldVal & ~clearFlags | setFlags;
            } while (! stateUpdater.compareAndSet(this, oldVal, newVal));
            return oldVal;
        } finally {
            if (intr) currentThread.interrupt();
        }
    }

    private void exit(int enterFlag, final int setFlags) {
        int newVal = state & ~enterFlag | setFlags;
        stateUpdater.set(this, newVal);
        safeUnpark(lockWaiterUpdater.getAndSet(this, null));
    }

    /**
     * Open the gate and allow data to flow.  Once opened, the gate cannot be closed other than closing the channel.
     *
     * @param permit the permit passed in to the constructor
     */
    public void openGate(Object permit) {
        if (permit != this.permit) {
            throw new SecurityException();
        }
        int val = enter(FLAG_IN | FLAG_GATE_OPEN, 0, FLAG_GATE_OPEN, 0);
        if (allAreSet(val, FLAG_GATE_OPEN)) {
            return;
        }
        try {
            if (allAreSet(val, FLAG_CLOSE_DONE)) {
                safeClose(delegate);
            } else {
                boolean doResume = allAreSet(val, FLAG_RESUME);
                if (!doResume && delegate.isWriteResumed()) {
                    delegate.suspendWrites();
                }
                delegate.getWriteSetter().set(delegatingChannelListener(this, writeSetter));
                if (doResume && !delegate.isWriteResumed()) {
                    delegate.resumeWrites();
                }
            }
            safeUnpark(waiterUpdater.getAndSet(this, null));
        } finally {
            exit(FLAG_IN, 0);
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

    public int write(final ByteBuffer src) throws IOException {
        int val = enter(FLAG_IN_WRITE, 0, FLAG_CLOSE_REQ, 0);
        if (anyAreSet(val, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }
        try {
            if (anyAreClear(val, FLAG_GATE_OPEN)) {
                return 0;
            }
            return delegate.write(src);
        } finally {
            exit(FLAG_IN_WRITE, 0);
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        int val = enter(FLAG_IN_WRITE, 0, FLAG_CLOSE_REQ, 0);
        if (anyAreSet(val, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }
        try {
            if (anyAreClear(val, FLAG_GATE_OPEN)) {
                return 0;
            }
            return delegate.write(srcs, offset, length);
        } finally {
            exit(FLAG_IN_WRITE, 0);
        }
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        int val = enter(FLAG_IN_WRITE, 0, FLAG_CLOSE_REQ, 0);
        if (anyAreSet(val, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }
        try {
            if (anyAreClear(val, FLAG_GATE_OPEN)) {
                return 0L;
            }
            return delegate.transferFrom(src, position, count);
        } finally {
            exit(FLAG_IN_WRITE, 0);
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        int val = enter(FLAG_IN_WRITE, 0, FLAG_CLOSE_REQ, 0);
        if (anyAreSet(val, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }
        try {
            if (anyAreClear(val, FLAG_GATE_OPEN)) {
                return 0L;
            }
            return delegate.transferFrom(source, count, throughBuffer);
        } finally {
            exit(FLAG_IN_WRITE, 0);
        }
    }

    public boolean flush() throws IOException {
        int val = enter(FLAG_IN, 0, FLAG_CLOSE_DONE, 0);
        if (allAreSet(val, FLAG_CLOSE_DONE)) {
            return true;
        }
        int setFlags = 0;
        try {
            if (allAreClear(val, FLAG_GATE_OPEN)) {
                return false;
            }
            if (allAreSet(config, CONF_FLAG_PASS_CLOSE) && allAreSet(val, FLAG_CLOSE_REQ) && allAreClear(val, FLAG_CLOSE_SENT)) {
                setFlags |= FLAG_CLOSE_SENT;
                delegate.shutdownWrites();
            }
            boolean flushed = delegate.flush();
            if (flushed && anyAreSet(val | setFlags, FLAG_CLOSE_SENT)) {
                delegate.suspendWrites();
                delegate.getWriteSetter().set(null);
                setFlags |= FLAG_CLOSE_DONE;
            }
            return flushed;
        } finally {
            exit(FLAG_IN, setFlags);
        }
    }

    public void suspendWrites() {
        int val = enter(FLAG_IN, FLAG_RESUME, 0, FLAG_RESUME);
        if (allAreClear(val, FLAG_RESUME)) {
            return;
        }
        try {
            if (allAreSet(val, FLAG_GATE_OPEN)) {
                delegate.suspendWrites();
            }
        } finally {
            exit(FLAG_IN, 0);
        }
    }

    public void resumeWrites() {
        int val = enter(FLAG_IN | FLAG_RESUME, 0, FLAG_RESUME, 0);
        if (allAreSet(val, FLAG_RESUME)) {
            return;
        }
        try {
            if (allAreSet(val, FLAG_GATE_OPEN)) {
                delegate.resumeWrites();
            }
        } finally {
            exit(FLAG_IN, 0);
        }
    }

    public boolean isWriteResumed() {
        final int state = this.state;
        return allAreSet(state, FLAG_RESUME) && allAreClear(state, FLAG_CLOSE_DONE);
    }

    public void wakeupWrites() {
        int val = enter(FLAG_IN | FLAG_RESUME, 0, FLAG_RESUME, 0);
        if (allAreSet(val, FLAG_RESUME)) {
            return;
        }
        try {
            if (allAreSet(val, FLAG_GATE_OPEN)) {
                delegate.wakeupWrites();
            } else {
                getWriteThread().execute(ChannelListeners.getChannelListenerTask(this, writeSetter));
            }
        } finally {
            exit(FLAG_IN, 0);
        }
    }

    public void shutdownWrites() throws IOException {
        int val = enter(FLAG_IN | FLAG_CLOSE_REQ, 0, FLAG_CLOSE_REQ, 0);
        if (allAreSet(val, FLAG_CLOSE_REQ)) {
            return;
        }
        int setFlags = 0;
        try {
            if (allAreSet(val, FLAG_GATE_OPEN)) {
                setFlags |= FLAG_CLOSE_SENT;
                if (allAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                    delegate.shutdownWrites();
                }
            }
        } finally {
            exit(FLAG_IN, setFlags);
        }
    }

    public void close() throws IOException {
        int val = enter(FLAG_IN | FLAG_CLOSE_REQ | FLAG_CLOSE_SENT | FLAG_CLOSE_DONE, 0, FLAG_CLOSE_DONE, 0);
        if (allAreSet(val, FLAG_CLOSE_DONE)) {
            return;
        }
        try {
            if (allAreSet(val, FLAG_GATE_OPEN)) {
                delegate.suspendWrites();
                delegate.getWriteSetter().set(null);
                if (allAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                    delegate.close();
                }
            }
        } finally {
            exit(FLAG_IN, 0);
            invokeChannelListener(this, closeSetter.get());
        }
    }

    public void awaitWritable() throws IOException {
        if (allAreClear(state, FLAG_GATE_OPEN | FLAG_CLOSE_DONE)) {
            final Thread next = waiterUpdater.getAndSet(this, currentThread());
            try {
                while (allAreClear(state, FLAG_GATE_OPEN | FLAG_CLOSE_DONE)) {
                    park(this);
                    if (currentThread().isInterrupted()) {
                        throw new InterruptedIOException();
                    }
                }
            } finally {
                safeUnpark(next);
            }
        }
        delegate.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        long t = timeUnit.toNanos(time);
        long start;
        if (allAreClear(state, FLAG_GATE_OPEN | FLAG_CLOSE_DONE)) {
            final Thread next = waiterUpdater.getAndSet(this, currentThread());
            try {
                long now = System.nanoTime();
                while (allAreClear(state, FLAG_GATE_OPEN | FLAG_CLOSE_DONE)) {
                    if (t <= 0L) {
                        return;
                    }
                    start = now;
                    parkNanos(this, t);
                    if (currentThread().isInterrupted()) {
                        throw new InterruptedIOException();
                    }
                    t -= (now = System.nanoTime()) - start;
                }
            } finally {
                safeUnpark(next);
            }
        }
        delegate.awaitWritable(t, TimeUnit.NANOSECONDS);
    }

    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSE_DONE);
    }

    public boolean supportsOption(final Option<?> option) {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) && delegate.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) ? delegate.getOption(option) : null;
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) ? delegate.setOption(option, value) : null;
    }

    private static void safeUnpark(final Thread waiter) {
        if (waiter != null) unpark(waiter);
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
