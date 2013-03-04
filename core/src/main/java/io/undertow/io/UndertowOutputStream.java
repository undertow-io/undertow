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

package io.undertow.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * Buffering output stream that wraps a channel.
 * <p/>
 * This stream delays channel creation, so if a response will fit in the buffer it is not nessesary to
 * set the content length header.
 *
 * @author Stuart Douglas
 */
public class UndertowOutputStream extends OutputStream {

    private final HttpServerExchange exchange;
    private ByteBuffer buffer;
    private Pooled<ByteBuffer> pooledBuffer;
    private Integer bufferSize;
    private StreamSinkChannel channel;
    private int state;
    private int written;
    private final Integer contentLength;

    private static final int FLAG_CLOSED = 1;
    private static final int FLAG_WRITE_STARTED = 1 << 1;

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param exchange
     */
    public UndertowOutputStream(HttpServerExchange exchange) {
        this.exchange = exchange;
        final String cl = exchange.getResponseHeaders().getFirst(Headers.CONTENT_LENGTH);
        if (cl != null) {
            contentLength = Integer.parseInt(cl);
        } else {
            contentLength = null;
        }
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
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        int written = 0;
        ByteBuffer buffer = buffer();
        while (written < len) {
            if (buffer.remaining() >= (len - written)) {
                buffer.put(b, off + written, len - written);
                if (buffer.remaining() == 0) {
                    writeBuffer();
                }
                updateWritten(len);
                return;
            } else {
                int remaining = buffer.remaining();
                buffer.put(b, off + written, remaining);
                writeBuffer();
                written += remaining;
            }
        }
        updateWritten(len);
    }

    void updateWritten(final int len) throws IOException {
        this.written += len;
        if (contentLength != null && this.written >= contentLength) {
            flush();
            close();
        }
    }

    /**
     * Returns the underlying buffer. If this has not been created yet then
     * it is created.
     * <p/>
     * Callers that use this method must call {@link #updateWritten(int)} to update the written
     * amount.
     * <p/>
     * This allows the buffer to be filled directly, which can be more efficient.
     *
     * @return The underlying buffer
     */
    ByteBuffer underlyingBuffer() {
        return buffer();
    }

    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        if (buffer != null && buffer.position() != 0) {
            writeBuffer();
        }
        if (channel == null) {
            channel = exchange.getResponseChannel();
        }
        Channels.flushBlocking(channel);
    }

    private void writeBuffer() throws IOException {
        buffer.flip();
        if (channel == null) {
            channel = exchange.getResponseChannel();
        }
        Channels.writeBlocking(channel, buffer);
        buffer.clear();
        state |= FLAG_WRITE_STARTED;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) return;
        try {
            state |= FLAG_CLOSED;
            if (anyAreClear(state, FLAG_WRITE_STARTED) && channel == null) {
                if (buffer == null) {
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                } else {
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + buffer.position());
                }
            }
            if (buffer != null) {
                writeBuffer();
            }
            if (channel == null) {
                channel = exchange.getResponseChannel();
            }
            StreamSinkChannel channel = this.channel;
            channel.shutdownWrites();
            Channels.flushBlocking(channel);
        } finally {
            if (pooledBuffer != null) {
                pooledBuffer.free();
                buffer = null;
            } else {
                buffer = null;
            }
        }
    }

    /**
     * Closes the stream, and writes the data, possibly using an async background writes.
     * <p/>
     * Once everything is written out the completion handle will be called. If the stream is
     * already closed then the completion handler is invoked immediately.
     *
     * @param handler
     * @throws java.io.IOException
     */
    public void closeAsync() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            exchange.endExchange();
            return;
        }
        state |= FLAG_CLOSED;
        if (anyAreClear(state, FLAG_WRITE_STARTED) && channel == null) {
            if (buffer == null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
            } else {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + buffer.position());
            }
        }

        if (channel == null) {
            channel = exchange.getResponseChannel();
        }
        if (buffer != null) {
            buffer.flip();
            try {
                int res = 0;
                do {
                    res = channel.write(buffer);
                    if (!buffer.hasRemaining()) {
                        if (pooledBuffer != null) {
                            pooledBuffer.free();
                        }
                        exchange.endExchange();
                        return;
                    }
                } while (res > 0);

                if (res == 0) {
                    channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        public void handleEvent(final StreamSinkChannel channel) {
                            int result;
                            boolean ok = false;
                            do {
                                try {
                                    result = channel.write(buffer);
                                    ok = true;
                                } catch (IOException e) {
                                    channel.suspendWrites();
                                    IoUtils.safeClose(channel);
                                    exchange.endExchange();
                                    return;
                                } finally {
                                    if (!ok) {
                                        if (pooledBuffer != null) {
                                            pooledBuffer.free();
                                        }
                                    }
                                }
                                if (result == 0) {
                                    return;
                                }
                                if (result == -1) {
                                    channel.suspendWrites();
                                    IoUtils.safeClose(channel);
                                    exchange.endExchange();
                                }
                            } while (buffer.hasRemaining());
                            if (pooledBuffer != null) {
                                pooledBuffer.free();
                            }
                            exchange.endExchange();
                        }

                    });
                    channel.resumeWrites();
                } else if (res == -1) {
                    IoUtils.safeClose(channel);
                    exchange.endExchange();
                } else {
                    buffer = null;
                    pooledBuffer = null;
                }
            } catch (IOException e) {
                IoUtils.safeClose(channel);
                exchange.endExchange();
            }
        } else {
            exchange.endExchange();
            buffer = null;
            pooledBuffer = null;
        }
    }


    private ByteBuffer buffer() {
        ByteBuffer buffer = this.buffer;
        if (buffer != null) {
            return buffer;
        }
        if (bufferSize != null) {
            this.buffer = ByteBuffer.allocateDirect(bufferSize);
            return this.buffer;
        } else {
            this.pooledBuffer = exchange.getConnection().getBufferPool().allocate();
            this.buffer = pooledBuffer.getResource();
            return this.buffer;
        }
    }

    public void resetBuffer() {
        if (anyAreClear(state, FLAG_WRITE_STARTED)) {
            if (pooledBuffer != null) {
                pooledBuffer.free();
                pooledBuffer = null;
            }
            buffer = null;
        } else {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
    }

    public void setBufferSize(final int size) {
        if (buffer != null) {
            throw UndertowMessages.MESSAGES.responseAlreadyStarted();
        }
        this.bufferSize = size;
    }

    public boolean isClosed() {
        return anyAreSet(state, FLAG_CLOSED);
    }
}
