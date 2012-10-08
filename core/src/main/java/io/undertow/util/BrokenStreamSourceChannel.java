/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author Stuart Douglas
 */
public class BrokenStreamSourceChannel implements StreamSourceChannel {

    private final IOException exception;
    private final StreamSourceChannel delegate;

    private final ChannelListener.SimpleSetter<BrokenStreamSourceChannel> readSetter = new ChannelListener.SimpleSetter<BrokenStreamSourceChannel>();
    private final ChannelListener.SimpleSetter<BrokenStreamSourceChannel> closeSetter = new ChannelListener.SimpleSetter<BrokenStreamSourceChannel>();

    public BrokenStreamSourceChannel(final IOException exception, final StreamSourceChannel delegate) {
        this.exception = exception;
        this.delegate = delegate;
        delegate.getReadSetter().set(ChannelListeners.delegatingChannelListener(this, readSetter));
        delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
    }

    @Override
    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        throw exception;
    }

    @Override
    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        throw exception;
    }

    @Override
    public void suspendReads() {
        delegate.suspendReads();
    }

    @Override
    public void resumeReads() {
        delegate.resumeReads();
    }

    @Override
    public boolean isReadResumed() {
        return delegate.isReadResumed();
    }

    @Override
    public void wakeupReads() {
        delegate.wakeupReads();
    }

    @Override
    public void shutdownReads() throws IOException {
        delegate.shutdownReads();
    }

    @Override
    public void awaitReadable() throws IOException {
        delegate.awaitReadable();
    }

    @Override
    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitReadable(time, timeUnit);
    }

    @Override
    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    @Override
    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    @Override
    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    @Override
    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    @Override
    public <T> T getOption(final Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }

    @Override
    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        throw exception;
    }

    @Override
    public long read(final ByteBuffer[] dsts) throws IOException {
        throw exception;
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        throw exception;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
