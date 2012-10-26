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

package io.undertow.websockets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;


import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class StreamSourceFrameChannel implements StreamSourceChannel {

    private final WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl;
    protected final WebSocketFrameType type;
    protected final StreamSourceChannel channel;
    protected final WebSocketChannel wsChannel;

    private final SimpleSetter<? extends StreamSourceFrameChannel> readSetter = new SimpleSetter<StreamSourceFrameChannel>();
    private final SimpleSetter<StreamSourceFrameChannel> closeSetter = new SimpleSetter<StreamSourceFrameChannel>();
    private volatile boolean closed;
    private final boolean finalFragment;
    private boolean complete;

    public StreamSourceFrameChannel(final WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, boolean finalFragment) {
        this.streamSourceChannelControl = streamSourceChannelControl;
        this.channel = channel;
        this.wsChannel = wsChannel;
        this.type = type;
        this.finalFragment = finalFragment;
    }

    /**
     * Returns <code>true</code> if the frame was complete.
     */
    protected abstract boolean isComplete();

    @Override
    public final long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        if (complete) {
            return -1;
        }
        try {
            return read0(dsts, offset, length);
        } finally {
            if(isComplete()) {
                complete();
            }
        }
    }

    /**
     * @see StreamSourceChannel#read(ByteBuffer[], int, int)
     */
    protected abstract long read0(ByteBuffer[] dsts, int offset, int length) throws IOException;

    @Override
    public final long read(ByteBuffer[] dsts) throws IOException {
        if (complete) {
            return -1;
        }
        try {
            return read0(dsts);
        } finally {
            if(isComplete()) {
                complete();
            }
        }
    }

    /**
     * @see StreamSourceChannel#read(ByteBuffer[])
     */
    protected abstract long read0(ByteBuffer[] dsts) throws IOException;

    @Override
    public final int read(ByteBuffer dst) throws IOException {
        if (complete) {
            return -1;
        }
        try {
            return read0(dst);
        } finally {
            if(isComplete()) {
                complete();
            }
        }
    }

    /**
     * @see StreamSourceChannel#read(ByteBuffer)
     */
    protected abstract int read0(ByteBuffer dst) throws IOException;

    @Override
    public final long transferTo(long position, long count, FileChannel target) throws IOException {
        if (complete) {
            return -1;
        }
        try {
            return transferTo0(position, count, target);
        } finally {
            if(isComplete()) {
                complete();
            }
        }
    }

    /**
     * @see StreamSourceChannel#transferTo(long, long, FileChannel)
     */
    protected abstract long transferTo0(long position, long count, FileChannel target) throws IOException;

    @Override
    public final long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        if (complete) {
            return -1;
        }
        try {
            return transferTo0(count, throughBuffer, target);
        } finally {
            if(isComplete()) {
                complete();
            }
        }
    }

    /**
     * @see StreamSourceChannel#transferTo(long, ByteBuffer, StreamSinkChannel)
     */
    protected abstract long transferTo0(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException;

    private void complete() {
        complete = true;
        streamSourceChannelControl.readFrameDone(this);
    }

    /**
     * Return the {@link WebSocketFrameType} or <code>null</code> if its not known at the calling time.
     */
    public WebSocketFrameType getType() {
        return type;
    }

    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    public boolean isFinalFragment() {
        return finalFragment;
    }

    @Override
    public Setter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        ChannelListeners.invokeChannelListener(this, closeSetter.get());
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
    public boolean isOpen() {
        return !closed && channel.isOpen();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return channel.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return channel.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return channel.setOption(option, value);
    }

    @Override
    public Setter<? extends StreamSourceChannel> getCloseSetter() {
        return closeSetter;
    }
}
