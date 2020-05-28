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

package io.undertow.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.xnio.Buffers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
public class AsyncSenderImpl implements Sender {

    private StreamSinkChannel channel;
    private final HttpServerExchange exchange;
    private PooledByteBuffer[] pooledBuffers = null;
    private FileChannel fileChannel;
    private IoCallback callback;

    private ByteBuffer[] buffer;

    /**
     * This object is not intended to be used in a multi threaded manner
     * however as we run code after the callback it is possible that another
     * thread may call send while we are still running
     * we use the 'writeThread' state guard to protect against this happening.
     *
     * During a send() call the 'writeThread' object is set first, followed by the
     * buffer. The inCallback variable is used to determine if the current thread
     * is in the process of running a callback.
     *
     * After the callback has been invoked the thread that initiated the callback
     * will only continue to process if it is the writeThread.
     *
     */
    private volatile Thread writeThread;
    private volatile Thread inCallback;

    private ChannelListener<StreamSinkChannel> writeListener;

    public class TransferTask implements Runnable, ChannelListener<StreamSinkChannel> {
        public boolean run(boolean complete) {
            try {
                FileChannel source = fileChannel;
                long pos = source.position();
                long size = source.size();

                StreamSinkChannel dest = channel;
                if (dest == null) {
                    if (callback == IoCallback.END_EXCHANGE) {
                        if (exchange.getResponseContentLength() == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                            exchange.setResponseContentLength(size);
                        }
                    }
                    channel = dest = exchange.getResponseChannel();
                    if (dest == null) {
                        throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
                    }
                }

                while (size - pos > 0) {
                    long ret = dest.transferFrom(source, pos, size - pos);
                    pos += ret;
                    if (ret == 0) {
                        source.position(pos);
                        dest.getWriteSetter().set(this);
                        dest.resumeWrites();
                        return false;
                    }
                }

                if (complete) {
                    invokeOnComplete();
                }
            } catch (IOException e) {
                invokeOnException(callback, e);
            }

            return true;
        }

        @Override
        public void handleEvent(StreamSinkChannel channel) {
            channel.suspendWrites();
            channel.getWriteSetter().set(null);
            exchange.dispatch(this);
        }

        @Override
        public void run() {
            run(true);
        }
    }

    private TransferTask transferTask;


    public AsyncSenderImpl(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }


