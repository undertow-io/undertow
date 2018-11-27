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
import java.util.function.BiConsumer;

import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSinkChannel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
public class AsyncSenderImpl implements Sender {

    private final HttpServerExchange exchange;
    private ByteBuf[] pooledBuffers = null;
    private FileChannel fileChannel;
    private IoCallback callback;
    private boolean inCallback;

    public AsyncSenderImpl(final HttpServerExchange exchange) {
        this.exchange = exchange;
    }


    @Override
    public void send(final ByteBuf buffer, final IoCallback callback) {
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }
        if (!exchange.getConnection().isOpen()) {
            invokeOnException(callback, new ClosedChannelException());
            return;
        }
        if (exchange.isResponseComplete()) {
            invokeOnException(callback, new IOException(UndertowMessages.MESSAGES.responseComplete()));
        }
        if (this.pooledBuffers != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        long responseContentLength = exchange.getResponseContentLength();
        if (responseContentLength > 0 && buffer.readableBytes() > responseContentLength) {
            invokeOnException(callback, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(buffer.readableBytes(), responseContentLength));
            return;
        }


        this.callback = callback;
        if (inCallback) {
            this.pooledBuffers = new ByteBuf[]{buffer};
            return;
        }
        exchange.writeAsync(buffer, callback == IoCallback.END_EXCHANGE)
                .addListener(new GenericFutureListener<Future<? super Void>>() {
                    @Override
                    public void operationComplete(Future<? super Void> future) throws Exception {
                        if (future.isSuccess()) {
                            invokeOnComplete();
                        } else {
                            invokeOnException(callback, new IOException(future.cause()));
                        }
                    }
                });
    }

    @Override
    public void send(final ByteBuf[] buffer, final IoCallback callback) {
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }

        if (!exchange.getConnection().isOpen()) {
            invokeOnException(callback, new ClosedChannelException());
            return;
        }
        if (exchange.isResponseComplete()) {
            invokeOnException(callback, new IOException(UndertowMessages.MESSAGES.responseComplete()));
        }
        if (this.pooledBuffers != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        this.callback = callback;
        if (inCallback) {
            this.pooledBuffers = buffer;
            return;
        }

        long totalToWrite = ByteBufUtil.re.remaining(buffer);
        long responseContentLength = exchange.getResponseContentLength();
        if (responseContentLength > 0 && totalToWrite > responseContentLength) {
            invokeOnException(callback, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(totalToWrite, responseContentLength));
            return;
        }

        IoSink channel = this.channel;
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

                    if (writeListener == null) {
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
        if (callback == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("callback");
        }

        if (!exchange.getConnection().isOpen()) {
            invokeOnException(callback, new ClosedChannelException());
            return;
        }
        if (exchange.isResponseComplete()) {
            invokeOnException(callback, new IOException(UndertowMessages.MESSAGES.responseComplete()));
        }
        if (this.fileChannel != null || this.buffer != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }

        this.callback = callback;
        this.fileChannel = source;
        if (inCallback) {
            return;
        }
        if (transferTask == null) {
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

        if (!exchange.getConnection().isOpen()) {
            invokeOnException(callback, new ClosedChannelException());
            return;
        }
        if (exchange.isResponseComplete()) {
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
        IoSink channel = this.channel;
        if (channel == null) {
            if (exchange.getResponseContentLength() == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                exchange.setResponseContentLength(0);
            }
            this.channel = channel = exchange.getResponseChannel();
            if (channel == null) {
                throw UndertowMessages.MESSAGES.responseChannelAlreadyProvided();
            }
        }
        channel.close().whenComplete(new BiConsumer<Void, Throwable>() {
            @Override
            public void accept(Void aVoid, Throwable exception) {
                if (exception != null) {
                    if (callback != null) {
                        invokeOnException(callback, new IOException(exception));
                    }
                } else {
                    if (callback != null) {
                        callback.onComplete(exchange, AsyncSenderImpl.this);
                    }
                }
            }
        });
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
            inCallback = true;
            try {
                callback.onComplete(exchange, this);
            } finally {
                inCallback = false;
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
                            if (writeListener == null) {
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
                if (transferTask == null) {
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
