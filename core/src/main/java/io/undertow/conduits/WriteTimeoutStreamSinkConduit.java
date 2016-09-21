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

import org.xnio.Buffers;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper for write timeout. This should always be the first wrapper applied to the underlying channel.
 *
 * @author Stuart Douglas
 * @see org.xnio.Options#WRITE_TIMEOUT
 */
public final class WriteTimeoutStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private XnioExecutor.Key handle;
    private final StreamConnection connection;
    private volatile long expireTime = -1;
    private final OpenListener openListener;

    private static final int FUZZ_FACTOR = 50; //we add 50ms to the timeout to make sure the underlying channel has actually timed out

    private final Runnable timeoutCommand = new Runnable() {
        @Override
        public void run() {
            handle = null;
            if (expireTime == -1) {
                return;
            }
            long current = System.currentTimeMillis();
            if (current  < expireTime) {
                //timeout has been bumped, re-schedule
                handle = connection.getIoThread().executeAfter(timeoutCommand, (expireTime - current) + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
                return;
            }
            UndertowLogger.REQUEST_LOGGER.tracef("Timing out channel %s due to inactivity", connection.getSinkChannel());
            IoUtils.safeClose(connection);
            if (connection.getSourceChannel().isReadResumed()) {
                ChannelListeners.invokeChannelListener(connection.getSourceChannel(), connection.getSourceChannel().getReadListener());
            }
            if (connection.getSinkChannel().isWriteResumed()) {
                ChannelListeners.invokeChannelListener(connection.getSinkChannel(), connection.getSinkChannel().getWriteListener());
            }
        }
    };

    public WriteTimeoutStreamSinkConduit(final StreamSinkConduit delegate, StreamConnection connection, OpenListener openListener) {
        super(delegate);
        this.connection = connection;
        this.openListener = openListener;
    }

    private void handleWriteTimeout(final long ret) throws IOException {
        if (!connection.isOpen()) {
            return;
        }
        if (ret == 0 && handle != null) {
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
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        int ret = super.write(src);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        long ret = super.write(srcs, offset, length);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        int ret = super.writeFinal(src);
        handleWriteTimeout(ret);
        if(!src.hasRemaining()) {
            if(handle != null) {
                handle.remove();
                handle = null;
            }
        }
        return ret;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long ret = super.writeFinal(srcs, offset, length);
        handleWriteTimeout(ret);
        if(!Buffers.hasRemaining(srcs)) {
            if(handle != null) {
                handle.remove();
                handle = null;
            }
        }
        return ret;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        long ret = super.transferFrom(src, position, count);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        long ret = super.transferFrom(source, count, throughBuffer);
        handleWriteTimeout(ret);
        return ret;
    }

    @Override
    public void awaitWritable() throws IOException {
        Integer timeout = getTimeout();
        if (timeout != null && timeout > 0) {
            super.awaitWritable(timeout + FUZZ_FACTOR, TimeUnit.MILLISECONDS);
        } else {
            super.awaitWritable();
        }
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        Integer timeout = getTimeout();
        if (timeout != null && timeout > 0) {
            long millis = timeUnit.toMillis(time);
            super.awaitWritable(Math.min(millis, timeout + FUZZ_FACTOR), TimeUnit.MILLISECONDS);
        } else {
            super.awaitWritable(time, timeUnit);
        }
    }

    private Integer getTimeout() {
        Integer timeout = 0;
        try {
            timeout = connection.getSourceChannel().getOption(Options.WRITE_TIMEOUT);
        } catch (IOException ignore) {
            // should never happen, ignoring
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
    public void terminateWrites() throws IOException {
        super.terminateWrites();
        if(handle != null) {
            handle.remove();
            handle = null;
        }
    }

    @Override
    public void truncateWrites() throws IOException {
        super.truncateWrites();
        if(handle != null) {
            handle.remove();
            handle = null;
        }
    }

    @Override
    public void resumeWrites() {
        super.resumeWrites();
        handleResumeTimeout();
    }

    @Override
    public void suspendWrites() {
        super.suspendWrites();
        XnioExecutor.Key handle = this.handle;
        if(handle != null) {
            handle.remove();
            this.handle = null;
        }
    }

    @Override
    public void wakeupWrites() {
        super.wakeupWrites();
        handleResumeTimeout();
    }

    private void handleResumeTimeout() {
        Integer timeout = getTimeout();
        if (timeout == null || timeout <= 0) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        expireTime = currentTime + timeout;
        XnioExecutor.Key key = handle;
        if (key == null) {
            handle = connection.getIoThread().executeAfter(timeoutCommand, timeout, TimeUnit.MILLISECONDS);
        }
    }
}
