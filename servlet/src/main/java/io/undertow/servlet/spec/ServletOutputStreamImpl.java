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
import java.nio.channels.FileChannel;
import javax.servlet.DispatcherType;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.WriteListener;

import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.io.BufferWritableOutputStream;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.Headers;

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
public class ServletOutputStreamImpl extends ServletOutputStream implements BufferWritableOutputStream {

    private final ServletRequestContext servletRequestContext;
    private PooledByteBuffer pooledBuffer;
    private ByteBuffer buffer;
    private Integer bufferSize;
    private StreamSinkChannel channel;
    private long written;
    private int state;
    private AsyncContextImpl asyncContext;

    private WriteListener listener;
    private WriteChannelListener internalListener;


    /**
     * buffers that are queued up to be written via async writes. This will include
     * {@link #buffer} as the first element, and maybe a user supplied buffer that
     * did not fit
     */
    private ByteBuffer[] buffersToWrite;

    private FileChannel pendingFile;

    private static final int FLAG_CLOSED = 1;
    private static final int FLAG_WRITE_STARTED = 1 << 1;
    private static final int FLAG_READY = 1 << 2;
    private static final int FLAG_DELEGATE_SHUTDOWN = 1 << 3;
    private static final int FLAG_IN_CALLBACK = 1 << 4;

    //TODO: should this be configurable?
    private static final int MAX_BUFFERS_TO_ALLOCATE = 6;


    /**
     * Construct a new instance.  No write timeout is configured.
     */
    public ServletOutputStreamImpl(final ServletRequestContext servletRequestContext) {
        this.servletRequestContext = servletRequestContext;
    }

