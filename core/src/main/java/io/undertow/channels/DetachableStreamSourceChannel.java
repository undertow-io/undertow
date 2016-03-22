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

import io.undertow.UndertowLogger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import io.undertow.UndertowMessages;

/**
 * A stream source channel that can be marked as detached. Once this is marked as detached then
 * calls will no longer be forwarded to the delegate.
 *
 * @author Stuart Douglas
 */
public abstract class DetachableStreamSourceChannel implements StreamSourceChannel{

    protected final StreamSourceChannel delegate;

    protected ChannelListener.SimpleSetter<DetachableStreamSourceChannel> readSetter;
    protected ChannelListener.SimpleSetter<DetachableStreamSourceChannel> closeSetter;

    public DetachableStreamSourceChannel(final StreamSourceChannel delegate) {
        this.delegate = delegate;
    }

    protected abstract boolean isFinished();

    @Override
    public void resumeReads() {
        if (isFinished()) {
            return;
        }
        delegate.resumeReads();
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.transferTo(position, count, target);
    }

    public void awaitReadable() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.awaitReadable();
    }

    public void suspendReads() {
        if (isFinished()) {
            return;
        }
        delegate.suspendReads();
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.transferTo(count, throughBuffer, target);
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean isReadResumed() {
        if (isFinished()) {
            return false;
        }
        return delegate.isReadResumed();
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {

        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        return delegate.setOption(option, value);
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public void shutdownReads() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.shutdownReads();
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        if (readSetter == null) {
            readSetter = new ChannelListener.SimpleSetter<>();
            if (!isFinished()) {
                if(delegate instanceof ConduitStreamSourceChannel) {
                    ((ConduitStreamSourceChannel)delegate).setReadListener(new SetterDelegatingListener((ChannelListener.SimpleSetter)readSetter, this));
                } else {
                    delegate.getReadSetter().set(new SetterDelegatingListener((ChannelListener.SimpleSetter)readSetter, this));
                }
            }
        }
        return readSetter;
    }

    public boolean isOpen() {
        if (isFinished()) {
            return false;
        }
        return delegate.isOpen();
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.read(dsts);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.read(dsts, offset, length);
    }

    public void wakeupReads() {
        if (isFinished()) {
            return;
        }
        delegate.wakeupReads();
    }

    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        delegate.awaitReadable(time, timeUnit);
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        if (closeSetter == null) {
            closeSetter = new ChannelListener.SimpleSetter<>();
            if (!isFinished()) {
                if(delegate instanceof ConduitStreamSourceChannel) {
                    ((ConduitStreamSourceChannel)delegate).setCloseListener(ChannelListeners.delegatingChannelListener(this, closeSetter));
                } else {
                    delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
                }
            }
        }
        return closeSetter;
    }

    public void close() throws IOException {
        if (isFinished()) {
            return;
        }
        delegate.close();
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (isFinished()) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.getOption(option);
    }

    public int read(final ByteBuffer dst) throws IOException {
        if (isFinished()) {
            return -1;
        }
        return delegate.read(dst);
    }

    @Override
    public XnioIoThread getIoThread() {
        return delegate.getIoThread();
    }


    private static class SetterDelegatingListener implements ChannelListener<StreamSourceChannel> {

        private final SimpleSetter<StreamSourceChannel> setter;
        private final StreamSourceChannel channel;

        SetterDelegatingListener(final SimpleSetter<StreamSourceChannel> setter, final StreamSourceChannel channel) {
            this.setter = setter;
            this.channel = channel;
        }

        public void handleEvent(final StreamSourceChannel channel) {
            ChannelListener<? super StreamSourceChannel> channelListener = setter.get();
            if(channelListener != null) {
                ChannelListeners.invokeChannelListener(this.channel, channelListener);
            } else {
                UndertowLogger.REQUEST_LOGGER.debugf("suspending reads on %s to prevent listener runaway", channel);
                channel.suspendReads();
            }
        }

        public String toString() {
            return "Setter delegating channel listener -> " + setter;
        }
    }
}
