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

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.xnio.Buffers;
import org.xnio.IoUtils;

/**
 * A sender that uses an output stream.
 *
 * @author Stuart Douglas
 */
public class BlockingSenderImpl implements Sender {

    private final HttpServerExchange exchange;
    private final OutputStream outputStream;
    private volatile Thread inCall;
    private volatile Thread sendThread;
    private ByteBuffer[] next;
    private FileChannel pendingFile;
    private IoCallback queuedCallback;

    public BlockingSenderImpl(final HttpServerExchange exchange, final OutputStream outputStream) {
        this.exchange = exchange;
        this.outputStream = outputStream;
    }

    @Override
    public void send(final ByteBuffer buffer, final IoCallback callback) {
        sendThread = Thread.currentThread();
        if (inCall == Thread.currentThread()) {
            queue(new ByteBuffer[]{buffer}, callback);
            return;
        } else {
            long responseContentLength = exchange.getResponseContentLength();
            if(responseContentLength > 0 && buffer.remaining() > responseContentLength) {
                callback.onException(exchange, this, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(buffer.remaining(), responseContentLength));
                return;
            }
            if (!exchange.isResponseStarted() && callback == IoCallback.END_EXCHANGE) {
                if (responseContentLength == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                    exchange.setResponseContentLength(buffer.remaining());
                }
            }
        }
        if (writeBuffer(buffer, callback)) {
            invokeOnComplete(callback);
        }
    }