    @Override
    public void send(final ByteBuffer buffer, final IoCallback callback) {
        writeThread = Thread.currentThread();
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if(!exchange.getConnection().isOpen()) {
            invokeOnException(callback, new ClosedChannelException());
            return;
        }
        if(exchange.isResponseComplete()) {
            invokeOnException(callback, new IOException(UndertowMessages.MESSAGES.responseComplete()));
        }
        if (this.buffer != null || this.fileChannel != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        long responseContentLength = exchange.getResponseContentLength();
        if(responseContentLength > 0 && buffer.remaining() > responseContentLength) {
            invokeOnException(callback, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(buffer.remaining(), responseContentLength));
            return;
        }
        StreamSinkChannel channel = this.channel;
        if (channel == null) {
            if (callback == IoCallback.END_EXCHANGE) {
                if (responseContentLength == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                    exchange.setResponseContentLength(buffer.remaining());
                }
            }
            this.channel = channel = exchange.getResponseChannel();
            if (channel == null) {
                throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
            }
        }
        this.callback = callback;
        if (inCallback == Thread.currentThread()) {
            this.buffer = new ByteBuffer[]{buffer};
            return;
        }
        try {
            do {
                if (buffer.remaining() == 0) {
                    callback.onComplete(exchange, this);
                    return;
                }
                int res = channel.write(buffer);
                if (res == 0) {
                    this.buffer = new ByteBuffer[]{buffer};
                    this.callback = callback;

                    if(writeListener == null) {
                        initWriteListener();
                    }
                    channel.getWriteSetter().set(writeListener);
                    channel.resumeWrites();
                    return;
                }
            } while (buffer.hasRemaining());
            invokeOnComplete();

        } catch (IOException e) {

            invokeOnException(callback, e);
        }
    }

    @Override
    public void send(final ByteBuffer[] buffer, final IoCallback callback) {
        writeThread = Thread.currentThread();
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }

        if(!exchange.getConnection().isOpen()) {
            invokeOnException(callback, new ClosedChannelException());
            return;
        }
        if(exchange.isResponseComplete()) {
            invokeOnException(callback, new IOException(UndertowMessages.MESSAGES.responseComplete()));
        }
        if (this.buffer != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        this.callback = callback;
        if (inCallback == Thread.currentThread()) {
            this.buffer = buffer;
            return;
        }

        long totalToWrite = Buffers.remaining(buffer);
        long responseContentLength = exchange.getResponseContentLength();
        if(responseContentLength > 0 && totalToWrite > responseContentLength) {
            invokeOnException(callback, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(totalToWrite, responseContentLength));
            return;
        }

        StreamSinkChannel channel = this.channel;
        if (channel == null) {
            if (callback == IoCallback.END_EXCHANGE) {
                if (responseContentLength == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                    exchange.setResponseContentLength(totalToWrite);
                }
            }
            this.channel = channel = exchange.getResponseChannel();
            if (channel == null) {
                throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
            }
        }

        final long total = totalToWrite;
        long written = 0;

        try {
            do {
                long res = channel.write(buffer);
                written += res;
                if (res == 0) {
                    this.buffer = buffer;
                    this.callback = callback;

                    if(writeListener == null) {
                        initWriteListener();
                    }
                    channel.getWriteSetter().set(writeListener);
                    channel.resumeWrites();
                    return;
                }
            } while (written < total);
            invokeOnComplete();

        } catch (IOException e) {
            invokeOnException(callback, e);
        }
    }


    @Override
    public void transferFrom(FileChannel source, IoCallback callback) {
        writeThread = Thread.currentThread();
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }

        if(!exchange.getConnection().isOpen()) {
            invokeOnException(callback, new ClosedChannelException());
            return;
        }
        if(exchange.isResponseComplete()) {
            invokeOnException(callback, new IOException(UndertowMessages.MESSAGES.responseComplete()));
        }
        if (this.fileChannel != null || this.buffer != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }

        this.callback = callback;
        this.fileChannel = source;
        if (inCallback == Thread.currentThread()) {
            return;
        }
        if(transferTask == null) {
            transferTask = new TransferTask();
        }
        if (exchange.isInIoThread()) {
            exchange.dispatch(transferTask);
            return;
        }

        transferTask.run();
    }

    @Override
    public void send(final ByteBuffer buffer) {
        send(buffer, IoCallback.END_EXCHANGE);
    }

    @Override
    public void send(final ByteBuffer[] buffer) {
        send(buffer, IoCallback.END_EXCHANGE);
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        send(data, StandardCharsets.UTF_8, callback);
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        writeThread = Thread.currentThread();
        if(!exchange.getConnection().isOpen()) {
            invokeOnException(callback, new ClosedChannelException());
            return;
        }
        if(exchange.isResponseComplete()) {
            invokeOnException(callback, new IOException(UndertowMessages.MESSAGES.responseComplete()));
        }
        ByteBuffer bytes = ByteBuffer.wrap(data.getBytes(charset));
        if (bytes.remaining() == 0) {
            callback.onComplete(exchange, this);
        } else {
            int i = 0;
            ByteBuffer[] bufs = null;
            while (bytes.hasRemaining()) {
                PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();
                if (bufs == null) {
                    int noBufs = (bytes.remaining() + pooled.getBuffer().remaining() - 1) / pooled.getBuffer().remaining(); //round up division trick
                    pooledBuffers = new PooledByteBuffer[noBufs];
                    bufs = new ByteBuffer[noBufs];
                }
                pooledBuffers[i] = pooled;
                bufs[i] = pooled.getBuffer();
                Buffers.copy(pooled.getBuffer(), bytes);
                pooled.getBuffer().flip();
                ++i;
            }
            send(bufs, callback);
        }
    }

