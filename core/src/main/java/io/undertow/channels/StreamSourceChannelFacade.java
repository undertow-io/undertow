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

/**
 * @author Stuart Douglas
 */
public class StreamSourceChannelFacade implements StreamSourceChannel {

    protected final ChannelListener.SimpleSetter<StreamSourceChannelFacade> readSetter = new ChannelListener.SimpleSetter<StreamSourceChannelFacade>();
    protected final ChannelListener.SimpleSetter<StreamSourceChannelFacade> closeSetter = new ChannelListener.SimpleSetter<StreamSourceChannelFacade>();
    protected final StreamSourceChannel delegate;

    private boolean closed = false;

    public StreamSourceChannelFacade(final StreamSourceChannel delegate) {
        this.delegate = delegate;
        delegate.getReadSetter().set(ChannelListeners.delegatingChannelListener(this, readSetter));
        delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }

        long res = delegate.transferTo(position, count, target);
        if (res == -1) {
            closed = true;
        }
        return res;
    }

    public void awaitReadable() throws IOException {
        delegate.awaitReadable();
    }

    public void suspendReads() {
        delegate.suspendReads();
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {

        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        long res = delegate.transferTo(count, throughBuffer, target);
        if (res == -1) {
            closed = true;
        }
        return res;
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean isReadResumed() {
        if (closed) {
            return false;
        }
        return delegate.isReadResumed();
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {

        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.setOption(option, value);
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public void shutdownReads() throws IOException {
        if (closed) {
            return;
        }
        delegate.shutdownReads();
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    public boolean isOpen() {
        if (closed) {
            return false;
        }
        return delegate.isOpen();
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        long res = delegate.read(dsts);
        if (res == -1) {
            closed = true;
        }
        return res;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        long res = delegate.read(dsts, offset, length);
        if (res == -1) {
            closed = true;
        }
        return res;
    }

    public void wakeupReads() {
        if (closed) {
            return;
        }
        delegate.wakeupReads();
    }

    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        delegate.awaitReadable(time, timeUnit);
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        delegate.close();
    }

    public <T> T getOption(final Option<T> option) throws IOException {

        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.getOption(option);
    }

    public void resumeReads() {
        if (closed) {
            return;
        }
        delegate.resumeReads();
    }

    public int read(final ByteBuffer dst) throws IOException {
        if (closed) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        int res = delegate.read(dst);
        if (res == -1) {
            closed = true;
        }
        return res;
    }

    @Override
    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }
}
