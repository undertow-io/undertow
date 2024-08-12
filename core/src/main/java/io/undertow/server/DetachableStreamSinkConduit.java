/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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

package io.undertow.server;

import io.undertow.UndertowMessages;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.WriteReadyHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

/**
 * When this conduit is considered detached it will no longer forward calls to the delegate.
 */
abstract class DetachableStreamSinkConduit implements StreamSinkConduit {

    private final StreamSinkConduit delegate;

    DetachableStreamSinkConduit(StreamSinkConduit delegate) {
        this.delegate = delegate;
    }

    protected abstract boolean isFinished();

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.transferFrom(src, position, count);
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.transferFrom(source, count, throughBuffer);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.write(src);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.write(srcs, offs, len);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.writeFinal(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.writeFinal(srcs, offset, length);
    }

    @Override
    public void terminateWrites() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.terminateWrites();
    }

    @Override
    public boolean isWriteShutdown() {
        if (isFinished()) {
            return true;
        }
        return delegate.isWriteShutdown();
    }

    @Override
    public void resumeWrites() {
        if (isFinished()) {
            return;
        }
        delegate.resumeWrites();
    }

    @Override
    public void suspendWrites() {
        if (isFinished()) {
            return;
        }
        delegate.suspendWrites();
    }

    @Override
    public void wakeupWrites() {
        if (isFinished()) {
            return;
        }
        delegate.wakeupWrites();
    }

    @Override
    public boolean isWriteResumed() {
        if (isFinished()) {
            return false;
        }
        return delegate.isWriteResumed();
    }

    @Override
    public void awaitWritable() throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        delegate.awaitWritable();
    }

    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        delegate.awaitWritable(time, timeUnit);
    }

    @Override
    public XnioIoThread getWriteThread() {
        return delegate.getWriteThread();
    }

    @Override
    public void setWriteReadyHandler(WriteReadyHandler handler) {
        if (isFinished()) {
            return;
        }
        delegate.setWriteReadyHandler(handler);
    }

    @Override
    public void truncateWrites() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.truncateWrites();
    }

    @Override
    public boolean flush() throws IOException {
        if (isFinished()) {
            return true;
        }
        return delegate.flush();
    }

    @Override
    public XnioWorker getWorker() {
        return delegate.getWorker();
    }
}
