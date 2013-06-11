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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * {@link StreamChannel} wrapper that add support to close a {@link StreamChannel} once for a specified time no
 * reads and no writes were perfomed.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class IdleTimeoutStreamChannel<C extends StreamChannel> extends DelegatingStreamSinkChannel<IdleTimeoutStreamChannel<C>> implements StreamChannel {
    private final C channel;
    private final ChannelListener.SimpleSetter<C> readSetter = new ChannelListener.SimpleSetter<C>();
    private final ChannelListener.SimpleSetter<C> closeSetter = new ChannelListener.SimpleSetter<C>();
    private final ChannelListener.SimpleSetter<C> writeSetter = new ChannelListener.SimpleSetter<C>();

    // TODO: Remove volatile once XNIO changes are complete
    private volatile XnioExecutor.Key handle;
    private static final AtomicReferenceFieldUpdater<IdleTimeoutStreamChannel, XnioExecutor.Key> KEY_UPDATER = AtomicReferenceFieldUpdater.newUpdater(IdleTimeoutStreamChannel.class, XnioExecutor.Key.class, "handle");

    private volatile long idleTimeout;

    private final Runnable timeoutCommand = new Runnable() {
        @Override
        public void run() {
            UndertowLogger.REQUEST_LOGGER.tracef("Timing out channel %s due to inactivity");
            try {
                if (channel.isWriteResumed()) {
                    ChannelListeners.invokeChannelListener((C) IdleTimeoutStreamChannel.this, writeSetter.get());
                }
                if (channel.isReadResumed()) {
                    ChannelListeners.invokeChannelListener((C) IdleTimeoutStreamChannel.this, readSetter.get());
                }
            } finally {
                IoUtils.safeClose(channel);
            }
        }
    };

    public IdleTimeoutStreamChannel(C channel) {
        super(channel);
        channel.getReadSetter().set(ChannelListeners.delegatingChannelListener((C) this, readSetter));
        channel.getCloseSetter().set(ChannelListeners.delegatingChannelListener((C) this, closeSetter));
        this.channel = channel;
    }

    private void handleIdleTimeout(final long ret) {
        long idleTimeout = this.idleTimeout;
        XnioExecutor.Key key = handle;
        if (idleTimeout > 0) {
            if (ret == 0 && key == null) {
                XnioExecutor.Key k = channel.getWriteThread().executeAfter(timeoutCommand, idleTimeout, TimeUnit.MILLISECONDS);
                if (!KEY_UPDATER.compareAndSet(this, null, k)) {
                    k.remove();
                } else {
                    handle = k;
                }
            } else if (ret > 0 && key != null) {
                key.remove();
            }
        }
    }

    @Override
    public ChannelListener.Setter<? extends StreamChannel> getReadSetter() {
        return readSetter;
    }

    @Override
    public ChannelListener.Setter<? extends StreamChannel> getWriteSetter() {
        return writeSetter;
    }

    @Override
    public ChannelListener.Setter<? extends StreamChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int w = channel.write(src);
        handleIdleTimeout(w);
        return w;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long w = channel.write(srcs, offset, length);
        handleIdleTimeout(w);
        return w;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        long w = channel.transferTo(position, count, target);
        handleIdleTimeout(w);
        return w;
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        long w = channel.transferTo(count, throughBuffer, target);
        handleIdleTimeout(w);
        return w;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long r = channel.read(dsts, offset, length);
        handleIdleTimeout(r);
        return r;
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        long r = channel.read(dsts);
        handleIdleTimeout(r);
        return r;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int r = channel.read(dst);
        handleIdleTimeout(r);
        return r;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        long r = channel.transferFrom(src, position, count);
        handleIdleTimeout(r);
        return r;
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        long r = channel.transferFrom(source, count, throughBuffer);
        handleIdleTimeout(r);
        return r;
    }

    @Override
    public void suspendReads() {
        channel.suspendReads();
    }

    @Override
    public void resumeReads() {
        channel.resumeReads();
    }

    @Override
    public boolean isReadResumed() {
        return channel.isReadResumed();
    }

    @Override
    public void wakeupReads() {
        channel.wakeupReads();
    }

    @Override
    public void shutdownReads() throws IOException {
        channel.shutdownReads();
    }

    @Override
    public void awaitReadable() throws IOException {
        channel.awaitReadable();
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        channel.awaitReadable(time, timeUnit);
    }

    @Override
    public XnioExecutor getReadThread() {
        return channel.getReadThread();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        if (option == UndertowOptions.IDLE_TIMEOUT) {
            return true;
        }
        return super.supportsOption(option);
    }

    @Override
    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        T ret = super.setOption(option, value);
        if (option == UndertowOptions.IDLE_TIMEOUT) {
            idleTimeout = (Long) value;
            XnioExecutor.Key key = handle;
            if (key != null) {
                key.remove();
            }

            if (idleTimeout > 0) {
                XnioExecutor.Key k = getWriteThread().executeAfter(timeoutCommand, idleTimeout, TimeUnit.MILLISECONDS);
                if (!KEY_UPDATER.compareAndSet(this, null, k)) {
                    k.remove();
                } else {
                    handle = k;
                }
            }
        }
        return ret;
    }
}