    /**
     * Construct a new instance.  No write timeout is configured.
     */
    public ServletOutputStreamImpl(final ServletRequestContext servletRequestContext, int bufferSize) {
        this.bufferSize = bufferSize;
        this.servletRequestContext = servletRequestContext;
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
        if (anyAreSet(state, FLAG_CLOSED) || servletRequestContext.getOriginalResponse().isTreatAsCommitted()) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        if (len < 1) {
            return;
        }

        if (listener == null) {
            ByteBuffer buffer = buffer();
            if (buffer.remaining() < len) {

                //so what we have will not fit.
                //We allocate multiple buffers up to MAX_BUFFERS_TO_ALLOCATE
                //and put it in them
                //if it still dopes not fit we loop, re-using these buffers

                StreamSinkChannel channel = this.channel;
                if (channel == null) {
                    this.channel = channel = servletRequestContext.getExchange().getResponseChannel();
                }
                final ByteBufferPool bufferPool = servletRequestContext.getExchange().getConnection().getByteBufferPool();
                ByteBuffer[] buffers = new ByteBuffer[MAX_BUFFERS_TO_ALLOCATE + 1];
                PooledByteBuffer[] pooledBuffers = new PooledByteBuffer[MAX_BUFFERS_TO_ALLOCATE];
                try {
                    buffers[0] = buffer;
                    int bytesWritten = 0;
                    int rem = buffer.remaining();
                    buffer.put(b, bytesWritten + off, rem);
                    buffer.flip();
                    bytesWritten += rem;
                    int bufferCount = 1;
                    for (int i = 0; i < MAX_BUFFERS_TO_ALLOCATE; ++i) {
                        PooledByteBuffer pooled = bufferPool.allocate();
                        pooledBuffers[bufferCount - 1] = pooled;
                        buffers[bufferCount++] = pooled.getBuffer();
                        ByteBuffer cb = pooled.getBuffer();
                        int toWrite = len - bytesWritten;
                        if (toWrite > cb.remaining()) {
                            rem = cb.remaining();
                            cb.put(b, bytesWritten + off, rem);
                            cb.flip();
                            bytesWritten += rem;
                        } else {
                            cb.put(b, bytesWritten + off, toWrite);
                            bytesWritten = len;
                            cb.flip();
                            break;
                        }
                    }
                    Channels.writeBlocking(channel, buffers, 0, bufferCount);
                    while (bytesWritten < len) {
                        //ok, it did not fit, loop and loop and loop until it is done
                        bufferCount = 0;
                        for (int i = 0; i < MAX_BUFFERS_TO_ALLOCATE + 1; ++i) {
                            ByteBuffer cb = buffers[i];
                            cb.clear();
                            bufferCount++;
                            int toWrite = len - bytesWritten;
                            if (toWrite > cb.remaining()) {
                                rem = cb.remaining();
                                cb.put(b, bytesWritten + off, rem);
                                cb.flip();
                                bytesWritten += rem;
                            } else {
                                cb.put(b, bytesWritten + off, toWrite);
                                bytesWritten = len;
                                cb.flip();
                                break;
                            }
                        }
                        Channels.writeBlocking(channel, buffers, 0, bufferCount);
                    }
                    buffer.clear();
                } finally {
                    for (int i = 0; i < pooledBuffers.length; ++i) {
                        PooledByteBuffer p = pooledBuffers[i];
                        if (p == null) {
                            break;
                        }
                        p.close();
                    }
                }
            } else {
                buffer.put(b, off, len);
                if (buffer.remaining() == 0) {
                    writeBufferBlocking(false);
                }
            }
            updateWritten(len);
        } else {
            if (anyAreClear(state, FLAG_READY)) {
                throw UndertowServletMessages.MESSAGES.streamNotReady();
            }
            //even though we are in async mode we are still buffering
            try {
                ByteBuffer buffer = buffer();
                if (buffer.remaining() > len) {
                    buffer.put(b, off, len);
                } else {
                    buffer.flip();
                    final ByteBuffer userBuffer = ByteBuffer.wrap(b, off, len);
                    final ByteBuffer[] bufs = new ByteBuffer[]{buffer, userBuffer};
                    long toWrite = Buffers.remaining(bufs);
                    long res;
                    long written = 0;
                    createChannel();
                    state |= FLAG_WRITE_STARTED;
                    do {
                        res = channel.write(bufs);
                        written += res;
                        if (res == 0) {
                            //write it out with a listener
                            //but we need to copy any extra data
                            final ByteBuffer copy = ByteBuffer.allocate(userBuffer.remaining());
                            copy.put(userBuffer);
                            copy.flip();

                            this.buffersToWrite = new ByteBuffer[]{buffer, copy};
                            state &= ~FLAG_READY;
                            channel.resumeWrites();
                            return;
                        }
                    } while (written < toWrite);
                    buffer.clear();
                }
            } finally {
                updateWrittenAsync(len);
            }
        }
    }


