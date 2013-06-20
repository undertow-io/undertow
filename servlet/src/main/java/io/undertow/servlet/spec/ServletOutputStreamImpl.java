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

package io.undertow.servlet.spec;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.DispatcherType;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.WriteListener;

import io.undertow.io.BufferWritableOutputStream;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.Headers;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreClear;
import static org.xnio.Bits.anyAreSet;

/**
 * This stream essentially has two modes. When it is being used in standard blocking mode then
 * it will buffer in the pooled buffer. If the stream is closed before the buffer is full it will
 * set a content-length header if one has not been explicitly set.
 * <p/>
 * If a content-length header was present when the stream was created then it will automatically
 * close and flush itself once the appropriate amount of data has been written.
 * <p/>
 * Once the listener has been set it goes into async mode, and writes become non blocking. Most methods
 * have two different code paths, based on if the listener has been set or not
 * <p/>
 * Once the write listener has been set operations must only be invoked on this stream from the write
 * listener callback. Attempting to invoke from a different thread will result in an IllegalStateException.
 * <p/>
 * Async listener tasks are queued in the {@link AsyncContextImpl}. At most one lister can be active at
 * one time, which simplifies the thread safety requirements.
 *
 * @author Stuart Douglas
 */
public class ServletOutputStreamImpl extends ServletOutputStream implements BufferWritableOutputStream {

    private final ServletRequestContext servletRequestContext;
    private Pooled<ByteBuffer> pooledBuffer;
    private ByteBuffer buffer;
    private Integer bufferSize;
    private StreamSinkChannel channel;
    private long written;
    private int state;
    private final long contentLength;
    private AsyncContextImpl asyncContext;

    private WriteListener listener;
    private WriteChannelListener internalListener;


    /**
     * buffers that are queued up to be written via async writes. This will include
     * {@link #buffer} as the first element, and maybe a user supplied buffer that
     * did not fit
     */
    private ByteBuffer[] buffersToWrite;

    private static final int FLAG_CLOSED = 1;
    private static final int FLAG_WRITE_STARTED = 1 << 1;
    private static final int FLAG_READY = 1 << 2;
    private static final int FLAG_DELEGATE_SHUTDOWN = 1 << 3;
    private static final int FLAG_IN_CALLBACK = 1 << 4;

    //TODO: should this be configurable?
    private static final int MAX_BUFFERS_TO_ALLOCATE = 6;

    private final StreamSinkChannel underlyingConnectionChannel;
    private CompositeThreadSetupAction threadSetupAction;

    /**
     * Construct a new instance.  No write timeout is configured.
     */
    public ServletOutputStreamImpl(long contentLength, final ServletRequestContext servletRequestContext) {
        this.contentLength = contentLength;
        this.underlyingConnectionChannel = servletRequestContext.getExchange().getConnection().getChannel().getSinkChannel();
        this.threadSetupAction = servletRequestContext.getDeployment().getServletContext().getDeployment().getThreadSetupAction();
        this.servletRequestContext = servletRequestContext;
    }

