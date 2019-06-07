/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.util.HttpHeaderNames;
import io.undertow.util.IoUtils;

/**
 * A sender that uses an output stream.
 *
 * @author Stuart Douglas
 */
public class BlockingSenderImpl implements Sender {

    private final HttpServerExchange exchange;
    private final OutputStream outputStream;
    private boolean inCall;
    private ByteBuf next;
    private RandomAccessFile pendingFile;
    private IoCallback queuedCallback;
    private long pendingFileStart;
    private long pendingFileEnd;

    public BlockingSenderImpl(final HttpServerExchange exchange, final OutputStream outputStream) {
        this.exchange = exchange;
        this.outputStream = outputStream;
    }

    @Override
    public void send(final ByteBuf buffer, final IoCallback callback) {
        if (inCall) {
            queue(buffer, callback);
            return;
        } else {
            long responseContentLength = exchange.getResponseContentLength();
            if (responseContentLength > 0 && buffer.readableBytes() > responseContentLength) {
                callback.onException(exchange, null, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(buffer.readableBytes(), responseContentLength));
                return;
            }
            if (!exchange.isResponseStarted() && callback == IoCallback.END_EXCHANGE) {
                if (responseContentLength == -1 && !exchange.responseHeaders().contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                    exchange.setResponseContentLength(buffer.readableBytes());
                }
            }
        }
        if (writeBuffer(buffer, callback)) {
            invokeOnComplete(callback);
        }
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        if (inCall) {
            queue(Unpooled.copiedBuffer(data, charset), callback);
            return;
        }
        byte[] bytes = data.getBytes(charset);
        long responseContentLength = exchange.getResponseContentLength();
        if (responseContentLength > 0 && bytes.length > responseContentLength) {
            callback.onException(exchange, null, UndertowLogger.ROOT_LOGGER.dataLargerThanContentLength(bytes.length, responseContentLength));
            return;
        }
        if (!exchange.isResponseStarted() && callback == IoCallback.END_EXCHANGE) {
            if (responseContentLength == -1 && !exchange.responseHeaders().contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                exchange.setResponseContentLength(bytes.length);
            }
        }

        try {
            outputStream.write(bytes);
            invokeOnComplete(callback);
        } catch (IOException e) {
            callback.onException(exchange, null, e);
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
    public void transferFrom(RandomAccessFile source, IoCallback callback) {
        try {
            if (inCall) {
                queue(source, callback, 0, source.length());
                return;
            }
            performTransfer(source, callback, 0, source.length());
        } catch (IOException e) {
            callback.onException(exchange, null, e);
            return;
        }
        invokeOnComplete(callback);
    }

    @Override
    public void transferFrom(RandomAccessFile channel, long start, long length, IoCallback callback) {

        if (inCall) {
            queue(channel, callback, start, length);
            return;
        }
        performTransfer(channel, callback, start, length);
        invokeOnComplete(callback);
    }

    private void performTransfer(RandomAccessFile file, IoCallback callback, long start, long length) {
        ByteBuf buffer = exchange.getConnection().allocateBuffer(false);
        FileChannel source = file.getChannel();
        try {
            source.position(start);
            long pos = start;
            while (length - pos > 0) {
                int ret = source.read(buffer.nioBuffer(0, buffer.writableBytes()));
                if (ret <= 0) {
                    break;
                }
                int toWrite = ret;
                if (ret > length - pos) {
                    toWrite = (int) (length - pos);
                }
                pos += ret;
                outputStream.write(buffer.array(), buffer.arrayOffset(), toWrite);
            }

            if (pos != length) {
                throw new EOFException("Unexpected EOF reading file");
            }

        } catch (IOException e) {
            callback.onException(exchange, null, e);
        } finally {
            buffer.release();
        }

    }

    @Override
    public void close(final IoCallback callback) {
        try {
            outputStream.close();
            invokeOnComplete(callback);
        } catch (IOException e) {
            callback.onException(exchange, null, e);
        }
    }

    @Override
    public void close() {
        IoUtils.safeClose(outputStream);
    }

    private boolean writeBuffer(final ByteBuf buffer, final IoCallback callback) {
        if (buffer.hasArray()) {
            try {
                outputStream.write(buffer.array(), buffer.arrayOffset(), buffer.readableBytes());
            } catch (IOException e) {
                callback.onException(exchange, null, e);
                return false;
            }
        } else {
            ByteBuf pooled = exchange.getConnection().allocateBuffer(false);
            try {
                while (buffer.isReadable()) {
                    int toRead = Math.min(buffer.readableBytes(), pooled.writableBytes());
                    buffer.readBytes(pooled, toRead);
                    try {
                        outputStream.write(pooled.array(), pooled.arrayOffset(), toRead);
                    } catch (IOException e) {
                        callback.onException(exchange, null, e);
                        return false;
                    }
                }
            } finally {
                pooled.release();
            }
        }

        return true;
    }


    private void invokeOnComplete(final IoCallback callback) {
        inCall = true;
        try {
            callback.onComplete(exchange, null);
        } finally {
            inCall = false;
        }
        while (next != null || pendingFile != null) {
            ByteBuf next = this.next;
            IoCallback queuedCallback = this.queuedCallback;
            RandomAccessFile file = this.pendingFile;
            this.next = null;
            this.queuedCallback = null;
            this.pendingFile = null;

            if (next != null) {
                writeBuffer(next, queuedCallback);
            } else if (file != null) {
                performTransfer(file, queuedCallback, pendingFileStart, pendingFileEnd);
            }
            inCall = true;
            try {
                queuedCallback.onComplete(exchange, null);
            } finally {
                inCall = false;
            }
        }
    }

    private void queue(final ByteBuf ByteBufs, final IoCallback ioCallback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (next != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        next = ByteBufs;
        queuedCallback = ioCallback;
    }

    private void queue(final RandomAccessFile source, final IoCallback ioCallback, long start, long end) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (pendingFile != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        pendingFile = source;
        queuedCallback = ioCallback;
        this.pendingFileStart = start;
        this.pendingFileEnd = end;
    }

}
