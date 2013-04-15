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

package io.undertow.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowMessages;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.xnio.Bits.anyAreSet;

/**
 * @author Stuart Douglas
 */
public class StreamSinkChannelFacade implements StreamSinkChannel {

    private static final int WRITES_SHUTDOWN = 1;
    private static final int CLOSED = 1 << 1;


    protected final StreamSinkChannel delegate;
    protected final ChannelListener.SimpleSetter<StreamSinkChannelFacade> writeSetter = new ChannelListener.SimpleSetter<StreamSinkChannelFacade>();
    protected final ChannelListener.SimpleSetter<StreamSinkChannelFacade> closeSetter = new ChannelListener.SimpleSetter<StreamSinkChannelFacade>();
    private int state = 0;

    public StreamSinkChannelFacade(final StreamSinkChannel delegate) {
        this.delegate = delegate;
        delegate.getWriteSetter().set(ChannelListeners.delegatingChannelListener(this, writeSetter));
        delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
    }


    @Override
    public void suspendWrites() {
        if (anyAreSet(state, CLOSED)) {
            return;
        }
        delegate.suspendWrites();
    }

    @Override
    public void resumeWrites() {
        if (anyAreSet(state, CLOSED)) {
            return;
        }
        delegate.resumeWrites();
    }

    @Override
    public boolean isWriteResumed() {
        if (anyAreSet(state, CLOSED)) {
            return false;
        }
        return delegate.isWriteResumed();
    }

    @Override
    public void wakeupWrites() {

        if (anyAreSet(state, CLOSED)) {
            return;
        }
        delegate.wakeupWrites();
    }

    @Override
    public void shutdownWrites() throws IOException {
        if (anyAreSet(state, CLOSED)) {
            return;
        }
        state |= WRITES_SHUTDOWN;
        delegate.shutdownWrites();
    }

    @Override
    public void awaitWritable() throws IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        delegate.awaitWritable();
    }

    @Override
    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {

        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        delegate.awaitWritable(time, timeUnit);
    }

    @Override
    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    @Override
    public boolean isOpen() {
        return !anyAreSet(state, CLOSED);
    }

    @Override
    public void close() throws IOException {
        if (anyAreSet(state, CLOSED)) return;
        state |= CLOSED;
        delegate.close();
    }

    @Override
    public boolean flush() throws IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        boolean res = delegate.flush();
        if (res && anyAreSet(state, WRITES_SHUTDOWN)) {
            state |= CLOSED;
        }
        return res;
    }

    @Override
    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.transferFrom(src, position, count);
    }

    @Override
    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.transferFrom(source, count, throughBuffer);
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }

    @Override
    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }

    @Override
    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.write(srcs, offset, length);
    }

    @Override
    public long write(final ByteBuffer[] srcs) throws IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.write(srcs);
    }

    @Override
    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.getOption(option);
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.setOption(option, value);
    }

    @Override
    public int write(final ByteBuffer src) throws IOException {
        if (anyAreSet(state, CLOSED)) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.write(src);
    }
}
