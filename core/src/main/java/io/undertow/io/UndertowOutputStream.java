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
import java.nio.channels.FileChannel;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.Buffers;
import org.xnio.Pool;
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
public class UndertowOutputStream extends OutputStream implements BufferWritableOutputStream {

    private final HttpServerExchange exchange;
    private ByteBuffer buffer;
    private Pooled<ByteBuffer> pooledBuffer;
    private StreamSinkChannel channel;
    private int state;
    private int written;
    private final long contentLength;

    private static final int FLAG_CLOSED = 1;
    private static final int FLAG_WRITE_STARTED = 1 << 1;

    private static final int MAX_BUFFERS_TO_ALLOCATE = 10;

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
        //if this is the last of the content
        ByteBuffer buffer = buffer();
        if (len == contentLength - written || buffer.remaining() < len) {
            if (buffer.remaining() < len) {

                //so what we have will not fit.
                //We allocate multiple buffers up to MAX_BUFFERS_TO_ALLOCATE
                //and put it in them
                //if it still dopes not fit we loop, re-using these buffers

                StreamSinkChannel channel = this.channel;
                if (channel == null) {
                    this.channel = channel = exchange.getResponseChannel();
                }
                final Pool<ByteBuffer> bufferPool = exchange.getConnection().getBufferPool();
                ByteBuffer[] buffers = new ByteBuffer[MAX_BUFFERS_TO_ALLOCATE + 1];
                Pooled[] pooledBuffers = new Pooled[MAX_BUFFERS_TO_ALLOCATE];
                try {
                    buffers[0] = buffer;
                    int currentOffset = off;
                    int rem = buffer.remaining();
                    buffer.put(b, currentOffset, rem);
                    buffer.flip();
                    currentOffset += rem;
                    int bufferCount = 1;
                    for (int i = 0; i < MAX_BUFFERS_TO_ALLOCATE; ++i) {
                        Pooled<ByteBuffer> pooled = bufferPool.allocate();
                        pooledBuffers[bufferCount - 1] = pooled;
                        buffers[bufferCount++] = pooled.getResource();
                        ByteBuffer cb = pooled.getResource();
                        int toWrite = len - currentOffset;
                        if (toWrite > cb.remaining()) {
                            rem = cb.remaining();
                            cb.put(b, currentOffset, rem);
                            cb.flip();
                            currentOffset += rem;
                        } else {
                            cb.put(b, currentOffset, len - currentOffset);
                            currentOffset = len;
                            cb.flip();
                            break;
                        }
                    }
                    Channels.writeBlocking(channel, buffers, 0, bufferCount);
                    while (currentOffset < len) {
                        //ok, it did not fit, loop and loop and loop until it is done
                        bufferCount = 0;
                        for (int i = 0; i < MAX_BUFFERS_TO_ALLOCATE + 1; ++i) {
                            ByteBuffer cb = buffers[i];
                            cb.clear();
                            bufferCount++;
                            int toWrite = len - currentOffset;
                            if (toWrite > cb.remaining()) {
                                rem = cb.remaining();
                                cb.put(b, currentOffset, rem);
                                cb.flip();
                                currentOffset += rem;
                            } else {
                                cb.put(b, currentOffset, len - currentOffset);
                                currentOffset = len;
                                cb.flip();
                                break;
                            }
                        }
                        Channels.writeBlocking(channel, buffers, 0, bufferCount);
                    }
                    buffer.clear();
                } finally {
                    for (int i = 0; i < pooledBuffers.length; ++i) {
                        Pooled p = pooledBuffers[i];
                        if (p == null) {
                            break;
                        }
                        p.free();
                    }
                }
            } else {
                buffer.put(b, off, len);
                if (buffer.remaining() == 0) {
                    writeBufferBlocking(false);
                }
            }
        } else {
            buffer.put(b, off, len);
            if (buffer.remaining() == 0) {
                writeBufferBlocking(false);
            }
        }
        updateWritten(len);
    }

    @Override
    public void write(ByteBuffer[] buffers) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        int len = 0;
        for (ByteBuffer buf : buffers) {
            len += buf.remaining();
        }
        if (len < 1) {
            return;
        }

        //if we have received the exact amount of content write it out in one go
        //this is a common case when writing directly from a buffer cache.
        if (this.written == 0 && len == contentLength) {
            if (channel == null) {
                channel = exchange.getResponseChannel();
            }
            Channels.writeBlocking(channel, buffers, 0, buffers.length);
            state |= FLAG_WRITE_STARTED;
        } else {
            ByteBuffer buffer = buffer();
            if (len < buffer.remaining()) {
                Buffers.copy(buffer, buffers, 0, buffers.length);
            } else {
                if (channel == null) {
                    channel = exchange.getResponseChannel();
                }
                if (buffer.position() == 0) {
                    Channels.writeBlocking(channel, buffers, 0, buffers.length);
                } else {
                    final ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + 1];
                    buffer.flip();
                    newBuffers[0] = buffer;
                    System.arraycopy(buffers, 0, newBuffers, 1, buffers.length);
                    Channels.writeBlocking(channel, newBuffers, 0, newBuffers.length);
                    buffer.clear();
                }
                state |= FLAG_WRITE_STARTED;
            }
        }
        updateWritten(len);
    }

    @Override
    public void write(ByteBuffer byteBuffer) throws IOException {
        write(new ByteBuffer[]{byteBuffer});
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
        if (buffer != null && buffer.position() != 0) {
            writeBufferBlocking(false);
        }
        if (channel == null) {
            channel = exchange.getResponseChannel();
        }
        Channels.flushBlocking(channel);
    }

    private void writeBufferBlocking(final boolean writeFinal) throws IOException {
        if (channel == null) {
            channel = exchange.getResponseChannel();
        }
        buffer.flip();

        while (buffer.hasRemaining()) {
            if(writeFinal) {
                channel.writeFinal(buffer);
            } else {
                channel.write(buffer);
            }
            if(buffer.hasRemaining()) {
                channel.awaitWritable();
            }
        }
        buffer.clear();
        state |= FLAG_WRITE_STARTED;
    }
    @Override
    public void transferFrom(FileChannel source) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        if (buffer != null && buffer.position() != 0) {
            writeBufferBlocking(false);
        }
        if (channel == null) {
            channel = exchange.getResponseChannel();
        }
        long position = source.position();
        long size = source.size();
        Channels.transferBlocking(channel, source, position, size);
        updateWritten(size - position);
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
                writeBufferBlocking(true);
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

    private ByteBuffer buffer() {
        ByteBuffer buffer = this.buffer;
        if (buffer != null) {
            return buffer;
        }
        this.pooledBuffer = exchange.getConnection().getBufferPool().allocate();
        this.buffer = pooledBuffer.getResource();
        return this.buffer;
    }

}
