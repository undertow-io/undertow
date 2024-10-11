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
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.server.OpenListener;
import io.undertow.util.WorkerUtils;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper for read timeout. This should always be the first wrapper applied to the underlying channel.
 *
 * @author Stuart Douglas
 * @see org.xnio.Options#READ_TIMEOUT
 */
public final class ReadTimeoutStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private volatile XnioExecutor.Key handle;
    private final StreamConnection connection;
    private volatile long expireTime = -1;
    private final OpenListener openListener;

    private static final int FUZZ_FACTOR = 50; //we add 50ms to the timeout to make sure the underlying channel has actually timed out
    private volatile boolean expired;

    private final Runnable timeoutCommand = new Runnable() {
        @Override
        public void run() {
            synchronized (ReadTimeoutStreamSourceConduit.this) {
                handle = null;
            }
            if (expireTime == -1 || !connection.isOpen()) {
                return;
            }
            long current = System.currentTimeMillis();
            if (current  < expireTime) {
                //timeout has been bumped, re-schedule
                if (handle == null) {
                    synchronized (ReadTimeoutStreamSourceConduit.this) {
                        if (handle == null)
                            handle = WorkerUtils.executeAfter(connection.getIoThread(), timeoutCommand, (expireTime - current) + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
                    }
                }
                return;
            }
            UndertowLogger.REQUEST_LOGGER.tracef("Timing out channel %s due to inactivity", connection.getSourceChannel());
            synchronized (ReadTimeoutStreamSourceConduit.this) {
                expired = true;
            }
            boolean readResumed = connection.getSourceChannel().isReadResumed();
            ChannelListener<? super ConduitStreamSourceChannel> readListener = connection.getSourceChannel().getReadListener();

            if (readResumed) {
                ChannelListeners.invokeChannelListener(connection.getSourceChannel(), readListener);
            }
            if (connection.getSinkChannel().isWriteResumed()) {
                ChannelListeners.invokeChannelListener(connection.getSinkChannel(), connection.getSinkChannel().getWriteListener());
            }
            // close only after invoking listeners, to allow space for listener getting ReadTimeoutException
            IoUtils.safeClose(connection);
        }
    };

    public ReadTimeoutStreamSourceConduit(final StreamSourceConduit delegate, StreamConnection connection, OpenListener openListener) {
        super(delegate);
        this.connection = connection;
        this.openListener = openListener;
        final ReadReadyHandler handler = new ReadReadyHandler.ChannelListenerHandler<>(connection.getSourceChannel());
        delegate.setReadReadyHandler(new ReadReadyHandler() {
            @Override
            public void readReady() {
                handler.readReady();
            }

            @Override
            public void forceTermination() {
                cleanup();
                handler.forceTermination();
            }

            @Override
            public void terminated() {
                cleanup();
                handler.terminated();
            }
        });
    }

    private void handleReadTimeout(final long ret) throws IOException {
        if (!connection.isOpen()) {
            cleanup();
            return;
        }
        if (ret == -1) {
            cleanup();
            return;
        }
        Integer timeout = getTimeout();
        if (timeout == null || timeout <= 0) {
            return;
        }
        final long currentTime = System.currentTimeMillis();
        if (ret == 0) {
            final long expireTimeVar = expireTime;
            if (expireTimeVar != -1 && currentTime > expireTimeVar) {
                IoUtils.safeClose(connection);
                throw UndertowMessages.MESSAGES.readTimedOut(currentTime - (expireTimeVar - this.getTimeout()));
            }
        }
        expireTime = currentTime + timeout;
        if (handle == null) {
            synchronized (this) {
                if (handle == null)
                    handle = connection.getIoThread().executeAfter(timeoutCommand, timeout, TimeUnit.MILLISECONDS);
            }

        }
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        checkExpired();
        long ret = super.transferTo(position, count, target);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        checkExpired();
        long ret = super.transferTo(count, throughBuffer, target);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        checkExpired();
        long ret = super.read(dsts, offset, length);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        checkExpired();
        int ret = super.read(dst);
        handleReadTimeout(ret);
        return ret;
    }

    @Override
    public void awaitReadable() throws IOException {
        checkExpired();
        Integer timeout = getTimeout();
        if (timeout != null && timeout > 0) {
            super.awaitReadable(timeout + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
        } else {
            super.awaitReadable();
        }
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        checkExpired();
        Integer timeout = getTimeout();
        if (timeout != null && timeout > 0) {
            long millis = timeUnit.toMillis(time);
            super.awaitReadable(Math.min(millis, timeout + FUZZ_FACTOR), TimeUnit.MILLISECONDS);
        } else {
            super.awaitReadable(time, timeUnit);
        }
    }

    private Integer getTimeout() {
        Integer timeout = 0;
        try {
            timeout = connection.getSourceChannel().getOption(Options.READ_TIMEOUT);
        } catch (IOException ignore) {
            // should never happen
        }
        Integer idleTimeout = openListener.getUndertowOptions().get(UndertowOptions.IDLE_TIMEOUT);
        if ((timeout == null || timeout <= 0) && idleTimeout != null) {
            timeout = idleTimeout;
        } else if (timeout != null && idleTimeout != null && idleTimeout > 0) {
            timeout = Math.min(timeout, idleTimeout);
        }
        return timeout;
    }

    @Override
    public void resumeReads() {
        super.resumeReads();
        if (handle == null) {
            try {
                handleReadTimeout(1);
            } catch (IOException e) {
                // impossible as 1 is passed
            }
        }
    }

    @Override
    public void terminateReads() throws IOException {
        checkExpired();
        super.terminateReads();
        cleanup();
    }

    private void cleanup() {
        if (handle != null) {
            synchronized (this) {
                if (handle != null) {
                    handle.remove();
                    handle = null;
                    expireTime = -1;
                }
            }
        }
    }

    @Override
    public void suspendReads() {
        super.suspendReads();
        cleanup();
    }

    private void checkExpired() throws ReadTimeoutException {
        synchronized (this) {
            if (expired) {
                throw UndertowMessages.MESSAGES.readTimedOut(System.currentTimeMillis() - (expireTime - getTimeout()));
            }
        }
    }

    public String toString() {
        return super.toString() + " (next: " + next + ")";
    }
}
