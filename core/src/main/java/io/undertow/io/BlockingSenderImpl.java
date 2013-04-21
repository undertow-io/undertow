/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

package io.undertow.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import org.xnio.IoUtils;

/**
 * A sender that uses an output stream.
 *
 *
 * @author Stuart Douglas
 */
public class BlockingSenderImpl implements Sender {

    private static final Charset utf8 = Charset.forName("UTF-8");

    private final HttpServerExchange exchange;
    private final OutputStream outputStream;
    private boolean inCall;
    private ByteBuffer[] next;
    private IoCallback queuedCallback;

    public BlockingSenderImpl(final HttpServerExchange exchange, final OutputStream outputStream) {
        this.exchange = exchange;
        this.outputStream = outputStream;
    }

    @Override
    public void send(final ByteBuffer buffer, final IoCallback callback) {
        if (inCall) {
            queue(new ByteBuffer[]{buffer}, callback);
            return;
        }
        if (writeBuffer(buffer, callback)) {
            invokeOnComplete(callback);
        }
    }


    @Override
    public void send(final ByteBuffer[] buffer, final IoCallback callback) {
        if (inCall) {
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
        if (inCall) {
            queue(new ByteBuffer[]{ByteBuffer.wrap(data.getBytes(utf8))}, callback);
            return;
        }
        try {
            outputStream.write(data.getBytes(utf8));
            invokeOnComplete(callback);
        } catch (IOException e) {
            callback.onException(exchange, this, e);
        }
    }

    @Override
    public void send(final String data, final Charset charset, final IoCallback callback) {
        if (inCall) {
            queue(new ByteBuffer[]{ByteBuffer.wrap(data.getBytes(charset))}, callback);
            return;
        }
        try {
            outputStream.write(data.getBytes(charset));
            invokeOnComplete(callback);
        } catch (IOException e) {
            callback.onException(exchange, this, e);
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
        if (buffer.hasArray()) {
            try {
                outputStream.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
            } catch (IOException e) {
                callback.onException(exchange, this, e);
                return false;
            }
        } else {
            byte[] b = new byte[128];
            do {
                int rem = buffer.remaining();
                buffer.get(b);
                try {
                    outputStream.write(b, 0, Math.min(rem, 128));
                } catch (IOException e) {
                    callback.onException(exchange, this, e);
                    return false;
                }
            } while (buffer.hasRemaining());
        }
        return true;
    }


    private void invokeOnComplete(final IoCallback callback) {
        inCall = true;
        try {
            callback.onComplete(exchange, this);
        } finally {
            inCall = false;
        }
        while (next != null) {
            ByteBuffer[] next = this.next;
            IoCallback queuedCallback = this.queuedCallback;
            this.next = null;
            this.queuedCallback = null;
            for(ByteBuffer buffer : next) {
                writeBuffer(buffer, queuedCallback);
            }
            inCall = true;
            try {
                queuedCallback.onComplete(exchange, this);
            } finally {
                inCall = false;
            }
        }
    }

    private void queue(final ByteBuffer[] byteBuffers, final IoCallback ioCallback) {
        //if data is sent from withing the callback we queue it, to prevent the stack growing indefinitly
        if (next != null) {
            throw UndertowMessages.MESSAGES.dataAlreadyQueued();
        }
        next = byteBuffers;
        queuedCallback = ioCallback;
    }

}