    @Override
    public void send(final ByteBuffer[] buffer, final IoCallback callback) {
        sendThread = Thread.currentThread();
        if (inCall == Thread.currentThread()) {
            queue(buffer, callback);
            return;
        } else {
            long responseContentLength = exchange.getResponseContentLength();
            if(responseContentLength > 0 && Buffers.remaining(buffer) > responseContentLength) {
                callback.onException(exchange, this, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(Buffers.remaining(buffer), responseContentLength));
                return;
            }
            if (!exchange.isResponseStarted() && callback == IoCallback.END_EXCHANGE) {
                if (responseContentLength == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                    exchange.setResponseContentLength(Buffers.remaining(buffer));
                }
            }
        }
        if (!writeBuffer(buffer, callback)) {
            return;
        }
        invokeOnComplete(callback);
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
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        sendThread = Thread.currentThread();
        if (inCall == Thread.currentThread()) {
            queue(new ByteBuffer[]{ByteBuffer.wrap(bytes)}, callback);
            return;
        } else {
            long responseContentLength = exchange.getResponseContentLength();
            if(responseContentLength > 0 && bytes.length > responseContentLength) {
                callback.onException(exchange, this, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(bytes.length, responseContentLength));
                return;
            }
            if (!exchange.isResponseStarted() && callback == IoCallback.END_EXCHANGE) {
                if (responseContentLength == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                    exchange.setResponseContentLength(bytes.length);
                }
            }
        }
        try {
            outputStream.write(bytes);
            invokeOnComplete(callback);
        } catch (IOException e) {
            callback.onException(exchange, this, e);
        }
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        byte[] bytes = data.getBytes(charset);
        sendThread = Thread.currentThread();
        if (inCall == Thread.currentThread()) {
            queue(new ByteBuffer[]{ByteBuffer.wrap(bytes)}, callback);
            return;
        }else {
            long responseContentLength = exchange.getResponseContentLength();
            if(responseContentLength > 0 && bytes.length > responseContentLength) {
                callback.onException(exchange, this, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(bytes.length, responseContentLength));
                return;
            }
            if (!exchange.isResponseStarted() && callback == IoCallback.END_EXCHANGE) {
                if (responseContentLength == -1 && !exchange.getResponseHeaders().contains(Headers.TRANSFER_ENCODING)) {
                    exchange.setResponseContentLength(bytes.length);
                }
            }
        }
        try {
            outputStream.write(bytes);
            invokeOnComplete(callback);
        } catch (IOException e) {
            callback.onException(exchange, this, e);
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
    public void transferFrom(FileChannel source, IoCallback callback) {
        sendThread = Thread.currentThread();
        if (inCall == Thread.currentThread()) {
            queue(source, callback);
            return;
        }
        performTransfer(source, callback);
        invokeOnComplete(callback);
    }

    private void performTransfer(FileChannel source, IoCallback callback) {
        if (outputStream instanceof BufferWritableOutputStream) {
            try {
                ((BufferWritableOutputStream) outputStream).transferFrom(source);
            } catch (IOException e) {
                callback.onException(exchange, this, e);
            }
        } else {
            try (PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().getArrayBackedPool().allocate()){
                ByteBuffer buffer = pooled.getBuffer();
                long pos = source.position();
                long size = source.size();
                while (size - pos > 0) {
                    int ret = source.read(buffer);
                    if (ret <= 0) {
                        break;
                    }
                    pos += ret;
                    outputStream.write(buffer.array(), buffer.arrayOffset(), ret);
                    buffer.clear();
                }

                if (pos != size) {
                    throw new EOFException("Unexpected EOF reading file");
                }

            }  catch (IOException e) {
                callback.onException(exchange, this, e);
            }
        }
    }

    @Override
    public void close(final IoCallback callback) {
        try {
            outputStream.close();
            invokeOnComplete(callback);
        } catch (IOException e) {
            callback.onException(exchange, this, e);
        }
    }

    @Override
    public void close() {
        IoUtils.safeClose(outputStream);
    }

    private boolean writeBuffer(final ByteBuffer buffer, final IoCallback callback) {
        return writeBuffer(new ByteBuffer[]{buffer}, callback);
    }

    private boolean writeBuffer(final ByteBuffer[] buffers, final IoCallback callback) {
        if (outputStream instanceof BufferWritableOutputStream) {
            //fast path, if the stream can take a buffer directly just write to it
            try {
                ((BufferWritableOutputStream) outputStream).write(buffers);
                return true;
            } catch (IOException e) {
                callback.onException(exchange, this, e);
                return false;
            }
        }
        for (ByteBuffer buffer : buffers) {
            if (buffer.hasArray()) {
                try {
                    outputStream.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
                } catch (IOException e) {
                    callback.onException(exchange, this, e);
                    return false;
                }
            } else {
                try (PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().getArrayBackedPool().allocate()) {
                    while (buffer.hasRemaining()) {
                        int toRead = Math.min(buffer.remaining(), pooled.getBuffer().remaining());
                        buffer.get(pooled.getBuffer().array(), pooled.getBuffer().arrayOffset(), toRead);
                        try {
                            outputStream.write(pooled.getBuffer().array(), pooled.getBuffer().arrayOffset(), toRead);
                        } catch (IOException e) {
                            callback.onException(exchange, this, e);
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }


    private void invokeOnComplete(final IoCallback callback) {
        sendThread = null;
        inCall = Thread.currentThread();
        try {
            callback.onComplete(exchange, this);
        } finally {
            inCall = null;
        }
        if (Thread.currentThread() != sendThread) {
            return;
        }
        while (next != null || pendingFile != null) {
            ByteBuffer[] next = this.next;
            IoCallback queuedCallback = this.queuedCallback;
            FileChannel file = this.pendingFile;
            this.next = null;
            this.queuedCallback = null;
            this.pendingFile = null;

            if (next != null) {
                for (ByteBuffer buffer : next) {
                    writeBuffer(buffer, queuedCallback);
                }
            } else if (file != null) {
                performTransfer(file, queuedCallback);
            }
            sendThread = null;
            inCall = Thread.currentThread();
            try {
                queuedCallback.onComplete(exchange, this);
            } finally {
                inCall = null;
            }
            if (Thread.currentThread() != sendThread) {
                return;
            }
        }
    }

    private void queue(final ByteBuffer[] byteBuffers, final IoCallback ioCallback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (next != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        next = byteBuffers;
        queuedCallback = ioCallback;
    }

    private void queue(final FileChannel source, final IoCallback ioCallback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (pendingFile != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        pendingFile = source;
        queuedCallback = ioCallback;
    }

}
