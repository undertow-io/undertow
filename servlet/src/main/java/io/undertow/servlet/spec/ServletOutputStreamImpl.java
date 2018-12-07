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

import static io.undertow.util.Bits.anyAreClear;
import static io.undertow.util.Bits.anyAreSet;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import io.netty.buffer.ByteBuf;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.UndertowOutputStream;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.Headers;
import io.undertow.util.IoUtils;
import io.undertow.util.UndertowOptionMap;

/**
 * This stream essentially has two modes. When it is being used in standard blocking mode then
 * it will buffer in the pooled buffer. If the stream is closed before the buffer is full it will
 * set a content-length header if one has not been explicitly set.
 * <p>
 * If a content-length header was present when the stream was created then it will automatically
 * close and flush itself once the appropriate amount of data has been written.
 * <p>
 * Once the listener has been set it goes into async mode, and writes become non blocking. Most methods
 * have two different code paths, based on if the listener has been set or not
 * <p>
 * Once the write listener has been set operations must only be invoked on this stream from the write
 * listener callback. Attempting to invoke from a different thread will result in an IllegalStateException.
 * <p>
 * Async listener tasks are queued in the {@link AsyncContextImpl}. At most one lister can be active at
 * one time, which simplifies the thread safety requirements.
 *
 * @author Stuart Douglas
 */
public class ServletOutputStreamImpl extends ServletOutputStream {

    private final ServletRequestContext servletRequestContext;
    private final HttpServerExchange exchange;
    private ByteBuf pooledBuffer;
    private int state;
    private long written;
    private final long contentLength;

    private static final int FLAG_CLOSED = 1;
    private static final int FLAG_WRITE_STARTED = 1 << 1;

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param exchange The exchange
     */
    public ServletOutputStreamImpl(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.contentLength = exchange.getResponseContentLength();
        servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
    }

    public ServletOutputStreamImpl(HttpServerExchange exchange, Integer bufferSize) {
        this.exchange = exchange;
        this.contentLength = exchange.getResponseContentLength();
        servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
    }


    public long getBytesWritten() {
        return written;
    }

    /**
     * {@inheritDoc}
     */
    public void write(final int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    /**
     * {@inheritDoc}
     */
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * {@inheritDoc}
     */
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (len < 1) {
            return;
        }
        if (exchange.getIoThread().inEventLoop()) {
            throw UndertowMessages.MESSAGES.blockingIoFromIOThread();
        }
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }

        int rem = len;
        int idx = off;
        ByteBuf buffer = pooledBuffer;
        try {
            if (buffer == null) {
                pooledBuffer = buffer = exchange.getConnection().getByteBufferPool().buffer();
            }
            while (rem > 0) {
                int toWrite = Math.min(rem, buffer.writableBytes());
                buffer.writeBytes(b, idx, toWrite);
                rem -= toWrite;
                idx += toWrite;
                if (!buffer.isWritable()) {
                    exchange.writeBlocking(buffer, false);
                    this.pooledBuffer = buffer = exchange.getConnection().getByteBufferPool().buffer();
                }
            }
        } catch (Exception e) {
            if (buffer != null) {
                buffer.release();
                this.pooledBuffer = null;
            }
            throw new IOException(e);
        }
        updateWritten(len);
    }

    void updateWritten(final long len) throws IOException {
        this.written += len;
        if (contentLength != -1 && this.written >= contentLength) {
            flush();
            close();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        try {
            if (pooledBuffer != null) {
                exchange.writeBlocking(pooledBuffer, false);
                pooledBuffer = null;
            }
        } catch (Exception e) {
            if (pooledBuffer != null) {
                pooledBuffer.release();
                pooledBuffer = null;
            }
            throw new IOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) return;
        state |= FLAG_CLOSED;
        if (anyAreClear(state, FLAG_WRITE_STARTED) && servletRequestContext.getOriginalResponse().getHeader(Headers.CONTENT_LENGTH_STRING) == null) {
            if (pooledBuffer == null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
            } else {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + pooledBuffer.readableBytes());
            }
        }
        try {
            exchange.writeBlocking(pooledBuffer, true);
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            pooledBuffer = null;
        }
    }


    public void resetBuffer() {
        if(pooledBuffer != null) {
            pooledBuffer.clear();
        }
    }

    public void setBufferSize(int bufferSize) {
        throw new RuntimeException("NYI");
    }

    public ServletRequestContext getServletRequestContext() {
        return servletRequestContext;
    }

    public ByteBuf underlyingBuffer() {
        if(pooledBuffer == null) {
            pooledBuffer = exchange.getConnection().getByteBufferPool().buffer();
        }
        return pooledBuffer;
    }

    public void flushInternal() throws IOException {
        flush();
    }

    public void updateWritten(int len) {

        this.written += len;
        long contentLength = servletRequestContext.getOriginalResponse().getContentLength();
        if (contentLength != -1 && this.written >= contentLength) {
            IoUtils.safeClose(this);
        }
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        throw new RuntimeException("NYI");
    }
}
