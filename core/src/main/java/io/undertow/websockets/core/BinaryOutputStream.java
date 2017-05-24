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
package io.undertow.websockets.core;

import io.undertow.UndertowMessages;
import org.xnio.channels.Channels;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * {@link OutputStream} implementation which buffers all the data until {@link #close()} is called and then will
 * try to send it in a blocking fashion with the provided {@link StreamSinkFrameChannel}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class BinaryOutputStream extends OutputStream {
    private final StreamSinkFrameChannel sender;
    private boolean closed;

    public BinaryOutputStream(StreamSinkFrameChannel sender) {
        this.sender = sender;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkClosed();
        if(Thread.currentThread() == sender.getIoThread()) {
            throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
        }
        Channels.writeBlocking(sender, ByteBuffer.wrap(b, off, len));
    }

    @Override
    public void write(int b) throws IOException {
        checkClosed();
        if(Thread.currentThread() == sender.getIoThread()) {
            throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
        }
        Channels.writeBlocking(sender, ByteBuffer.wrap(new byte[]{(byte) b}));
    }

    @Override
    public void flush() throws IOException {
        checkClosed();
        if(Thread.currentThread() == sender.getIoThread()) {
            throw UndertowMessages.MESSAGES.awaitCalledFromIoThread();
        }
        sender.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            sender.shutdownWrites();
            Channels.flushBlocking(sender);
        }
    }

    private void checkClosed() throws IOException {
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
    }
}
