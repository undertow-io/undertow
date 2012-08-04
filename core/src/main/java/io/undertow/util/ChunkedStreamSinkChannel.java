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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.undertow.UndertowMessages;
import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.ConcurrentStreamChannelAccessException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.util.concurrent.locks.LockSupport.park;
import static java.util.concurrent.locks.LockSupport.unpark;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.ChannelListeners.invokeChannelListener;

/**
 * Channel that implements HTTP chunked transfer coding.
 *
 * @author Stuart Douglas
 */
public class ChunkedStreamSinkChannel implements StreamSinkChannel {

    private static final Logger log = Logger.getLogger(ChunkedStreamSinkChannel.class);

    private final StreamSinkChannel delegate;
    private final ChannelListener.SimpleSetter<ChunkedStreamSinkChannel> closeSetter = new ChannelListener.SimpleSetter<ChunkedStreamSinkChannel>();
    private final ChannelListener.SimpleSetter<ChunkedStreamSinkChannel> writeSetter = new ChannelListener.SimpleSetter<ChunkedStreamSinkChannel>();
    private final ChannelListener<? super ChunkedStreamSinkChannel> finishListener;
    private final int config;

    /**
     * The buffer pool that is used to allocate the buffers
     */
    private final Pool<ByteBuffer> bufferPool;

    /**
     * The current buffer that is being written out by a write listener. This will be cleared
     * in the {@link #exit(int, int, int)} method, unless the {@link #FLAG_CLOSING_ASYNC} or
     * {@link #FLAG_WRITING_CHUNKED} flag is set
     */
    private volatile Pooled<ByteBuffer> pooledBuffer = null;

    private static final byte[] LAST_CHUNK = "0\r\n\r\n".getBytes();
    public static final byte[] CRLF = "\r\n".getBytes();

    @SuppressWarnings("unused")
    private volatile int state;
    @SuppressWarnings("unused")
    private volatile Thread waiter;
    @SuppressWarnings("unused")
    private volatile Thread lockWaiter;

