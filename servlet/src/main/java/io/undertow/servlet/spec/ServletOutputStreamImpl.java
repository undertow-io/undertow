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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.servlet.ServletOutputStream;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.util.Headers;
import org.xnio.Bits;
import org.xnio.Pooled;
import org.xnio.channels.ChannelFactory;
import org.xnio.channels.Channels;
import org.xnio.channels.ConcurrentStreamChannelAccessException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.WriteTimeoutException;
import org.xnio.streams.ChannelOutputStream;

/**
 * @author Stuart Douglas
 */
public class ServletOutputStreamImpl extends ServletOutputStream {

    protected final ChannelFactory<StreamSinkChannel> channelFactory;
    private final HttpServletResponseImpl servletResponse;
    private volatile boolean closed;
    private volatile ByteBuffer buffer;
    private volatile Pooled<ByteBuffer> pooledBuffer;
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
        if(channel == null) {
            channel = channelFactory.create();
        }
        Channels.flushBlocking(channel);
    }

    private void writeBuffer() throws IOException {
        buffer.flip();
        if(channel == null) {
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
        closed = true;
        if(!writeStarted && channel == null) {
            if(buffer == null) {
                servletResponse.setHeader(Headers.CONTENT_LENGTH, "0");
            } else {
                servletResponse.setHeader(Headers.CONTENT_LENGTH, "" + buffer.position());
            }
        }
        writeBuffer();
        StreamSinkChannel channel = this.channel;
        channel.shutdownWrites();
        Channels.flushBlocking(channel);
        if (pooledBuffer != null) {
            pooledBuffer.free();
            buffer = null;
        } else {
            buffer = null;
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
}
