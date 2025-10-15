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

package io.undertow.servlet.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import jakarta.servlet.DispatcherType;

import io.undertow.UndertowMessages;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import org.xnio.IoUtils;

/**
 * A sender that uses a print writer.
 *
 * In general this should never be used. It exists for the edge case where a filter has called
 * getWriter() and then the default servlet is being used to serve a text file.
 *
 * @author Stuart Douglas
 */
public class BlockingWriterSenderImpl implements Sender {

    /**
     * TODO: we should be used pooled buffers
     */
    public static final int BUFFER_SIZE = 128;

    private final CharsetDecoder charsetDecoder;
    private final HttpServerExchange exchange;
    private final PrintWriter writer;

    private FileChannel pendingFile;
    private volatile Thread inCall;
    private volatile Thread sendThread;
    private String next;
    private IoCallback queuedCallback;

    public BlockingWriterSenderImpl(final HttpServerExchange exchange, final PrintWriter writer, final String charset) {
        this.exchange = exchange;
        this.writer = writer;
        this.charsetDecoder = Charset.forName(charset).newDecoder();
    }

    @Override
    public void send(final ByteBuffer buffer, final IoCallback callback) {
        sendThread = Thread.currentThread();
        if (inCall == Thread.currentThread()) {
            queue(new ByteBuffer[]{buffer}, callback);
            return;
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
        }
        for (ByteBuffer b : buffer) {
            if (!writeBuffer(b, callback)) {
                return;
            }
        }
        invokeOnComplete(callback);
    }

    @Override
    public void send(final String data, final IoCallback callback) {
        sendThread = Thread.currentThread();
        if (inCall == Thread.currentThread()) {
            queue(data, callback);
            return;
        }
        writer.write(data);

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
    public void send(final String data, final Charset charset, final IoCallback callback) {
        sendThread = Thread.currentThread();
        if (inCall == Thread.currentThread()) {
            queue(new ByteBuffer[]{ByteBuffer.wrap(data.getBytes(charset))}, callback);
            return;
        }
        writer.write(data);
        invokeOnComplete(callback);
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
    }

    private void performTransfer(FileChannel source, IoCallback callback) {

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        try {
            long pos = source.position();
            long size = source.size();
            while (size - pos > 0) {
                int ret = source.read(buffer);
                if (ret <= 0) {
                    break;
                }
                pos += ret;
                buffer.flip();
                if (!writeBuffer(buffer, callback)) {
                    return;
                }
                buffer.clear();
            }

            if (pos != size) {
                throw new EOFException("Unexpected EOF reading file");
            }

        } catch (IOException e) {
            callback.onException(exchange, this, e);
        }
        invokeOnComplete(callback);
    }


    @Override
    public void close(final IoCallback callback) {
        writer.close();
        if (writer.checkError()) {
            callback.onException(exchange, this, new IOException());
        } else {
            invokeOnComplete(callback);
        }
    }

    @Override
    public void close() {
        if(exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getDispatcherType() != DispatcherType.INCLUDE) {
            IoUtils.safeClose(writer);
        }
        writer.checkError();
    }


    private boolean writeBuffer(final ByteBuffer buffer, final IoCallback callback) {
        StringBuilder builder = new StringBuilder();
        try {
            builder.append(charsetDecoder.decode(buffer));
        } catch (CharacterCodingException e) {
            callback.onException(exchange, this, e);
            return false;
        }
        String data = builder.toString();
        writer.write(data);
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
        while (next != null) {
            String next = this.next;
            IoCallback queuedCallback = this.queuedCallback;
            this.next = null;
            this.queuedCallback = null;
            writer.write(next);
            if (writer.checkError()) {
                queuedCallback.onException(exchange, this, new IOException());
            } else {
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
    }

    private void queue(final ByteBuffer[] byteBuffers, final IoCallback ioCallback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (next != null || pendingFile != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        StringBuilder builder = new StringBuilder();
        for (ByteBuffer buffer : byteBuffers) {
            try {
                builder.append(charsetDecoder.decode(buffer));
            } catch (CharacterCodingException e) {
                ioCallback.onException(exchange, this, e);
                return;
            }
        }
        this.next = builder.toString();
        queuedCallback = ioCallback;
    }

    private void queue(final String data, final IoCallback callback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (next != null || pendingFile != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        next = data;
        queuedCallback = callback;
    }
    private void queue(final FileChannel data, final IoCallback callback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitely
        if (next != null || pendingFile != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        pendingFile = data;
        queuedCallback = callback;
    }

}
