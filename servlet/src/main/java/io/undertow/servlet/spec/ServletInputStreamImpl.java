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

package io.undertow.servlet.spec;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.EmptyStreamSourceChannel;
import org.xnio.channels.StreamSourceChannel;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.servlet.UndertowServletMessages;

/**
 * Servlet input stream implementation. This stream is non-buffered, and is used for both
 * HTTP requests and for upgraded streams.
 *
 * @author Stuart Douglas
 */
public class ServletInputStreamImpl extends ServletInputStream {

    private final HttpServletRequestImpl request;
    private final StreamSourceChannel channel;
    private final ByteBufferPool bufferPool;

    private volatile ReadListener listener;
    private volatile ServletInputStreamChannelListener internalListener;

    /**
     * If this stream is ready for a read
     */
    private static final int FLAG_READY = 1;
    private static final int FLAG_CLOSED = 1 << 1;
    private static final int FLAG_FINISHED = 1 << 2;
    private static final int FLAG_ON_DATA_READ_CALLED = 1 << 3;
    private static final int FLAG_CALL_ON_ALL_DATA_READ = 1 << 4;
    private static final int FLAG_BEING_INVOKED_IN_IO_THREAD = 1 << 5;
    private static final int FLAG_IS_READY_CALLED = 1 << 6;

    private volatile int state;
    private volatile AsyncContextImpl asyncContext;
    private volatile PooledByteBuffer pooled;
    private volatile boolean asyncIoStarted;