    @Override
    public void send(final String data) {
        send(data, IoCallback.END_EXCHANGE);
    }

    @Override
    public void send(final String data, final Charset charset) {
        send(data, charset, IoCallback.END_EXCHANGE);
    }

    @Override
    public void close(final IoCallback callback) {
        try {
            StreamSinkChannel channel = this.channel;
            if (channel == null) {
                if (exchange.getResponseContentLength() == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                    exchange.setResponseContentLength(0);
                }
                this.channel = channel = exchange.getResponseChannel();
                if (channel == null) {
                    throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
                }
            }
            channel.shutdownWrites();
            if (!channel.flush()) {
                channel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                        new ChannelListener<StreamSinkChannel>() {
                            @Override
                            public void handleEvent(final StreamSinkChannel channel) {
                                if(callback != null) {
                                    callback.onComplete(exchange, AsyncSenderImpl.this);
                                }
                            }
                        }, new ChannelExceptionHandler<StreamSinkChannel>() {
                            @Override
                            public void handleException(final StreamSinkChannel channel, final IOException exception) {
                                try {
                                    if(callback != null) {
                                        invokeOnException(callback, exception);
                                    }
                                } finally {
                                    IoUtils.safeClose(channel);
                                }
                            }
                        }
                ));
                channel.resumeWrites();
            } else {
                if (callback != null) {
                    callback.onComplete(exchange, this);
                }
            }
        } catch (IOException e) {
            if (callback != null) {
                invokeOnException(callback, e);
            }
        }
    }

    @Override
    public void close() {
        close(null);
    }

    /**
     * Invokes the onComplete method. If send is called again in onComplete then
     * we loop and write it out. This prevents possible stack overflows due to recursion
     */
    private void invokeOnComplete() {
        for (; ; ) {
            if (pooledBuffers != null) {
                for (PooledByteBuffer buffer : pooledBuffers) {
                    buffer.close();
                }
                pooledBuffers = null;
            }
            IoCallback callback = this.callback;
            this.buffer = null;
            this.fileChannel = null;
            this.callback = null;
            writeThread = null;
            inCallback = Thread.currentThread();
            try {
                callback.onComplete(exchange, this);
            } finally {
                inCallback = null;
            }
            if (Thread.currentThread() != writeThread) {
                return;
            }

            StreamSinkChannel channel = this.channel;
            if (this.buffer != null) {
                long t = Buffers.remaining(buffer);
                final long total = t;
                long written = 0;

                try {
                    do {
                        long res = channel.write(buffer);
                        written += res;
                        if (res == 0) {
                            if(writeListener == null) {
                                initWriteListener();
                            }
                            channel.getWriteSetter().set(writeListener);
                            channel.resumeWrites();
                            return;
                        }
                    } while (written < total);
                    //we loop and invoke onComplete again
                } catch (IOException e) {
                    invokeOnException(callback, e);
                }
            } else if (this.fileChannel != null) {
                if(transferTask == null) {
                    transferTask = new TransferTask();
                }
                if (!transferTask.run(false)) {
                    return;
                }
            } else {
                return;
            }

        }
    }


    private void invokeOnException(IoCallback callback, IOException e) {

        if (pooledBuffers != null) {
            for (PooledByteBuffer buffer : pooledBuffers) {
                buffer.close();
            }
            pooledBuffers = null;
        }
        callback.onException(exchange, this, e);
    }

    private void initWriteListener() {
        writeListener = new ChannelListener<StreamSinkChannel>() {
            @Override
            public void handleEvent(final StreamSinkChannel streamSinkChannel) {
                try {
                    long toWrite = Buffers.remaining(buffer);
                    long written = 0;
                    while (written < toWrite) {
                        long res = streamSinkChannel.write(buffer, 0, buffer.length);
                        written += res;
                        if (res == 0) {
                            return;
                        }
                    }
                    streamSinkChannel.suspendWrites();
                    invokeOnComplete();
                } catch (IOException e) {
                    streamSinkChannel.suspendWrites();
                    invokeOnException(callback, e);
                }
            }
        };
    }
}
