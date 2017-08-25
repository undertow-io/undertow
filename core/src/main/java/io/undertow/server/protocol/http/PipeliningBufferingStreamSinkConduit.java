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

package io.undertow.server.protocol.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * A buffer that is used when processing pipelined requests, that allows the server to
 * buffer multiple responses into a single write() call.
 * <p>
 * This can improve performance when pipelining requests.
 *
 * @author Stuart Douglas
 */
public class PipeliningBufferingStreamSinkConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {
    /**
     * If this channel is shutdown
     */
    private static final int SHUTDOWN = 1;
    private static final int DELEGATE_SHUTDOWN = 1 << 1;
    private static final int FLUSHING = 1 << 3;

    private int state;

    private final ByteBufferPool pool;
    private PooledByteBuffer buffer;

    public PipeliningBufferingStreamSinkConduit(StreamSinkConduit next, final ByteBufferPool pool) {
        super(next);
        this.pool = pool;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (anyAreSet(state, FLUSHING)) {
            boolean res = flushBuffer();
            if (!res) {
                return 0;
            }
        }
        PooledByteBuffer pooled = this.buffer;
        if (pooled == null) {
            this.buffer = pooled = pool.allocate();
        }
        final ByteBuffer buffer = pooled.getBuffer();

        long total = Buffers.remaining(srcs, offset, length);

        if (buffer.remaining() > total) {
            long put = total;
            Buffers.copy(buffer, srcs, offset, length);
            return put;
        } else {
            return flushBufferWithUserData(srcs, offset, length);
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (anyAreSet(state, SHUTDOWN)) {
            throw new ClosedChannelException();
        }
        if (anyAreSet(state, FLUSHING)) {
            boolean res = flushBuffer();
            if (!res) {
                return 0;
            }
        }
        PooledByteBuffer pooled = this.buffer;
        if (pooled == null) {
            this.buffer = pooled = pool.allocate();
        }
        final ByteBuffer buffer = pooled.getBuffer();
        if (buffer.remaining() > src.remaining()) {
            int put = src.remaining();
            buffer.put(src);
            return put;
        } else {
            return (int) flushBufferWithUserData(new ByteBuffer[]{src}, 0, 1);
        }
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offset, length);
    }

    private long flushBufferWithUserData(final ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
        final ByteBuffer byteBuffer = buffer.getBuffer();
        if (byteBuffer.position() == 0) {
            try {
                return next.write(byteBuffers, offset, length);
            } finally {
                buffer.close();
                buffer = null;
            }
        }

        if (!anyAreSet(state, FLUSHING)) {
            state |= FLUSHING;
            byteBuffer.flip();
        }
        int originalBufferedRemaining = byteBuffer.remaining();
        long toWrite = originalBufferedRemaining;
        ByteBuffer[] writeBufs = new ByteBuffer[length + 1];
        writeBufs[0] = byteBuffer;
        for (int i = offset; i < offset + length; ++i) {
            writeBufs[i + 1 - offset] = byteBuffers[i];
            toWrite += byteBuffers[i].remaining();
        }

        long res = 0;
        long written = 0;
        do {
            res = next.write(writeBufs, 0, writeBufs.length);
            written += res;
            if (res == 0) {
                if (written > originalBufferedRemaining) {
                    buffer.close();
                    this.buffer = null;
                    state &= ~FLUSHING;
                    return written - originalBufferedRemaining;
                }
                return 0;
            }
        } while (written < toWrite);
        buffer.close();
        this.buffer = null;
        state &= ~FLUSHING;
        return written - originalBufferedRemaining;
    }

    /**
     * Flushes the cached data.
     * <p>
     * This should be called when a read thread fails to read any more request data, to make sure that any
     * buffered data is flushed after the last pipelined request.
     * <p>
     * If this returns false the read thread should suspend reads and resume writes
     *
     * @return <code>true</code> If the flush succeeded, false otherwise
     * @throws IOException
     */
    public boolean flushPipelinedData() throws IOException {
        if (buffer == null || (buffer.getBuffer().position() == 0 && allAreClear(state, FLUSHING))) {
            return next.flush();
        }
        return flushBuffer();
    }

