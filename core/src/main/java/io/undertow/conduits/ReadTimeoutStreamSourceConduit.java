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

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.server.OpenListener;

import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper for read timeout. This should always be the first wrapper applied to the underlying channel.
 *
 * @author Stuart Douglas
 * @see org.xnio.Options#READ_TIMEOUT
 */
public final class ReadTimeoutStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    /** Queue for enforcing deletion of timeouts for requests that are completed
     */
    private static final PhantomReferenceQueue timeoutDeletionQueue = new PhantomReferenceQueue();

    private final StreamConnection connection;
    private volatile long expireTime = -1;
    private final OpenListener openListener;

    private static final int FUZZ_FACTOR = 50; //we add 50ms to the timeout to make sure the underlying channel has actually timed out

    private final TimeoutCommand timeoutCommand = new TimeoutCommand(this);

    public ReadTimeoutStreamSourceConduit(final StreamSourceConduit delegate, StreamConnection connection, OpenListener openListener) {
        super(delegate);
        this.connection = connection;
        this.openListener = openListener;
    }

    private void handleReadTimeout(final long ret) throws IOException {
        if (!connection.isOpen()) {
            if(timeoutCommand.handle != null) {
               timeoutCommand.handle.remove();
               timeoutCommand.handle = null;
            }
            return;
        }
        if(ret == -1) {
            if(timeoutCommand.handle != null) {
               timeoutCommand.handle.remove();
               timeoutCommand.handle = null;
            }
            return;
        }
        if (ret == 0 && timeoutCommand.handle != null) {
            return;
        }
        Integer timeout = getTimeout();
        if (timeout == null || timeout <= 0) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        long expireTimeVar = expireTime;
        if (expireTimeVar != -1 && currentTime > expireTimeVar) {
            IoUtils.safeClose(connection);
            throw new ClosedChannelException();
        }
        expireTime = currentTime + timeout;
        XnioExecutor.Key key = timeoutCommand.handle;
        if (key == null) {
           timeoutCommand.handle = connection.getIoThread().executeAfter(timeoutCommand, timeout, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        long ret = super.transferTo(position, count, target);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        long ret = super.transferTo(count, throughBuffer, target);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        long ret = super.read(dsts, offset, length);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        int ret = super.read(dst);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public void awaitReadable() throws IOException {
        Integer timeout = getTimeout();
        if (timeout != null && timeout > 0) {
            super.awaitReadable(timeout + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
        } else {
            super.awaitReadable();
        }
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        Integer timeout = getTimeout();
        if (timeout != null && timeout > 0) {
            long millis = timeUnit.toMillis(time);
            super.awaitReadable(Math.min(millis, timeout + FUZZ_FACTOR), TimeUnit.MILLISECONDS);
        } else {
            super.awaitReadable(time, timeUnit);
        }
    }

    private Integer getTimeout() throws IOException {
        Integer timeout = connection.getSourceChannel().getOption(Options.READ_TIMEOUT);
        Integer idleTimeout = openListener.getUndertowOptions().get(UndertowOptions.IDLE_TIMEOUT);
        if ((timeout == null || timeout <= 0) && idleTimeout != null) {
            timeout = idleTimeout;
        } else if (timeout != null && idleTimeout != null && idleTimeout > 0) {
            timeout = Math.min(timeout, idleTimeout);
        }
        return timeout;
    }

    @Override
    public void terminateReads() throws IOException {
        super.terminateReads();
        if(timeoutCommand.handle != null) {
           timeoutCommand.handle.remove();
           timeoutCommand.handle = null;
        }
    }

    /** Queue for enforcing deletion of the timeout commands from the delay
     * work queue after the conduit itself is GCd.
     *
     * This is functionally a quazi-finalize.  Finalize is expensive.  Phantom
     * references are expected to be less so.
     */
    private static class PhantomReferenceQueue extends ReferenceQueue<ReadTimeoutStreamSourceConduit>
    {
        public PhantomReferenceQueue() {
            startMonitoring();
        }

        private void startMonitoring() {
            Runnable doRun = new Runnable() {
                @Override
                public void run() {
                    while(true) {
                        // Paranoid try/catch
                        try {
                            Reference<? extends ReadTimeoutStreamSourceConduit> r = null;
                            r = remove();
                            if(r instanceof TimeoutReference) {
                                TimeoutReference ref = (TimeoutReference) r;
                                ref.clearTimeout();
                            }
                        } catch (Throwable err) {
                            err.printStackTrace();
                        }
                    }
                }
            };

            //TODO: Integrate this properly with Undertow/Wildfly threading
            // Instead of a new thread, one might try slipping a call to poll()
            // into an appropriate spot, but I see there being threading
            // concerns with that.
            Thread runner = new Thread(doRun, "Undertow-Timeout-Cleanup");
            runner.setDaemon(true);
            runner.start();
        }
    }

    /** Tie a reference of the TimeoutCommand to the phantom reference
     * for post-GC cleanup of the timeout
     */
    public static class TimeoutReference extends PhantomReference<ReadTimeoutStreamSourceConduit>
    {
        /* This is not aiming to delay the normal GC process any,
         * only to put extra effort into things that don't clean up by
         * themselves - use weak references as we don't need anything stronger.
         */

        protected WeakReference<TimeoutCommand> commandRef;

        public TimeoutReference(ReadTimeoutStreamSourceConduit owner,
                PhantomReferenceQueue timeoutQueue, TimeoutCommand command) {
            super(owner, timeoutQueue);
            commandRef = new WeakReference<ReadTimeoutStreamSourceConduit.TimeoutCommand>(command);
        }

        public void clearTimeout() {
            TimeoutCommand command = commandRef.get();
            if(command != null) {
                XnioExecutor.Key handle = command.handle;
                if(handle != null) {
                    handle.remove();
                }
            }
        }
    }

    /** Implement as static so we can use a weak reference for the
     * associated conduit, allowing it to GC even while the timeout is still
     * queued. */
    public static class TimeoutCommand implements Runnable {

        /* Memory/GC notes:
         * Conduit is held with a strong reference by anything that uses it.
         * TimeoutCommand is held with a strong reference by the conduit and the
         * delay work queue.
         * For post GC cleanup (delete the timeout command from the delay work queue):
         * TimeoutCommand holds a strong reference to a phantom reference to the conduit.
         * The phantom reference also holds a weak reference back to the timeout command -
         * if the TimeoutCommand is GCd, the phantom reference can also be GCd before any
         * active cleanup is performed.
         * When the Conduit is GCd, if the phantom reference still exists it is added to the
         * PhanomReferenceQueue, which invokes handle.remove() should the handle still be valid.
         */

        /** Weak reference to the conduit that this TimeoutCommand is for - keep it weak
         * so that we don't hold the conduit in memory */
        WeakReference<ReadTimeoutStreamSourceConduit> owner;

        private XnioExecutor.Key handle;

        /* Hold on to the phantom reference needed to release us from the event
         * queue - if we get freed from the queue, the reference is cleared also
         * and extra action can be avoided.  If we arn't cleared up when the conduit
         * is collected, the phantom reference is picked up by the
         * PhantomReferenceQueue and removes us from the schedule.
         *
         * Paranoid:
         *   Give the timeout reference protected scope so optimizers don't
         *   try to optimize the reference away - the reference must only be
         *   GCd after this the TimeoutCommand is eligible for GC.
         */
        protected TimeoutReference reference;

        private TimeoutCommand(ReadTimeoutStreamSourceConduit owner) {
            this.owner = new WeakReference<ReadTimeoutStreamSourceConduit>(owner);
            reference = new TimeoutReference(owner, timeoutDeletionQueue, this);
        }

        @Override
        public void run() {
            ReadTimeoutStreamSourceConduit owner = this.owner.get();
            if(owner == null) {
                return;
            }

            handle = null;
            if (owner.expireTime == -1) {
                return;
            }
            long current = System.currentTimeMillis();
            if (current  < owner.expireTime) {
                //timeout has been bumped, re-schedule
                handle = owner.connection.getIoThread().executeAfter(owner.timeoutCommand, (owner.expireTime - current) + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
                return;
            }
            UndertowLogger.REQUEST_LOGGER.tracef("Timing out channel %s due to inactivity");
            IoUtils.safeClose(owner.connection);
            if (owner.connection.getSourceChannel().isReadResumed()) {
                ChannelListeners.invokeChannelListener(owner.connection.getSourceChannel(), owner.connection.getSourceChannel().getReadListener());
            }
            if (owner.connection.getSinkChannel().isWriteResumed()) {
                ChannelListeners.invokeChannelListener(owner.connection.getSinkChannel(), owner.connection.getSinkChannel().getWriteListener());
            }
        }
    }


}
