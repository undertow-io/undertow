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

import static io.undertow.util.Bits.allAreClear;
import static io.undertow.util.Bits.anyAreClear;
import static io.undertow.util.Bits.anyAreSet;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import io.netty.buffer.ByteBuf;
import io.undertow.io.IoCallback;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;

/**
 * Servlet input stream implementation. This stream is non-buffered, and is used for both
 * HTTP requests and for upgraded streams.
 *
 * @author Stuart Douglas
 */
public class ServletInputStreamImpl extends ServletInputStream {

    private final HttpServletRequestImpl request;
    private final HttpServerExchange exchange;

    private volatile ReadListener listener;
    private volatile ServletInputStreamChannelListener internalListener;

    /**
     * If this stream is ready for a read
     */
    private static final int FLAG_READY = 1;
    private static final int FLAG_CLOSED = 1 << 1;
    private static final int FLAG_FINISHED = 1 << 2;
    private static final int FLAG_ON_DATA_READ_CALLED = 1 << 3;
    private static final int FLAG_IS_READY_CALLED = 1 << 6;

    private static final AtomicIntegerFieldUpdater<ServletInputStreamImpl> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ServletInputStreamImpl.class, "state");
    private volatile int state;
    private volatile AsyncContextImpl asyncContext;
    private volatile ByteBuf pooled;


    public ServletInputStreamImpl(final HttpServletRequestImpl request) {
        this.request = request;
        this.exchange = request.getExchange();
    }


    @Override
    public boolean isFinished() {
        return anyAreSet(state, FLAG_FINISHED);
    }

    @Override
    public boolean isReady() {
        boolean finished = anyAreSet(state, FLAG_FINISHED);
        if (finished) {
            if (anyAreClear(state, FLAG_ON_DATA_READ_CALLED)) {
                exchange.scheduleIoCallback(new IoCallback<Object>() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Object context) {
                        setFlags(FLAG_ON_DATA_READ_CALLED);
                        request.getServletContext().invokeOnAllDataRead(request.getExchange(), listener);
                    }
                }, null);
            }
        }
        boolean ready = anyAreSet(state, FLAG_READY) && !finished;
        if (!ready && listener != null && !finished) {
            exchange.readAsync(internalListener);
        }
        if (ready) {
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
        internalListener = new ServletInputStreamChannelListener();

        ByteBuf existing = pooled;
        pooled = null;

        //we resume from an async task, after the request has been dispatched
        asyncContext.addAsyncTask(new Runnable() {
            @Override
            public void run() {
                if(existing != null) {
                    exchange.scheduleIoCallback(internalListener, existing);
                } else {
                    exchange.readAsync(internalListener);
                }
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
            if (anyAreClear(state, FLAG_READY | FLAG_IS_READY_CALLED)) {
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
        ByteBuf buffer = pooled;
        int copied = Math.min(len, buffer.readableBytes());
        buffer.readBytes(b, off, copied);
        if (!buffer.isReadable()) {
            pooled.release();
            pooled = null;
            clearFlags(FLAG_READY);
        }
        return copied;
    }

    private void readIntoBuffer() throws IOException {
        if (pooled == null && !anyAreSet(state, FLAG_FINISHED)) {
            pooled = exchange.readBlocking();
            if (pooled == null) {
                setFlags(FLAG_FINISHED);
                pooled = null;
            }
        }
    }

    @Override
    public int available() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        int ret = exchange.readBytesAvailable();
        if (pooled != null) {
            ret += pooled.readableBytes();
        }
        return ret;
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
                    pooled.release();
                    pooled = null;
                }
            }
        } finally {
            setFlags(FLAG_FINISHED);
            if (pooled != null) {
                pooled.release();
                pooled = null;
            }
            exchange.discardRequest();
        }
    }

    private class ServletInputStreamChannelListener implements IoCallback<ByteBuf> {


        @Override
        public void onComplete(HttpServerExchange exchange, ByteBuf context) {
            try {
                if (asyncContext.isDispatched()) {
                    //this is no longer an async request
                    //we just return
                    //TODO: what do we do here? Revert back to blocking mode?
                    return;
                }
                pooled = context;
                if (anyAreSet(state, FLAG_FINISHED)) {
                    if (pooled != null) {
                        pooled.release();
                    }
                    return;
                }
                if (pooled != null) {
                    setFlags(FLAG_READY);
                    if (!anyAreSet(state, FLAG_FINISHED)) {
                        try {
                            request.getServletContext().invokeOnDataAvailable(request.getExchange(), listener);
                        } catch (Throwable e) {
                            try {
                                request.getServletContext().invokeRunnable(request.getExchange(), new Runnable() {
                                    @Override
                                    public void run() {
                                        listener.onError(e);
                                    }
                                });
                            } finally {
                                if (pooled != null) {
                                    pooled.release();
                                    pooled = null;
                                }
                                exchange.discardRequest();
                            }
                        }
                    }
                } else if (pooled == null) {
                    setFlags(FLAG_FINISHED);
                    if (allAreClear(state, FLAG_ON_DATA_READ_CALLED)) {
                        setFlags(FLAG_ON_DATA_READ_CALLED);
                        request.getServletContext().invokeOnAllDataRead(request.getExchange(), listener);
                    }
                } else {
                    exchange.readAsync(this);
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
                        pooled.release();
                        pooled = null;
                    }
                    exchange.discardRequest();
                }
            }
        }

        @Override
        public void onException(HttpServerExchange exchange, ByteBuf context, IOException exception) {

            try {
                request.getServletContext().invokeRunnable(request.getExchange(), new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(exception);
                    }
                });
            } finally {
                if (pooled != null) {
                    pooled.release();
                    pooled = null;
                }
                exchange.discardRequest();
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