    /**
     * Gets the channel wrapper that implements the buffering
     */
    public void setupPipelineBuffer(final HttpServerExchange exchange) {
        ((HttpServerConnection) exchange.getConnection()).getChannel().getSinkChannel().setConduit(this);
    }

    private boolean flushBuffer() throws IOException {
        if (buffer == null) {
            return next.flush();
        }
        final ByteBuffer byteBuffer = buffer.getBuffer();
        if (!anyAreSet(state, FLUSHING)) {
            state |= FLUSHING;
            byteBuffer.flip();
        }
        while (byteBuffer.hasRemaining()) {
            if (next.write(byteBuffer) == 0) {
                return false;
            }
        }
        if (!next.flush()) {
            return false;
        }
        buffer.close();
        this.buffer = null;
        state &= ~FLUSHING;
        return true;
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (buffer != null) {
            if (buffer.getBuffer().hasRemaining()) {
                return;
            }
        }
        next.awaitWritable(time, timeUnit);
    }

    @Override
    public void awaitWritable() throws IOException {
        if (buffer != null) {
            if (buffer.getBuffer().hasRemaining()) {
                return;
            }
            next.awaitWritable();
        }
    }

    @Override
    public boolean flush() throws IOException {
        if (anyAreSet(state, SHUTDOWN)) {
            if (!flushBuffer()) {
                return false;
            }
            if (anyAreSet(state, SHUTDOWN) &&
                    anyAreClear(state, DELEGATE_SHUTDOWN)) {
                state |= DELEGATE_SHUTDOWN;
                next.terminateWrites();
            }
            return next.flush();
        }
        return true;
    }

    @Override
    public void terminateWrites() throws IOException {
        state |= SHUTDOWN;
        if (buffer == null) {
            state |= DELEGATE_SHUTDOWN;
            next.terminateWrites();
        }
    }

    public void truncateWrites() throws IOException {
        try {
            next.truncateWrites();
        } finally {
            if (buffer != null) {
                buffer.close();
            }
        }
    }

    public void exchangeComplete(final HttpServerExchange exchange) {
        //if we ever fail to read then we flush the pipeline buffer
        //this relies on us always doing an eager read when starting a request,
        //rather than waiting to be notified of data being available
        final HttpServerConnection connection = (HttpServerConnection) exchange.getConnection();
        if (connection.getExtraBytes() == null || exchange.isUpgrade()) {
            performFlush(exchange, connection);
        } else {
            connection.getReadListener().exchangeComplete(exchange);
        }
    }

    void performFlush(final HttpServerExchange exchange, final HttpServerConnection connection) {
        try {
            final HttpServerConnection.ConduitState oldState = connection.resetChannel();
            if (!flushPipelinedData()) {
                final StreamConnection channel = connection.getChannel();
                channel.getSinkChannel().setWriteListener(new ChannelListener<Channel>() {
                    @Override
                    public void handleEvent(Channel c) {
                        try {
                            if (flushPipelinedData()) {
                                channel.getSinkChannel().setWriteListener(null);
                                channel.getSinkChannel().suspendWrites();
                                connection.restoreChannel(oldState);
                                connection.getReadListener().exchangeComplete(exchange);
                            }
                        } catch (IOException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                            IoUtils.safeClose(channel);
                        } catch (Throwable t) {
                            UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
                            IoUtils.safeClose(channel);
                        }
                    }
                });
                connection.getChannel().getSinkChannel().resumeWrites();
                return;
            } else {
                connection.restoreChannel(oldState);
                connection.getReadListener().exchangeComplete(exchange);
            }
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            IoUtils.safeClose(connection.getChannel());
        } catch (Throwable t) {
            UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
            IoUtils.safeClose(connection.getChannel());
        }
    }

}