    private static final AtomicIntegerFieldUpdater<ServletInputStreamImpl> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ServletInputStreamImpl.class, "state");

    public ServletInputStreamImpl(final HttpServletRequestImpl request) {
        this.request = request;
        if (request.getExchange().isRequestChannelAvailable()) {
            this.channel = request.getExchange().getRequestChannel();
        } else {
            this.channel = new EmptyStreamSourceChannel(request.getExchange().getIoThread());
        }
        this.bufferPool = request.getExchange().getConnection().getByteBufferPool();
    }


    @Override
    public boolean isFinished() {
        return anyAreSet(state, FLAG_FINISHED);
    }

    @Override
    public boolean isReady() {
        if (!asyncContext.isInitialRequestDone()) {
            return false;
        }
        boolean finished = anyAreSet(state, FLAG_FINISHED);
        if(finished) {
            if (anyAreClear(state, FLAG_ON_DATA_READ_CALLED)) {
                if(allAreClear(state, FLAG_BEING_INVOKED_IN_IO_THREAD)) {
                    setFlags(FLAG_ON_DATA_READ_CALLED);
                    request.getServletContext().invokeOnAllDataRead(request.getExchange(), listener);
                } else {
                    setFlags(FLAG_CALL_ON_ALL_DATA_READ);
                }
            }
        }
        if (!asyncIoStarted) {
            //make sure we don't call resumeReads unless we have started async IO
            return false;
        }
        boolean ready = anyAreSet(state, FLAG_READY) && !finished;
        if(!ready && listener != null && !finished) {
            channel.resumeReads();
        }
        if(ready) {
            setFlags(FLAG_IS_READY_CALLED);
        }
        return ready;
    }

    @Override
    public void setReadListener(final ReadListener readListener) {
        if (readListener == null) {
            throw UndertowServletMessages.MESSAGES.listenerCannotBeNull();
        }
        if (listener != null) {
            throw UndertowServletMessages.MESSAGES.listenerAlreadySet();
        }
        if (!request.isAsyncStarted()) {
            throw UndertowServletMessages.MESSAGES.asyncNotStarted();
        }

        asyncContext = request.getAsyncContext();
        listener = readListener;
        channel.getReadSetter().set(internalListener = new ServletInputStreamChannelListener());

        //we resume from an async task, after the request has been dispatched
        asyncContext.addAsyncTask(new Runnable() {
            @Override
            public void run() {
                channel.getIoThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        asyncIoStarted = true;
                        internalListener.handleEvent(channel);
                    }
                });
            }
        });
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int read = read(b);
        if (read == -1) {
            return -1;
        }
        return b[0] & 0xff;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        if (listener != null) {
            if (anyAreClear(state, FLAG_READY | FLAG_IS_READY_CALLED) ) {
                throw UndertowServletMessages.MESSAGES.streamNotReady();
            }
            clearFlags(FLAG_IS_READY_CALLED);
        } else {
            readIntoBuffer();
        }
        if (anyAreSet(state, FLAG_FINISHED)) {
            return -1;
        }
        if (len == 0) {
            return 0;
        }
        ByteBuffer buffer = pooled.getBuffer();
        int copied = Math.min(buffer.remaining(), len);
        buffer.get(b, off, copied);
        if (!buffer.hasRemaining()) {
            pooled.close();
            pooled = null;
            if (listener != null) {
                readIntoBufferNonBlocking();
            }
        }
        return copied;
    }

    private void readIntoBuffer() throws IOException {
        if (pooled == null && !anyAreSet(state, FLAG_FINISHED)) {
            pooled = bufferPool.allocate();

            int res = Channels.readBlocking(channel, pooled.getBuffer());
            pooled.getBuffer().flip();
            if (res == -1) {
                setFlags(FLAG_FINISHED);
                pooled.close();
                pooled = null;
            }
        }
    }

    private void readIntoBufferNonBlocking() throws IOException {
        if (pooled == null && !anyAreSet(state, FLAG_FINISHED)) {
            pooled = bufferPool.allocate();
            if (listener == null) {
                int res = channel.read(pooled.getBuffer());
                if (res == 0) {
                    pooled.close();
                    pooled = null;
                    return;
                }
                pooled.getBuffer().flip();
                if (res == -1) {
                    setFlags(FLAG_FINISHED);
                    pooled.close();
                    pooled = null;
                }
            } else {
                int res = channel.read(pooled.getBuffer());
                pooled.getBuffer().flip();
                if (res == -1) {
                    setFlags(FLAG_FINISHED);
                    pooled.close();
                    pooled = null;
                } else if (res == 0) {
                    clearFlags(FLAG_READY);
                    pooled.close();
                    pooled = null;
                }
            }
        }
    }

    @Override
    public int available() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        readIntoBufferNonBlocking();
        if (anyAreSet(state, FLAG_FINISHED)) {
            return 0;
        }
        if (pooled == null) {
            return 0;
        }
        return pooled.getBuffer().remaining();
    }

    @Override
    public void close() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            return;
        }
        setFlags(FLAG_CLOSED);
        try {
            while (allAreClear(state, FLAG_FINISHED)) {
                readIntoBuffer();
                if (pooled != null) {
                    pooled.close();
                    pooled = null;
                }
            }
        } finally {
            setFlags(FLAG_FINISHED);
            if (pooled != null) {
                pooled.close();
                pooled = null;
            }
            channel.shutdownReads();
        }
    }

    private class ServletInputStreamChannelListener implements ChannelListener<StreamSourceChannel> {
        @Override
        public void handleEvent(final StreamSourceChannel channel) {
            try {
                if (asyncContext.isDispatched()) {
                    //this is no longer an async request
                    //we just return
                    //TODO: what do we do here? Revert back to blocking mode?
                    channel.suspendReads();
                    return;
                }
                if (anyAreSet(state, FLAG_FINISHED)) {
                    channel.suspendReads();
                    return;
                }
                readIntoBufferNonBlocking();
                if (pooled != null) {
                    channel.suspendReads();
                    setFlags(FLAG_READY);
                    if (!anyAreSet(state, FLAG_FINISHED)) {
                        setFlags(FLAG_BEING_INVOKED_IN_IO_THREAD);
                        try {
                            request.getServletContext().invokeOnDataAvailable(request.getExchange(), listener);
                        } finally {
                            clearFlags(FLAG_BEING_INVOKED_IN_IO_THREAD);
                        }
                        if(anyAreSet(state, FLAG_CALL_ON_ALL_DATA_READ) && allAreClear(state, FLAG_ON_DATA_READ_CALLED)) {
                            setFlags(FLAG_ON_DATA_READ_CALLED);
                            request.getServletContext().invokeOnAllDataRead(request.getExchange(), listener);
                        }
                    }
                } else if(anyAreSet(state, FLAG_FINISHED)) {
                    if (allAreClear(state, FLAG_ON_DATA_READ_CALLED)) {
                        setFlags(FLAG_ON_DATA_READ_CALLED);
                        request.getServletContext().invokeOnAllDataRead(request.getExchange(), listener);
                    }
                } else {
                    channel.resumeReads();
                }
            } catch (final Throwable e) {
                try {
                    request.getServletContext().invokeRunnable(request.getExchange(), new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(e);
                        }
                    });
                } finally {
                    if (pooled != null) {
                        pooled.close();
                        pooled = null;
                    }
                    IoUtils.safeClose(channel);
                }
            }
        }
    }

    private void setFlags(int flags) {
        int old;
        do {
            old = state;
        } while (!stateUpdater.compareAndSet(this, old, old | flags));
    }

    private void clearFlags(int flags) {
        int old;
        do {
            old = state;
        } while (!stateUpdater.compareAndSet(this, old, old & ~flags));
    }
}
