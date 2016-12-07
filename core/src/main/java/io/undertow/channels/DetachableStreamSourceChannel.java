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
import org.xnio.conduits.ConduitStreamSourceChannel;
import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;

/**
 * A stream source channel that can be marked as detached. Once this is marked as detached then
 * calls will no longer be forwarded to the delegate.
 *
 * @author Stuart Douglas
 */
public abstract class DetachableStreamSourceChannel implements StreamSourceChannel {

    protected final StreamSourceChannel delegate;

    protected ChannelListener.SimpleSetter<DetachableStreamSourceChannel> readSetter;
    protected ChannelListener.SimpleSetter<DetachableStreamSourceChannel> closeSetter;
    private boolean minusOneReturned = false;

    public DetachableStreamSourceChannel(final StreamSourceChannel delegate) {
        this.delegate = delegate;
        if(isFinished()) {
            minusOneReturned = true;
        }
    }

    protected abstract boolean isFinished();

    @Override
    public void resumeReads() {
        if (isFinished() && minusOneReturned) {
            return;
        }
        if (isFinished()) {
            runReadListener();
        } else {
            delegate.resumeReads();
        }
    }

    private void runReadListener() {
        if (readSetter != null && readSetter.get() != null) {
            ChannelListeners.invokeChannelListener(getIoThread(), this, readSetter.get());
        }
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        if (isFinished() && minusOneReturned) {
            return -1;
        }
        long ret = delegate.transferTo(position, count, target);
        if (ret == -1) {
            minusOneReturned = true;
        } else if (isFinished()) {
            runReadListener();
        }
        return ret;
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
        if (isFinished() && minusOneReturned) {
            return -1;
        }
        long ret = delegate.transferTo(count, throughBuffer, target);
        if (ret == -1) {
            minusOneReturned = true;
        } else if (isFinished()) {
            runReadListener();
        }
        return ret;
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean isReadResumed() {
        if (isFinished() && minusOneReturned) {
            return false;
        }
        return delegate.isReadResumed();
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        if (isFinished() && minusOneReturned) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        if (!isFinished()) {
            return delegate.setOption(option, value);
        } else {
            return null;
        }
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public void shutdownReads() throws IOException {
        if (isFinished() && minusOneReturned) {
            return;
        }
        delegate.shutdownReads();
        if (isFinished() && delegate.isReadResumed()) {
            runReadListener();
        }
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getReadSetter() {
        if (readSetter == null) {
            readSetter = new ChannelListener.SimpleSetter<>();
            if (!isFinished()) {
                if (delegate instanceof ConduitStreamSourceChannel) {
                    ((ConduitStreamSourceChannel) delegate).setReadListener(new SetterDelegatingListener((ChannelListener.SimpleSetter) readSetter, this));
                } else {
                    delegate.getReadSetter().set(new SetterDelegatingListener((ChannelListener.SimpleSetter) readSetter, this));
                }
            }
        }
        return readSetter;
    }

    public boolean isOpen() {
        if (isFinished() && minusOneReturned) {
            return false;
        }
        return delegate.isOpen();
    }

    public long read(final ByteBuffer[] dsts) throws IOException {
        if (isFinished() && minusOneReturned) {
            return -1;
        }
        long ret = delegate.read(dsts);
        if (ret == -1) {
            minusOneReturned = true;
        } else if (isFinished()) {
            runReadListener();
        }
        return ret;
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        if (isFinished() && minusOneReturned) {
            return -1;
        }
        long ret = delegate.read(dsts, offset, length);
        if (ret == -1) {
            minusOneReturned = true;
        } else if (isFinished()) {
            runReadListener();
        }
        return ret;
    }

    public void wakeupReads() {
        if (isFinished() && minusOneReturned) {
            return;
        }
        if(isFinished()) {
            runReadListener();
        } else {
            delegate.wakeupReads();
        }
    }

    public XnioExecutor getReadThread() {
        return delegate.getReadThread();
    }

    public void awaitReadable(final long time, final TimeUnit timeUnit) throws IOException {
        if(isFinished()) {
            return;
        }
        delegate.awaitReadable(time, timeUnit);
    }

    public ChannelListener.Setter<? extends StreamSourceChannel> getCloseSetter() {
        if (closeSetter == null) {
            closeSetter = new ChannelListener.SimpleSetter<>();
            if (!isFinished()) {
                if (delegate instanceof ConduitStreamSourceChannel) {
                    ((ConduitStreamSourceChannel) delegate).setCloseListener(ChannelListeners.delegatingChannelListener(this, closeSetter));
                } else {
                    delegate.getCloseSetter().set(ChannelListeners.delegatingChannelListener(this, closeSetter));
                }
            }
        }
        return closeSetter;
    }

    public void close() throws IOException {
        if (isFinished() && minusOneReturned) {
            return;
        }
        delegate.close();
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        if (isFinished() && minusOneReturned) {
            throw UndertowMessages.MESSAGES.streamIsClosed();
        }
        return delegate.getOption(option);
    }

    public int read(final ByteBuffer dst) throws IOException {
        if (isFinished() && minusOneReturned) {
            return -1;
        }
        int ret = delegate.read(dst);
        if (ret == -1) {
            minusOneReturned = true;
        } else if (isFinished()) {
            runReadListener();
        }
        return ret;
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
            if (channelListener != null) {
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