    @Override
    public void write(ByteBuffer[] buffers) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED) || servletRequestContext.getOriginalResponse().isTreatAsCommitted()) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        int len = 0;
        for (ByteBuffer buf : buffers) {
            len += buf.remaining();
        }
        if (len < 1) {
            return;
        }

        if (listener == null) {
            //if we have received the exact amount of content write it out in one go
            //this is a common case when writing directly from a buffer cache.
            if (this.written == 0 && len == servletRequestContext.getOriginalResponse().getContentLength()) {
                if (channel == null) {
                    channel = servletRequestContext.getExchange().getResponseChannel();
                }
                Channels.writeBlocking(channel, buffers, 0, buffers.length);
                state |= FLAG_WRITE_STARTED;
            } else {
                ByteBuffer buffer = buffer();
                if (len < buffer.remaining()) {
                    Buffers.copy(buffer, buffers, 0, buffers.length);
                } else {
                    if (channel == null) {
                        channel = servletRequestContext.getExchange().getResponseChannel();
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
        } else {
            if (anyAreClear(state, FLAG_READY)) {
                throw UndertowServletMessages.MESSAGES.streamNotReady();
            }
            //even though we are in async mode we are still buffering
            try {
                ByteBuffer buffer = buffer();
                if (buffer.remaining() > len) {
                    Buffers.copy(buffer, buffers, 0, buffers.length);
                } else {
                    final ByteBuffer[] bufs = new ByteBuffer[buffers.length + 1];
                    buffer.flip();
                    bufs[0] = buffer;
                    System.arraycopy(buffers, 0, bufs, 1, buffers.length);
                    long toWrite = Buffers.remaining(bufs);
                    long res;
                    long written = 0;
                    createChannel();
                    state |= FLAG_WRITE_STARTED;
                    do {
                        res = channel.write(bufs);
                        written += res;
                        if (res == 0) {
                            //write it out with a listener
                            //but we need to copy any extra data
                            //TODO: should really allocate from the pool here
                            final ByteBuffer copy = ByteBuffer.allocate((int) Buffers.remaining(buffers));
                            Buffers.copy(copy, buffers, 0, buffers.length);
                            copy.flip();
                            this.buffersToWrite = new ByteBuffer[]{buffer, copy};
                            state &= ~FLAG_READY;
                            channel.resumeWrites();
                            return;
                        }
                    } while (written < toWrite);
                    buffer.clear();
                }
            } finally {
                updateWrittenAsync(len);
            }
        }
    }

    @Override
    public void write(ByteBuffer byteBuffer) throws IOException {
        write(new ByteBuffer[]{byteBuffer});
    }

    void updateWritten(final long len) throws IOException {
        this.written += len;
        long contentLength = servletRequestContext.getOriginalResponse().getContentLength();
        if (contentLength != -1 && this.written >= contentLength) {
            close();
        }
    }

    void updateWrittenAsync(final long len) throws IOException {
        this.written += len;
        long contentLength = servletRequestContext.getOriginalResponse().getContentLength();
        if (contentLength != -1 && this.written >= contentLength) {
            state |= FLAG_CLOSED;
            //if buffersToWrite is set we are already flushing
            //so we don't have to do anything
            if (buffersToWrite == null && pendingFile == null) {
                if (flushBufferAsync(true)) {
                    channel.shutdownWrites();
                    state |= FLAG_DELEGATE_SHUTDOWN;
                    channel.flush();
                    if (pooledBuffer != null) {
                        pooledBuffer.close();
                        buffer = null;
                        pooledBuffer = null;
                    }
                }
            }
        }
    }

    private boolean flushBufferAsync(final boolean writeFinal) throws IOException {

        ByteBuffer[] bufs = buffersToWrite;
        if (bufs == null) {
            ByteBuffer buffer = this.buffer;
            if (buffer == null || buffer.position() == 0) {
                return true;
            }
            buffer.flip();
            bufs = new ByteBuffer[]{buffer};
        }
        long toWrite = Buffers.remaining(bufs);
        if (toWrite == 0) {
            //we clear the buffer, so it can be written to again
            buffer.clear();
            return true;
        }
        state |= FLAG_WRITE_STARTED;
        createChannel();
        long res;
        long written = 0;
        do {
            if (writeFinal) {
                res = channel.writeFinal(bufs);
            } else {
                res = channel.write(bufs);
            }
            written += res;
            if (res == 0) {
                //write it out with a listener
                state = state & ~FLAG_READY;
                buffersToWrite = bufs;
                channel.resumeWrites();
                return false;
            }
        } while (written < toWrite);
        buffer.clear();
        return true;
    }


    /**
     * Returns the underlying buffer. If this has not been created yet then
     * it is created.
     * <p>
     * Callers that use this method must call {@link #updateWritten(long)} to update the written
     * amount.
     * <p>
     * This allows the buffer to be filled directly, which can be more efficient.
     * <p>
     * This method is basically a hack that should only be used by the print writer
     *
     * @return The underlying buffer
     */
    ByteBuffer underlyingBuffer() {
        if (anyAreSet(state, FLAG_CLOSED)) {
            return null;
        }
        return buffer();
    }

    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException {
        //according to the servlet spec we ignore a flush from within an include
        if (servletRequestContext.getOriginalRequest().getDispatcherType() == DispatcherType.INCLUDE ||
                servletRequestContext.getOriginalResponse().isTreatAsCommitted()) {
            return;
        }
        if (servletRequestContext.getDeployment().getDeploymentInfo().isIgnoreFlush() &&
                servletRequestContext.getExchange().isRequestComplete() &&
                servletRequestContext.getOriginalResponse().getHeader(Headers.TRANSFER_ENCODING_STRING) == null) {
            //we mark the stream as flushed, but don't actually flush
            //because in most cases flush just kills performance
            //we only do this if the request is fully read, so that http tunneling scenarios still work
            servletRequestContext.getOriginalResponse().setIgnoredFlushPerformed(true);
            return;
        }
        flushInternal();
    }

    /**
     * {@inheritDoc}
     */
    public void flushInternal() throws IOException {
        if (listener == null) {
            if (anyAreSet(state, FLAG_CLOSED)) {
                //just return
                return;
            }
            if (buffer != null && buffer.position() != 0) {
                writeBufferBlocking(false);
            }
            if (channel == null) {
                channel = servletRequestContext.getExchange().getResponseChannel();
            }
            Channels.flushBlocking(channel);
        } else {
            if (anyAreClear(state, FLAG_READY)) {
                return;
            }
            createChannel();
            if (buffer == null || buffer.position() == 0) {
                //nothing to flush, we just flush the underlying stream
                //it does not matter if this succeeds or not
                channel.flush();
                return;
            }
            //we have some data in the buffer, we can just write it out
            //if the write fails we just compact, rather than changing the ready state
            state |= FLAG_WRITE_STARTED;
            buffer.flip();
            long res;
            do {
                res = channel.write(buffer);
            } while (buffer.hasRemaining() && res != 0);
            if (!buffer.hasRemaining()) {
                channel.flush();
            }
            buffer.compact();
        }
    }

    @Override
    public void transferFrom(FileChannel source) throws IOException {
        if (anyAreSet(state, FLAG_CLOSED) || servletRequestContext.getOriginalResponse().isTreatAsCommitted()) {
            throw UndertowServletMessages.MESSAGES.streamIsClosed();
        }
        if (listener == null) {
            if (buffer != null && buffer.position() != 0) {
                writeBufferBlocking(false);
            }
            if (channel == null) {
                channel = servletRequestContext.getExchange().getResponseChannel();
            }
            long position = source.position();
            long count = source.size() - position;
            Channels.transferBlocking(channel, source, position, count);
            updateWritten(count);
        } else {
            state |= FLAG_WRITE_STARTED;
            createChannel();

            long pos = 0;
            try {
                long size = source.size();
                pos = source.position();

                while (size - pos > 0) {
                    long ret = channel.transferFrom(pendingFile, pos, size - pos);
                    if (ret <= 0) {
                        state &= ~FLAG_READY;
                        pendingFile = source;
                        source.position(pos);
                        channel.resumeWrites();
                        return;
                    }
                    pos += ret;
                }
            } finally {
                updateWrittenAsync(pos - source.position());
            }
        }

    }


    private void writeBufferBlocking(final boolean writeFinal) throws IOException {
        if (channel == null) {
            channel = servletRequestContext.getExchange().getResponseChannel();
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            if (writeFinal) {
                channel.writeFinal(buffer);
            } else {
                channel.write(buffer);
            }
            if (buffer.hasRemaining()) {
                channel.awaitWritable();
            }
        }
        buffer.clear();
        state |= FLAG_WRITE_STARTED;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (servletRequestContext.getOriginalRequest().getDispatcherType() == DispatcherType.INCLUDE ||
                servletRequestContext.getOriginalResponse().isTreatAsCommitted()) {
            return;
        }
        if (listener == null) {
            if (anyAreSet(state, FLAG_CLOSED)) return;
            state |= FLAG_CLOSED;
            state &= ~FLAG_READY;
            if (allAreClear(state, FLAG_WRITE_STARTED) && channel == null && servletRequestContext.getOriginalResponse().getHeader(Headers.CONTENT_LENGTH_STRING) == null) {
                if (servletRequestContext.getOriginalResponse().getHeader(Headers.TRANSFER_ENCODING_STRING) == null) {
                    if (buffer == null) {
                        servletRequestContext.getExchange().getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
                    } else {
                        servletRequestContext.getExchange().getResponseHeaders().put(Headers.CONTENT_LENGTH, Integer.toString(buffer.position()));
                    }
                }
            }
            try {
                if (buffer != null) {
                    writeBufferBlocking(true);
                }
                if (channel == null) {
                    channel = servletRequestContext.getExchange().getResponseChannel();
                }
                state |= FLAG_DELEGATE_SHUTDOWN;
                StreamSinkChannel channel = this.channel;
                if (channel != null) { //mock requests
                    channel.shutdownWrites();
                    Channels.flushBlocking(channel);
                }
            } catch (IOException e) {
                IoUtils.safeClose(this.channel);
                throw e;
            } finally {
                if (pooledBuffer != null) {
                    pooledBuffer.close();
                    buffer = null;
                } else {
                    buffer = null;
                }
            }
        } else {
            closeAsync();
        }
    }

    /**
     * Closes the channel, and flushes any data out using async IO
     * <p>
     * This is used in two situations, if an output stream is not closed when a
     * request is done, and when performing a close on a stream that is in async
     * mode
     *
     * @throws IOException
     */
    public void closeAsync() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED) || servletRequestContext.getOriginalResponse().isTreatAsCommitted()) {
            return;
        }
        try {

            state |= FLAG_CLOSED;
            state &= ~FLAG_READY;
            if (allAreClear(state, FLAG_WRITE_STARTED) && channel == null) {

                if (servletRequestContext.getOriginalResponse().getHeader(Headers.TRANSFER_ENCODING_STRING) == null) {
                    if (buffer == null) {
                        servletRequestContext.getOriginalResponse().setHeader(Headers.CONTENT_LENGTH, "0");
                    } else {
                        servletRequestContext.getOriginalResponse().setHeader(Headers.CONTENT_LENGTH, Integer.toString(buffer.position()));
                    }
                }
            }
            createChannel();
            if (buffer != null) {
                if (!flushBufferAsync(true)) {
                    return;
                }

                if (pooledBuffer != null) {
                    pooledBuffer.close();
                    buffer = null;
                } else {
                    buffer = null;
                }
            }
            channel.shutdownWrites();
            state |= FLAG_DELEGATE_SHUTDOWN;
            if (!channel.flush()) {
                channel.resumeWrites();
            }
        } catch (IOException e) {
            if (pooledBuffer != null) {
                pooledBuffer.close();
                pooledBuffer = null;
                buffer = null;
            }
            throw e;
        }
    }

    private void createChannel() {
        if (channel == null) {
            channel = servletRequestContext.getExchange().getResponseChannel();
            if (internalListener != null) {
                channel.getWriteSetter().set(internalListener);
            }
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
            this.pooledBuffer = servletRequestContext.getExchange().getConnection().getByteBufferPool().allocate();
            this.buffer = pooledBuffer.getBuffer();
            return this.buffer;
        }
    }

    public void resetBuffer() {
        if (allAreClear(state, FLAG_WRITE_STARTED)) {
            if (pooledBuffer != null) {
                pooledBuffer.close();
                pooledBuffer = null;
            }
            buffer = null;
            this.written = 0;
        } else {
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }
    }

    public void setBufferSize(final int size) {
        if (buffer != null || servletRequestContext.getOriginalResponse().isTreatAsCommitted()) {
            throw UndertowServletMessages.MESSAGES.contentHasBeenWritten();
        }
        this.bufferSize = size;
    }

    public boolean isClosed() {
        return anyAreSet(state, FLAG_CLOSED);
    }

    @Override
    public boolean isReady() {
        if (listener == null) {
            //TODO: is this the correct behaviour?
            throw UndertowServletMessages.MESSAGES.streamNotInAsyncMode();
        }
        return anyAreSet(state, FLAG_READY);
    }

    @Override
    public void setWriteListener(final WriteListener writeListener) {
        if (writeListener == null) {
            throw UndertowServletMessages.MESSAGES.listenerCannotBeNull();
        }
        if (listener != null) {
            throw UndertowServletMessages.MESSAGES.listenerAlreadySet();
        }
        final ServletRequest servletRequest = servletRequestContext.getOriginalRequest();
        if (!servletRequest.isAsyncStarted()) {
            throw UndertowServletMessages.MESSAGES.asyncNotStarted();
        }
        asyncContext = (AsyncContextImpl) servletRequest.getAsyncContext();
        listener = writeListener;
        //we register the write listener on the underlying connection
        //so we don't have to force the creation of the response channel
        //under normal circumstances this will break write listener delegation
        this.internalListener = new WriteChannelListener();
        if (this.channel != null) {
            this.channel.getWriteSetter().set(internalListener);
        }
        //we resume from an async task, after the request has been dispatched
        asyncContext.addAsyncTask(new Runnable() {
            @Override
            public void run() {
                if (channel == null) {
                    servletRequestContext.getExchange().getIoThread().execute(new Runnable() {
                        @Override
                        public void run() {
                            internalListener.handleEvent(null);
                        }
                    });
                } else {
                    channel.resumeWrites();
                }
            }
        });
    }

    ServletRequestContext getServletRequestContext() {
        return servletRequestContext;
    }


    private class WriteChannelListener implements ChannelListener<StreamSinkChannel> {

        @Override
        public void handleEvent(final StreamSinkChannel aChannel) {
            //flush the channel if it is closed
            if (anyAreSet(state, FLAG_DELEGATE_SHUTDOWN)) {
                try {
                    //either it will work, and the channel is closed
                    //or it won't, and we continue with writes resumed
                    channel.flush();
                    return;
                } catch (IOException e) {
                    handleError(e);
                    return;
                }
            }
            //if there is data still to write
            if (buffersToWrite != null) {
                long toWrite = Buffers.remaining(buffersToWrite);
                long written = 0;
                long res;
                if (toWrite > 0) { //should always be true, but just to be defensive
                    do {
                        try {
                            res = channel.write(buffersToWrite);
                            written += res;
                            if (res == 0) {
                                return;
                            }
                        } catch (IOException e) {
                            handleError(e);
                            return;
                        }
                    } while (written < toWrite);
                }
                buffersToWrite = null;
                buffer.clear();
            }
            if (pendingFile != null) {
                try {
                    long size = pendingFile.size();
                    long pos = pendingFile.position();

                    while (size - pos > 0) {
                        long ret = channel.transferFrom(pendingFile, pos, size - pos);
                        if (ret <= 0) {
                            pendingFile.position(pos);
                            return;
                        }
                        pos += ret;
                    }
                    pendingFile = null;
                } catch (IOException e) {
                    handleError(e);
                    return;
                }
            }
            if (anyAreSet(state, FLAG_CLOSED)) {
                try {

                    if (pooledBuffer != null) {
                        pooledBuffer.close();
                        buffer = null;
                    } else {
                        buffer = null;
                    }
                    channel.shutdownWrites();
                    state |= FLAG_DELEGATE_SHUTDOWN;
                    channel.flush();
                } catch (IOException e) {
                    handleError(e);
                    return;
                }
            } else {

                if (asyncContext.isDispatched()) {
                    //this is no longer an async request
                    //we just return for now
                    //TODO: what do we do here? Revert back to blocking mode?
                    channel.suspendWrites();
                    return;
                }

                state |= FLAG_READY;
                try {
                    state |= FLAG_IN_CALLBACK;

                    servletRequestContext.getCurrentServletContext().invokeOnWritePossible(servletRequestContext.getExchange(), listener);

                    if (isReady()) {
                        //if the stream is still ready then we do not resume writes
                        //this is per spec, we only call the listener once for each time
                        //isReady returns true
                        if (channel != null) {
                            channel.suspendWrites();
                        }
                    } else {
                        if (channel != null) {
                            channel.resumeWrites();
                        }
                    }
                } catch (Throwable e) {
                    IoUtils.safeClose(channel);
                } finally {
                    state &= ~FLAG_IN_CALLBACK;
                }
            }

        }

        private void handleError(final IOException e) {

            try {
                servletRequestContext.getCurrentServletContext().invokeRunnable(servletRequestContext.getExchange(), new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(e);
                    }
                });
            } finally {
                IoUtils.safeClose(channel, servletRequestContext.getExchange().getConnection());
            }
        }
    }

}
