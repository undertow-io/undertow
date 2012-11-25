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

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.Headers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Stuart Douglas
 */
public class ServletOutputStreamImpl extends ServletOutputStream {

    protected final ChannelFactory<StreamSinkChannel> channelFactory;
    private final HttpServletResponseImpl servletResponse;
    private boolean closed;
    private ByteBuffer buffer;
    private Pooled<ByteBuffer> pooledBuffer;
    private Integer bufferSize;
    private boolean writeStarted;
    private StreamSinkChannel channel;

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param channelFactory the channel to wrap
     */
    public ServletOutputStreamImpl(ChannelFactory<StreamSinkChannel> channelFactory, final HttpServletResponseImpl servletResponse) {
        if (channelFactory == null) {
            throw new IllegalArgumentException("Null ChannelFactory");
        }
        this.channelFactory = channelFactory;
        this.servletResponse = servletResponse;
    }

    /**
     * Construct a new instance.  No write timeout is configured.
     *
     * @param channelFactory the channel to wrap
     */
    public ServletOutputStreamImpl(ChannelFactory<StreamSinkChannel> channelFactory, final HttpServletResponseImpl servletResponse, int bufferSize) {
        if (channelFactory == null) {
            throw new IllegalArgumentException("Null ChannelFactory");
        }
        this.channelFactory = channelFactory;
        this.servletResponse = servletResponse;
        this.bufferSize = bufferSize;
    }

    private static IOException closed() {
        return new IOException("The output stream is closed");
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
        if (closed) throw closed();
        int written = 0;
        ByteBuffer buffer = buffer();
        while (written < len) {
            if (buffer.remaining() >= (len - written)) {
                buffer.put(b, off + written, len - written);
                if (buffer.remaining() == 0) {
                    writeBuffer();
                }
                return;
            } else {
                int remaining = buffer.remaining();
                buffer.put(b, off + written, remaining);
                writeBuffer();
                written += remaining;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException {
        if (closed) {
            throw closed();
        }
        if (buffer != null && buffer.position() != 0) {
            writeBuffer();
        }
        if (channel == null) {
            channel = channelFactory.create();
        }
        Channels.flushBlocking(channel);
    }

    private void writeBuffer() throws IOException {
        buffer.flip();
        if (channel == null) {
            channel = channelFactory.create();
        }
        Channels.writeBlocking(channel, buffer);
        buffer.clear();
        writeStarted = true;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (closed) return;
        try {
            closed = true;
            if (!writeStarted && channel == null) {
                if (buffer == null) {
                    servletResponse.setHeader(Headers.CONTENT_LENGTH, "0");
                } else {
                    servletResponse.setHeader(Headers.CONTENT_LENGTH, "" + buffer.position());
                }
            }
            if (buffer != null) {
                writeBuffer();
            }
            if (channel == null) {
                channel = channelFactory.create();
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
     * @throws IOException
     */
    public void closeAsync(final HttpCompletionHandler handler) throws IOException {
        if (closed) {
            handler.handleComplete();
            return;
        }
        closed = true;
        if (!writeStarted && channel == null) {
            if (buffer == null) {
                servletResponse.setHeader(Headers.CONTENT_LENGTH, "0");
            } else {
                servletResponse.setHeader(Headers.CONTENT_LENGTH, "" + buffer.position());
            }
        }

        if (channel == null) {
            channel = channelFactory.create();
        }
        if (buffer != null) {
            buffer.flip();
            try {
                int res = 0;
                do {
                    res = channel.write(buffer);
                    if(!buffer.hasRemaining()) {
                        if(pooledBuffer != null) {
                            pooledBuffer.free();
                        }
                        HttpHandlers.flushAndCompleteRequest(channel, handler);
                        return;
                    }
                } while (res > 0);

                if(res == 0) {
                    channel.getWriteSetter().set( new ChannelListener<StreamSinkChannel>() {
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
                                    handler.handleComplete();
                                    return;
                                } finally {
                                    if (! ok) {
                                        if(pooledBuffer != null) {
                                            pooledBuffer.free();
                                        }
                                    }
                                }
                                if (result == 0) {
                                    return;
                                }
                                if(result == -1) {
                                    channel.suspendWrites();
                                    IoUtils.safeClose(channel);
                                    handler.handleComplete();
                                }
                            } while (buffer.hasRemaining());
                            if(pooledBuffer != null) {
                                pooledBuffer.free();
                            }
                            HttpHandlers.flushAndCompleteRequest(channel, handler);
                        }

                    });
                    channel.resumeWrites();
                } else if(res == -1) {
                    IoUtils.safeClose(channel);
                    handler.handleComplete();
                } else {
                    buffer = null;
                    pooledBuffer = null;
                }
            } catch (IOException e) {
                IoUtils.safeClose(channel);
                handler.handleComplete();
            }
        } else {
            HttpHandlers.flushAndCompleteRequest(channel, handler);
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
            this.pooledBuffer = servletResponse.getExchange().getExchange().getConnection().getBufferPool().allocate();
            this.buffer = pooledBuffer.getResource();
            return this.buffer;
        }
    }

    public void resetBuffer() {
        if (!writeStarted) {
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
        return closed;
    }
}
