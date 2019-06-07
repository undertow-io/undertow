/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server;

import static io.undertow.util.Bits.anyAreClear;
import static io.undertow.util.Bits.anyAreSet;

import java.io.IOException;
import java.io.OutputStream;

import io.netty.buffer.ByteBuf;
import io.undertow.UndertowMessages;
import io.undertow.util.HttpHeaderNames;

/**
 * Buffering output stream that wraps a channel.
 * <p>
 * This stream delays channel creation, so if a response will fit in the buffer it is not necessary to
 * set the content length header.
 *
 * @author Stuart Douglas
 */
public class UndertowOutputStream extends OutputStream {

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
    public UndertowOutputStream(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.contentLength = exchange.getResponseContentLength();
    }

    /**
     * If the response has not yet been written to the client this method will clear the streams buffer,
     * invalidating any content that has already been written. If any content has already been sent to the client then
     * this method will throw and IllegalStateException
     *
     * @throws java.lang.IllegalStateException If the response has been committed
     */
    public void resetBuffer() {
        if(anyAreSet(state, FLAG_WRITE_STARTED)) {
            throw UndertowMessages.MESSAGES.cannotResetBuffer();
        }
        if (pooledBuffer != null) {
            pooledBuffer.release();
            pooledBuffer = null;
            written = 0;
        }
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
                pooledBuffer = buffer = exchange.getConnection().allocateBuffer();
            }
            while (rem > 0) {
                int toWrite = Math.min(rem, buffer.writableBytes());
                buffer.writeBytes(b, idx, toWrite);
                rem -= toWrite;
                idx += toWrite;
                if (!buffer.isWritable()) {
                    exchange.writeBlocking(buffer, false);
                    this.pooledBuffer = buffer = exchange.getConnection().allocateBuffer();
                }
            }
        } catch (Exception e) {
            if (buffer != null) {
                buffer.release();
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
        if (anyAreClear(state, FLAG_WRITE_STARTED)) {
            if (pooledBuffer == null) {
                exchange.responseHeaders().set(HttpHeaderNames.CONTENT_LENGTH, "0");
            } else {
                exchange.responseHeaders().set(HttpHeaderNames.CONTENT_LENGTH, "" + pooledBuffer.readableBytes());
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

}
