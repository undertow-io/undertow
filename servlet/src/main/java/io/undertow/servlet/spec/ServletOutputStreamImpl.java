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

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.ThreadSetupAction;
import io.undertow.servlet.core.CompositeThreadSetupAction;
import io.undertow.util.Headers;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
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
public class ServletOutputStreamImpl extends ServletOutputStream {

    private final HttpServletResponseImpl servletResponse;
    private Pooled<ByteBuffer> pooledBuffer;
    private ByteBuffer buffer;
    private Integer bufferSize;
    private StreamSinkChannel channel;
    private long written;
    private int state;
    private final Long contentLength;
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

    private final StreamSinkChannel underlyingConnectionChannel;
    private CompositeThreadSetupAction threadSetupAction;

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param channelFactory the channel to wrap
     */
    public ServletOutputStreamImpl(Long contentLength, final HttpServletResponseImpl servletResponse) {
        this.servletResponse = servletResponse;
        this.contentLength = contentLength;
        this.underlyingConnectionChannel = servletResponse.getExchange().getConnection().getChannel().getSinkChannel();
        this.threadSetupAction = servletResponse.getServletContext().getDeployment().getThreadSetupAction();
    }

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param channelFactory the channel to wrap
     */
    public ServletOutputStreamImpl(Long contentLength, final HttpServletResponseImpl servletResponse, int bufferSize) {
        this.servletResponse = servletResponse;
        this.bufferSize = bufferSize;
        this.contentLength = contentLength;
        underlyingConnectionChannel = servletResponse.getExchange().getConnection().getChannel().getSinkChannel();
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
        if (len <1) {
            return;
        }

        if (listener == null) {
            if (len < 1) {
                return;
            }
            int written = 0;
            ByteBuffer buffer = buffer();
            while (written < len) {
                if (buffer.remaining() >= (len - written)) {
                    buffer.put(b, off + written, len - written);
                    if (buffer.remaining() == 0) {
                        writeBufferBlocking();
                    }
                    updateWritten(len);
                    return;
                } else {
                    int remaining = buffer.remaining();
                    buffer.put(b, off + written, remaining);
                    writeBufferBlocking();
                    written += remaining;
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
                            if (userBuffer != null) {
                                //but we need to copy any extra data
                                final ByteBuffer copy = ByteBuffer.allocate(userBuffer.remaining());
                                copy.put(userBuffer);
                                copy.flip();

                                this.buffersToWrite = new ByteBuffer[]{buffer, copy};
                            } else {
                                buffersToWrite = bufs;
                            }
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

    void updateWritten(final int len) throws IOException {
        this.written += len;
        if (contentLength != null && this.written >= contentLength) {
            flush();
            close();
        }
    }

    void updateWrittenAsync(final int len) throws IOException {
        this.written += len;
        if (contentLength != null && this.written >= contentLength) {
            state |= FLAG_CLOSED;
            if (flushBufferAsync()) {
                channel.shutdownWrites();
                state |= FLAG_DELEGATE_SHUTDOWN;
                if (!channel.flush()) {
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
            ByteBuffer buffer = buffer();
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

        if (listener == null) {
            if (anyAreSet(state, FLAG_CLOSED)) {
                //just return
                return;
            }
            if (buffer != null && buffer.position() != 0) {
                writeBufferBlocking();
            }
            if (channel == null) {
                channel = servletResponse.getExchange().getResponseChannel();
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
        buffer.flip();
        if (channel == null) {
            channel = servletResponse.getExchange().getResponseChannel();
        }
        Channels.writeBlocking(channel, buffer);
        buffer.clear();
        state |= FLAG_WRITE_STARTED;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (listener == null) {
            if (anyAreSet(state, FLAG_CLOSED)) return;
            state |= FLAG_CLOSED;
            state &= ~FLAG_READY;
            if (allAreClear(state, FLAG_WRITE_STARTED) && channel == null) {
                if (buffer == null) {
                    servletResponse.setHeader(Headers.CONTENT_LENGTH, "0");
                } else {
                    servletResponse.setHeader(Headers.CONTENT_LENGTH, "" + buffer.position());
                }
            }
            try {
                if (buffer != null) {
                    writeBufferBlocking();
                }
                if (channel == null) {
                    channel = servletResponse.getExchange().getResponseChannel();
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
                servletResponse.setHeader(Headers.CONTENT_LENGTH, "0");
            } else {
                servletResponse.setHeader(Headers.CONTENT_LENGTH, "" + buffer.position());
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
            channel = servletResponse.getExchange().getResponseChannel();
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
            this.pooledBuffer = servletResponse.getExchange().getConnection().getBufferPool().allocate();
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
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("writeListener");
        }
        if (listener != null) {
            throw UndertowServletMessages.MESSAGES.listenerAlreadySet();
        }
        final HttpServletRequestImpl servletRequest = HttpServletRequestImpl.getRequestImpl(this.servletResponse.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        if (!servletRequest.isAsyncStarted()) {
            throw UndertowServletMessages.MESSAGES.asyncNotStarted();
        }
        asyncContext = servletRequest.getAsyncContext();
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

                            ThreadSetupAction.Handle handle = threadSetupAction.setup(servletResponse.getExchange());
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
                ThreadSetupAction.Handle handle = threadSetupAction.setup(servletResponse.getExchange());
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