    private static final AtomicIntegerFieldUpdater<ChunkedStreamSinkChannel> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ChunkedStreamSinkChannel.class, "state");
    private static final AtomicReferenceFieldUpdater<ChunkedStreamSinkChannel, Thread> waiterUpdater = AtomicReferenceFieldUpdater.newUpdater(ChunkedStreamSinkChannel.class, Thread.class, "waiter");
    private static final AtomicReferenceFieldUpdater<ChunkedStreamSinkChannel, Thread> lockWaiterUpdater = AtomicReferenceFieldUpdater.newUpdater(ChunkedStreamSinkChannel.class, Thread.class, "lockWaiter");

    /**
     * The maximum overhead in bytes that a chunk can add
     */
    private static final int CHUNKING_OVERHEAD_MAX_BYTES = 14;


    private static final int CONF_FLAG_CONFIGURABLE = 1 << 0;
    private static final int CONF_FLAG_PASS_CLOSE = 1 << 1;

    /**
     * Flag that indicates we are in the middle of a write operation
     */
    private static final int FLAG_IN_WRITE = 1 << 0;
    /**
     * Flag that indicates we are in the middule of an operation
     */
    private static final int FLAG_IN = 1 << 1;
    /**
     * Flag that is set when {@link #shutdownWrites()} or @{link #close()} is called
     */
    private static final int FLAG_CLOSE_REQ = 1 << 2;
    private static final int FLAG_CLOSE_SENT = 1 << 3;
    private static final int FLAG_CLOSE_DONE = 1 << 4;
    /**
     * Flag that is set if {@link #resumeWrites()} has been called.
     */
    private static final int FLAG_RESUME = 1 << 5;
    /**
     * Flag that indicates that chunked data is in the process of being written out by the write listener
     */
    private static final int FLAG_WRITING_CHUNKED = 1 << 6;
    /**
     * Flag that indicates that either @{link #close} or {@link #shutdownWrites()} has been called
     * and that the channel is in the process of writing out the last chunk.
     */
    private static final int FLAG_CLOSING_ASYNC = 1 << 7;

    /**
     * Set when the finish listener has been invoked
     */
    private static final int FLAG_FINISH = 1 << 8;

    /**
     * Construct a new instance.
     *
     * @param delegate     the channel to wrap
     * @param configurable {@code true} to allow configuration of the delegate channel, {@code false} otherwise
     * @param passClose    {@code true} to close the underlying channel when this channel is closed, {@code false} otherwise
     * @param finishListener
     * @param bufferPool
     */
    public ChunkedStreamSinkChannel(final StreamSinkChannel delegate, final boolean configurable, final boolean passClose, final ChannelListener<? super ChunkedStreamSinkChannel> finishListener, final Pool<ByteBuffer> bufferPool) {
        this.delegate = delegate;
        this.finishListener = finishListener;
        this.bufferPool = bufferPool;
        config = (configurable ? CONF_FLAG_CONFIGURABLE : 0) | (passClose ? CONF_FLAG_PASS_CLOSE : 0);
        delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener(this, writeSetter));
    }


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
            } while (!stateUpdater.compareAndSet(this, oldVal, newVal));
            return oldVal;
        } finally {
            if (intr) currentThread.interrupt();
        }
    }

    private void exit(int oldVal, int enterFlag, final int setFlags) {
        int newVal = oldVal & ~enterFlag | setFlags;
        while (!stateUpdater.compareAndSet(this, oldVal, newVal)) {
            oldVal = state;
            newVal = oldVal & ~enterFlag | setFlags;
        }
        if (allAreClear(newVal, FLAG_WRITING_CHUNKED | FLAG_CLOSING_ASYNC) && pooledBuffer != null) {
            this.pooledBuffer.free();
            this.pooledBuffer = null;
        }
        if (anyAreSet(enterFlag, FLAG_WRITING_CHUNKED)) {
            safeUnpark(waiterUpdater.getAndSet(this, null));
        }
        safeUnpark(lockWaiterUpdater.getAndSet(this, null));

    }

    @Override
    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    @Override
    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        int val = enter(FLAG_IN_WRITE, 0, FLAG_CLOSE_REQ | FLAG_WRITING_CHUNKED, 0);
        if (anyAreSet(val, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }

        //if we are currently doing a background write of chunked data
        //we just return 0
        int clearFlags = 0;
        int exitFlag = 0;
        if (!continueWrite(val)) {
            return 0;
        } else {
            clearFlags = FLAG_WRITING_CHUNKED;
        }
        try {

            if (!src.hasRemaining()) {
                //don't write an empty chunk
                return 0;
            }

            //get our chunking header
            final Pooled<ByteBuffer> buffer = this.pooledBuffer = this.bufferPool.allocate();
            final ByteBuffer buff = buffer.getResource();

            //this is the most we can write
            //we need to limit the maximum size, as we need to make sure we can always
            //fit a chunk into our buffer
            final int maxSize = buff.capacity() - CHUNKING_OVERHEAD_MAX_BYTES;
            final int toWrite = Math.min(src.remaining(), maxSize);

            buff.clear();
            buff.put(Integer.toHexString(toWrite).getBytes());
            buff.put(CRLF);
            buff.flip();
            //now we try and write it
            writeBuffer(buff);
            if (toWrite != src.remaining()) {
                if (log.isTraceEnabled()) {
                    log.tracef("Copying into our buffer, as src size of %s was bigger than %s", src.remaining(), maxSize);
                }
                //our initial write was fully written out, but
                //the buffer they passed in was bigger than our buffer. This is an issue, because it means
                //that we can't just call delegate.write(), as it could write out more than what we have
                //specified as the chunk size (note that we can't just use src.remaining() as the chunk
                // size, as if it failed it might not fit into our buffer, we could simply
                //use multiple buffers, but it has its own drawbacks) instead we are going to
                //just copy what we need into our buffer, and then attempt to write it out
                //this is much less efficient

                //we compact rather than clearing here as the first write may not have finished
                buff.compact();
                for (int i = 0; i < toWrite; ++i) {
                    buff.put(src.get());
                }
                buff.put(CRLF);
                buff.flip();
                writeBuffer(buff);
                if (buff.hasRemaining()) {
                    exitFlag = FLAG_WRITING_CHUNKED;
                }
            } else {
                if (buff.hasRemaining()) {
                    if (log.isTraceEnabled()) {
                        log.tracef("Copying into our buffer, as initial write of chunk size did not complete");
                    }
                    buff.compact();
                } else {
                    if (log.isTraceEnabled()) {
                        log.tracef("Attempting to write out source buffer directly");
                    }
                    //the initial write completed, and the buffer they have given us will fit into our
                    //buffer, we can attempt to write the whole thing out.
                    writeBuffer(src);
                    if (!src.hasRemaining()) {
                        //we are done except for the CRLF
                        buff.clear();
                        buff.put(CRLF);
                        buff.flip();
                        writeBuffer(buff);
                        if (buff.hasRemaining()) {
                            exitFlag = FLAG_WRITING_CHUNKED;
                        }
                        return toWrite;
                    }
                }
                //we know we will fit at this point, so just stuff the remaining bytes in the buffer
                buff.put(src);
                buff.put(CRLF);
                //ok, we have our buffer.
                buff.flip();
                exitFlag = FLAG_WRITING_CHUNKED;
            }
            return toWrite;
        } finally {
            exit(val, FLAG_IN_WRITE | clearFlags, exitFlag);
        }
    }

    /**
     * writes a buffer in a loop
     *
     * @param buff The buffer
     */
    private void writeBuffer(final ByteBuffer buff) throws IOException {
        int c;
        do {
            c = delegate.write(buff);
        } while (c != 0 && buff.hasRemaining());
    }

    @Override
    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        for (int i = offset; i < length; ++i) {
            if (srcs[i].hasRemaining()) {
                return write(srcs[i]);
            }
        }
        return 0;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (anyAreSet(state, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }
        return src.transferTo(position, count, this);
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (anyAreSet(state, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }
        return IoUtils.transfer(source, count, throughBuffer, this);
    }

    @Override
    public boolean flush() throws IOException {
        int val = enter(FLAG_IN, 0, FLAG_CLOSE_DONE, 0);
        if (allAreSet(val, FLAG_CLOSE_DONE)) {
            return true;
        }
        int setFlags = 0;
        int clearFlags = 0;
        try {
            if (!continueWrite(val)) {
                return false;
            } else {
                clearFlags = FLAG_WRITING_CHUNKED | FLAG_CLOSING_ASYNC;
            }
            if (allAreSet(config, CONF_FLAG_PASS_CLOSE) && allAreSet(val, FLAG_CLOSE_REQ) && allAreClear(val, FLAG_CLOSE_SENT)) {
                setFlags |= FLAG_CLOSE_SENT;
                delegate.shutdownWrites();
            }
            boolean flushed = delegate.flush();
            if (flushed && anyAreSet(val, FLAG_CLOSE_REQ) && anyAreClear(val, FLAG_FINISH)) {
                ChannelListeners.invokeChannelListener(this, finishListener);
                setFlags |= FLAG_FINISH;
            }
            if (flushed && anyAreSet(val | setFlags, FLAG_CLOSE_SENT)) {
                delegate.suspendWrites();
                delegate.getWriteSetter().set(null);
                setFlags |= FLAG_CLOSE_DONE;
            }
            return flushed;
        } finally {
            exit(val, FLAG_IN | clearFlags, setFlags);
        }
    }

    @Override
    public void suspendWrites() {
        int val = enter(FLAG_IN, FLAG_RESUME, FLAG_CLOSE_DONE, FLAG_RESUME);
        if (anyAreSet(val, FLAG_CLOSE_DONE)) {
            return;
        }
        if (allAreClear(val, FLAG_RESUME)) {
            return;
        }
        try {
            delegate.suspendWrites();
        } finally {
            exit(val, FLAG_IN, 0);
        }
    }

    @Override
    public void resumeWrites() {
        int val = enter(FLAG_IN | FLAG_RESUME, 0, FLAG_CLOSE_DONE | FLAG_RESUME, 0);
        if (anyAreSet(val, FLAG_CLOSE_DONE | FLAG_RESUME)) {
            return;
        }
        try {
            delegate.resumeWrites();
        } finally {
            exit(val, FLAG_IN, 0);
        }
    }

    @Override
    public boolean isWriteResumed() {
        final int state = this.state;
        return allAreSet(state, FLAG_RESUME) && allAreClear(state, FLAG_CLOSE_DONE);
    }

    @Override
    public void wakeupWrites() {
        int val = enter(FLAG_IN | FLAG_RESUME, 0, FLAG_CLOSE_DONE | FLAG_RESUME, 0);
        if (anyAreSet(val, FLAG_CLOSE_DONE | FLAG_RESUME)) {
            return;
        }
        try {
            delegate.wakeupWrites();
        } finally {
            exit(val, FLAG_IN, 0);
        }
    }

    @Override
    public void shutdownWrites() throws IOException {
        int val = enter(FLAG_IN | FLAG_CLOSE_REQ, 0, FLAG_CLOSE_REQ, 0);
        if (allAreSet(val, FLAG_CLOSE_REQ)) {
            return;
        }
        int setFlags = 0;
        int clearFlags = 0;
        try {
            setFlags |= FLAG_CLOSE_SENT;
            //we pass the closing async flag here to make it attempt
            //to write out the last chunk
            if (continueWrite(val | FLAG_CLOSING_ASYNC)) {
                delegate.suspendWrites();
                delegate.getWriteSetter().set(null);
                if (allAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                    delegate.shutdownWrites();
                }
                clearFlags |= FLAG_WRITING_CHUNKED;
            } else {
                //we still need to write some stuff out
                //the user is going to need to flush a bit more
                setFlags |= FLAG_CLOSING_ASYNC;
            }
        } finally {
            exit(val, FLAG_IN | clearFlags, setFlags);
        }
    }

    @Override
    public void close() throws IOException {
        int val = enter(FLAG_IN | FLAG_CLOSE_REQ | FLAG_CLOSE_SENT | FLAG_CLOSE_DONE, 0, FLAG_CLOSE_DONE, 0);
        int setFlags = 0;
        try {
            if (allAreSet(val, FLAG_CLOSE_DONE)) {
                return;
            }
            if (anyAreSet(val, FLAG_WRITING_CHUNKED | FLAG_CLOSING_ASYNC) || anyAreClear(val, FLAG_CLOSE_REQ | FLAG_FINISH)) {
                throw UndertowMessages.MESSAGES.closeCalledWithDataStillToBeFlushed();
            }
            delegate.suspendWrites();
            delegate.getWriteSetter().set(null);
            if (allAreSet(config, CONF_FLAG_PASS_CLOSE)) {
                delegate.close();
            }
        } finally {
            exit(val, FLAG_IN, setFlags);
            invokeChannelListener(this, closeSetter.get());
        }
    }

    @Override
    public void awaitWritable() throws IOException {
        if (anyAreSet(state, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }
        delegate.awaitWritable();
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        if (anyAreSet(state, FLAG_CLOSE_REQ)) {
            throw new ClosedChannelException();
        }
        delegate.awaitWritable(time, timeUnit);
    }

    @Override
    public boolean isOpen() {
        return allAreClear(state, FLAG_CLOSE_DONE);
    }

    @Override
    public boolean supportsOption(final Option<?> option) {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) && delegate.supportsOption(option);
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) ? delegate.getOption(option) : null;
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return allAreSet(config, CONF_FLAG_CONFIGURABLE) ? delegate.setOption(option, value) : null;
    }

    private static void safeUnpark(final Thread waiter) {
        if (waiter != null) unpark(waiter);
    }


    /**
     * If a write is in progress this will continue to write out the data in the buffer.
     * <p/>
     * If it has not finished writing then it will return false, otherwise it will return true.
     * If it returns true then it is the callers responsibility to clear the {@link #FLAG_WRITING_CHUNKED}
     * flag on exit.
     *
     * @param flags The flags that were returned from the enter method
     * @return <code>true</code> If the write is completed or no write was nessesary
     */
    private boolean continueWrite(int flags) throws IOException {
        //we are not in the process of writing out chunked data
        //we simply delegate to the underlying listener
        Pooled<ByteBuffer> pooledBuffer = ChunkedStreamSinkChannel.this.pooledBuffer;
        if (allAreClear(flags, FLAG_WRITING_CHUNKED) && anyAreSet(flags, FLAG_CLOSING_ASYNC)) {
            //we have to write out the last chunk before we close
            //for real

            //we are responsible for queing up the last chunk. If FLAG_CLOSING_ASYNC is specified
            //but the buffer is null it means that we need to allocate a buffer and write the last chunk
            if (pooledBuffer == null) {
                ChunkedStreamSinkChannel.this.pooledBuffer = pooledBuffer = bufferPool.allocate();
                ByteBuffer buffer = pooledBuffer.getResource();
                buffer.clear();
                buffer.put(LAST_CHUNK);
                buffer.flip();
            }
            final ByteBuffer buffer = pooledBuffer.getResource();
            writeBuffer(buffer);
            return !buffer.hasRemaining();
        } else if (anyAreSet(flags, FLAG_WRITING_CHUNKED)) {
            final ByteBuffer buffer = pooledBuffer.getResource();
            int c;
            do {
                c = delegate.write(buffer);
            } while (buffer.hasRemaining() && c > 0);
            if (!buffer.hasRemaining() && anyAreSet(flags, FLAG_CLOSING_ASYNC)) {
                //we need to start writing the last chunk
                buffer.clear();
                buffer.put(LAST_CHUNK);
                buffer.flip();
                do {
                    c = delegate.write(buffer);
                } while (buffer.hasRemaining() && c > 0);
            }
            return !buffer.hasRemaining();
        }
        return true;
    }

}
