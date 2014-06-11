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

package io.undertow.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

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
public abstract class DelegatingStreamSourceChannel<T extends DelegatingStreamSourceChannel> implements StreamSourceChannel {

    protected final ChannelListener.SimpleSetter<T> readSetter = new ChannelListener.SimpleSetter<>();
    protected final ChannelListener.SimpleSetter<T> closeSetter = new ChannelListener.SimpleSetter<>();
    protected final StreamSourceChannel delegate;

    public DelegatingStreamSourceChannel(final StreamSourceChannel delegate) {
        this.delegate = delegate;
        delegate.getReadSetter().set(ChannelListeners.delegatingChannelListener((T) this, readSetter));
        delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener((T) this, closeSetter));
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return delegate.transferTo(position, count, target);
    }

    public void awaitReadable() throws IOException {
        delegate.awaitReadable();
    }

    public void suspendReads() {
        delegate.suspendReads();
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return delegate.transferTo(count, throughBuffer, target);
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean isReadResumed() {
        return delegate.isReadResumed();
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public void shutdownReads() throws IOException {
        delegate.shutdownReads();
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        return delegate.read(dsts);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        return delegate.read(dsts, offset, length);
    }

    public void wakeupReads() {
        delegate.wakeupReads();
    }

    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitReadable(time, timeUnit);
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

    public void close() throws IOException {
        delegate.close();
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    public void resumeReads() {
        delegate.resumeReads();
    }

    public int read(final ByteBuffer dst) throws IOException {
        return delegate.read(dst);
    }

    @Override
    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }
}