    /**
     * Construct a new instance.  No write timeout is configured.
     */
    public ServletOutputStreamImpl(Long contentLength, final ServletRequestContext servletRequestContext, int bufferSize) {
        this.bufferSize = bufferSize;
        this.contentLength = contentLength;
        underlyingConnectionChannel = servletRequestContext.getExchange().getConnection().getChannel().getSinkChannel();
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
        if (anyAreSet(state, FLAG_CLOSED)) {
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
                final Pool<ByteBuffer> bufferPool = servletRequestContext.getExchange().getConnection().getBufferPool();
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
                    writeBufferBlocking();
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
                            resumeWrites();
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
        if (anyAreSet(state, FLAG_CLOSED)) {
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
            if (this.written == 0 && len == contentLength) {
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
                            resumeWrites();
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

    void updateWritten(final int len) throws IOException {
        this.written += len;
        if (contentLength != -1 && this.written >= contentLength) {
            close();
        }
    }

    void updateWrittenAsync(final int len) throws IOException {
        this.written += len;
        if (contentLength != -1 && this.written >= contentLength) {
            state |= FLAG_CLOSED;
            //if buffersToWrite is set we are already flushing
            //so we don't have to do anything
            if(buffersToWrite == null) {
                if (flushBufferAsync()) {
                    channel.shutdownWrites();
                    state |= FLAG_DELEGATE_SHUTDOWN;
                    if (!channel.flush()) {
                        resumeWrites();
                    }
                } else {
                    resumeWrites();
                }
            }
        }
    }

    private void resumeWrites() {
        if (anyAreSet(state, FLAG_IN_CALLBACK)) {
            //writes will be resumed at the end of the callback
            return;
        }

        underlyingConnectionChannel.getWriteSetter().set(internalListener);
        underlyingConnectionChannel.resumeWrites();
    }

    private boolean flushBufferAsync() throws IOException {

        ByteBuffer[] bufs = buffersToWrite;
        if (bufs == null) {
            ByteBuffer buffer = this.buffer;
            if(buffer == null || buffer.position() == 0) {
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
            res = channel.write(bufs);
            written += res;
            if (res == 0) {
                //write it out with a listener
                state = state & ~FLAG_READY;
                buffersToWrite = bufs;
                return false;
            }
        } while (written < toWrite);
        buffer.clear();
        return true;
    }


    /**
     * Returns the underlying buffer. If this has not been created yet then
     * it is created.
     * <p/>
     * Callers that use this method must call {@link #updateWritten(int)} to update the written
     * amount.
     * <p/>
     * This allows the buffer to be filled directly, which can be more efficient.
     * <p/>
     * This method is basically a hack that should only be used by the print writer
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
        if (servletRequestContext.getOriginalRequest().getDispatcherType() == DispatcherType.INCLUDE) {
            return;
        }
        if (listener == null) {
            if (anyAreSet(state, FLAG_CLOSED)) {
                //just return
                return;
            }
            if (buffer != null && buffer.position() != 0) {
                writeBufferBlocking();
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
                written += res;
            } while (buffer.hasRemaining() && res != 0);
            if (!buffer.hasRemaining()) {
                channel.flush();
            }
            buffer.compact();
        }
    }

    private void writeBufferBlocking() throws IOException {
        if (channel == null) {
            channel = servletRequestContext.getExchange().getResponseChannel();
        }
        buffer.flip();
        if (buffer.hasRemaining()) {
            Channels.writeBlocking(channel, buffer);
        }
        buffer.clear();
        state |= FLAG_WRITE_STARTED;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if(servletRequestContext.getOriginalRequest().getDispatcherType() == DispatcherType.INCLUDE) {
            return;
        }
        if (listener == null) {
            if (anyAreSet(state, FLAG_CLOSED)) return;
            state |= FLAG_CLOSED;
            state &= ~FLAG_READY;
            if (allAreClear(state, FLAG_WRITE_STARTED) && channel == null) {
                if (buffer == null) {
                    servletRequestContext.getOriginalResponse().setHeader(Headers.CONTENT_LENGTH, "0");
                } else {
                    servletRequestContext.getOriginalResponse().setHeader(Headers.CONTENT_LENGTH, "" + buffer.position());
                }
            }
            try {
                if (buffer != null) {
                    writeBufferBlocking();
                }
                if (channel == null) {
                    channel = servletRequestContext.getExchange().getResponseChannel();
                }
                StreamSinkChannel channel = this.channel;
                channel.shutdownWrites();
                state |= FLAG_DELEGATE_SHUTDOWN;
                Channels.flushBlocking(channel);
            } finally {
                if (pooledBuffer != null) {
                    pooledBuffer.free();
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
     * <p/>
     * This is used in two situations, if an output stream is not closed when a
     * request is done, and when performing a close on a stream that is in async
     * mode
     *
     * @throws IOException
     */
    public void closeAsync() throws IOException {
        if (anyAreSet(state, FLAG_CLOSED)) return;

        state |= FLAG_CLOSED;
        state &= ~FLAG_READY;
        if (allAreClear(state, FLAG_WRITE_STARTED) && channel == null) {
            if (buffer == null) {
                servletRequestContext.getOriginalResponse().setHeader(Headers.CONTENT_LENGTH, "0");
            } else {
                servletRequestContext.getOriginalResponse().setHeader(Headers.CONTENT_LENGTH, "" + buffer.position());
            }
        }
        createChannel();
        if (buffer != null) {
            if (!flushBufferAsync()) {
                resumeWrites();
                return;
            }
        }
        channel.shutdownWrites();
        state |= FLAG_DELEGATE_SHUTDOWN;
        if (!channel.flush()) {
            resumeWrites();
        }
    }

    private void createChannel() {
        if (channel == null) {
            channel = servletRequestContext.getExchange().getResponseChannel();
            channel.getWriteSetter().set(internalListener);
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
            this.pooledBuffer = servletRequestContext.getExchange().getConnection().getBufferPool().allocate();
            this.buffer = pooledBuffer.getResource();
            return this.buffer;
        }
    }

    public void resetBuffer() {
        if (allAreClear(state, FLAG_WRITE_STARTED)) {
            if (pooledBuffer != null) {
                pooledBuffer.free();
                pooledBuffer = null;
            }
            buffer = null;
        } else {
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }
    }

    public void setBufferSize(final int size) {
        if (buffer != null) {
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
        final ServletRequest servletRequest = servletRequestContext.getServletRequest();
        if (!servletRequest.isAsyncStarted()) {
            throw UndertowServletMessages.MESSAGES.asyncNotStarted();
        }
        asyncContext = (AsyncContextImpl) servletRequest.getAsyncContext();
        listener = writeListener;
        //we register the write listener on the underlying connection
        //so we don't have to force the creation of the response channel
        //under normal circumstances this will break write listener delegation
        this.internalListener = new WriteChannelListener();
        underlyingConnectionChannel.getWriteSetter().set(internalListener);
        //we resume from an async task, after the request has been dispatched
        internalListener.handleEvent(underlyingConnectionChannel);
    }


    private class WriteChannelListener implements ChannelListener<StreamSinkChannel> {

        @Override
        public void handleEvent(final StreamSinkChannel theConnectionChannel) {
            theConnectionChannel.suspendWrites();
            //we run this whole thing as a async task, to avoid threading issues
            asyncContext.addAsyncTask(new Runnable() {
                @Override
                public void run() {
                    //flush the channel if it is closed
                    if (anyAreSet(state, FLAG_DELEGATE_SHUTDOWN)) {
                        try {
                            //either it will work, and the channel is closed
                            //or it won't, and we continue with writes resumed
                            if (!channel.flush()) {
                                theConnectionChannel.resumeWrites();
                            }
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
                        do {
                            try {
                                res = channel.write(buffersToWrite);
                                written += res;
                                if (res == 0) {
                                    theConnectionChannel.resumeWrites();
                                    return;
                                }
                            } catch (IOException e) {
                                handleError(e);
                                return;
                            }
                        } while (written < toWrite);
                        buffersToWrite = null;
                    }
                    if (anyAreSet(state, FLAG_CLOSED)) {
                        try {
                            channel.shutdownWrites();
                            state |= FLAG_DELEGATE_SHUTDOWN;
                            if (!channel.flush()) {
                                theConnectionChannel.resumeWrites();
                            }
                        } catch (IOException e) {
                            handleError(e);
                            return;
                        }
                    } else {


                        if (asyncContext.isDispatched()) {
                            //this is no longer an async request
                            //we just return for now
                            //TODO: what do we do here? Revert back to blocking mode?
                            return;
                        }

                        state |= FLAG_READY;
                        try {
                            state |= FLAG_IN_CALLBACK;

                            ThreadSetupAction.Handle handle = threadSetupAction.setup(servletRequestContext.getExchange());
                            try {
                                listener.onWritePossible();
                            } finally {
                                handle.tearDown();
                            }
                            theConnectionChannel.getWriteSetter().set(WriteChannelListener.this);
                            if (!isReady()) {
                                //if the stream is still ready then we do not resume writes
                                //this is per spec, we only call the listener once for each time
                                //isReady returns true
                                state &= ~FLAG_IN_CALLBACK;
                                theConnectionChannel.resumeWrites();
                            }
                        } catch (Throwable e) {
                            IoUtils.safeClose(channel);
                        } finally {
                            state &= ~FLAG_IN_CALLBACK;
                        }
                    }
                }
            });
        }

        private void handleError(final IOException e) {
            try {
                ThreadSetupAction.Handle handle = threadSetupAction.setup(servletRequestContext.getExchange());
                try {
                    listener.onError(e);
                } finally {
                    handle.tearDown();
                }
            } finally {
                IoUtils.safeClose(underlyingConnectionChannel);
            }
        }
    }

}
