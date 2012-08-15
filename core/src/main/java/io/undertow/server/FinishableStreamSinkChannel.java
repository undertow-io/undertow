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

package io.undertow.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class FinishableStreamSinkChannel implements StreamSinkChannel {
    private final StreamSinkChannel delegate;
    private volatile ChannelListener<? super FinishableStreamSinkChannel> writeListener;
    private volatile ChannelListener<? super FinishableStreamSinkChannel> closeListener;
    private final ChannelListener<? super FinishableStreamSinkChannel> finishListener;

    //0 = open
    //1 = writes shutdown
    //2 = finish listener invoked
    @SuppressWarnings("unused")
    private volatile int shutdownState = 0;

    private static final AtomicIntegerFieldUpdater<FinishableStreamSinkChannel> shutdownStateUpdater = AtomicIntegerFieldUpdater.newUpdater(FinishableStreamSinkChannel.class, "shutdownState");

    FinishableStreamSinkChannel(final StreamSinkChannel delegate, final ChannelListener<? super FinishableStreamSinkChannel> finishListener) {
        this.delegate = delegate;
        this.finishListener = finishListener;
        delegate.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                ChannelListeners.invokeChannelListener(FinishableStreamSinkChannel.this, writeListener);
            }
        });
        delegate.getCloseSetter().set(new ChannelListener<StreamSinkChannel>() {
            public void handleEvent(final StreamSinkChannel channel) {
                ChannelListeners.invokeChannelListener(FinishableStreamSinkChannel.this, closeListener);
                int state = shutdownStateUpdater.get(FinishableStreamSinkChannel.this);
                if(state != 2 && shutdownStateUpdater.compareAndSet(FinishableStreamSinkChannel.this,state, 2 )) {
                    ChannelListeners.invokeChannelListener(FinishableStreamSinkChannel.this, finishListener);
                }
            }
        });
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getWriteSetter() {
        return new ChannelListener.Setter<StreamSinkChannel>() {
            public void set(final ChannelListener<? super StreamSinkChannel> listener) {
                writeListener = listener;
            }
        };
    }

    public void setWriteListener(final ChannelListener<? super FinishableStreamSinkChannel> writeListener) {
        this.writeListener = writeListener;
    }

    public void setCloseListener(final ChannelListener<? super FinishableStreamSinkChannel> closeListener) {
        this.closeListener = closeListener;
    }

    public ChannelListener.Setter<? extends StreamSinkChannel> getCloseSetter() {
        return new ChannelListener.Setter<StreamSinkChannel>() {
            public void set(final ChannelListener<? super StreamSinkChannel> listener) {
                closeListener = listener;
            }
        };
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        return delegate.transferFrom(src, position, count);
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        return delegate.transferFrom(source, count, throughBuffer);
    }

    public int write(final ByteBuffer src) throws IOException {
        return delegate.write(src);
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void close() throws IOException {
        delegate.close();
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        return delegate.write(srcs, offset, length);
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return delegate.write(srcs);
    }

    public void suspendWrites() {
        delegate.suspendWrites();
    }

    public void resumeWrites() {
        delegate.resumeWrites();
    }

    public boolean isWriteResumed() {
        return delegate.isWriteResumed();
    }

    public void wakeupWrites() {
        delegate.wakeupWrites();
    }

    public void shutdownWrites() throws IOException {
        delegate.shutdownWrites();
        shutdownStateUpdater.compareAndSet(this, 0 ,1);
    }

    public void awaitWritable() throws IOException {
        delegate.awaitWritable();
    }

    public void awaitWritable(final long time, final TimeUnit timeUnit) throws IOException {
        delegate.awaitWritable(time, timeUnit);
    }

    public XnioExecutor getWriteThread() {
        return delegate.getWriteThread();
    }

    public boolean flush() throws IOException {
        final boolean val =  delegate.flush();
        if(val && shutdownStateUpdater.compareAndSet(this, 1, 2)) {
            ChannelListeners.invokeChannelListener(FinishableStreamSinkChannel.this, finishListener);
        }
        return val;
    }

    public XnioWorker getWorker() {
        return delegate.getWorker();
    }

    public boolean supportsOption(final Option<?> option) {
        return delegate.supportsOption(option);
    }

    public <T> T getOption(final Option<T> option) throws IOException {
        return delegate.getOption(option);
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException, IOException {
        return delegate.setOption(option, value);
    }
}
