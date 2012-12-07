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


import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
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
    private final boolean finalFragment;
    private final int rsv;
    private final long payloadSize;

    private volatile boolean readsResumed = false;
    private volatile boolean complete;
    private volatile boolean closed;

    public StreamSourceFrameChannel(final WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize) {
        this(streamSourceChannelControl, channel, wsChannel, type, payloadSize, 0, true);
    }

    public StreamSourceFrameChannel(final WebSocketChannel.StreamSourceChannelControl streamSourceChannelControl, StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize, int rsv, boolean finalFragment) {
        this.streamSourceChannelControl = streamSourceChannelControl;
        this.channel = channel;
        this.wsChannel = wsChannel;
        this.type = type;
        this.finalFragment = finalFragment;
        this.rsv = rsv;
        this.payloadSize = payloadSize;
    }

    /**
     * Return the payload size of <code>-1</code> if unknown on creation
     *
     * @return payloadSize
     */
    public long getPayloadSize() {
        return payloadSize;
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
            throughBuffer.clear();
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

    protected void complete() throws IOException {
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

    /**
     * Return the rsv which is used for extensions.
     *
     */
    public int getRsv() {
        return rsv;
    }

    @Override
    public SimpleSetter<? extends StreamSourceChannel> getReadSetter() {
        return readSetter;
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public void close() throws IOException {
        if (!isComplete() && wsChannel.isOpen()) {
            // the channel is broken
            wsChannel.markBroken();
            throw WebSocketMessages.MESSAGES.closedBeforeAllBytesWereRead();
        }
        closed = true;
        queueReadListener();
    }

    protected void queueReadListener() {
        getReadThread().execute(new Runnable() {
            @Override
            public void run() {
                WebSocketLogger.REQUEST_LOGGER.debugf("Invoking directly queued read listener");
                ChannelListeners.invokeChannelListener(StreamSourceFrameChannel.this, closeSetter.get());
            }
        });
    }

    /**
     * Discard the frame, which means all data that would be part of the frame will be discarded.
     *
     * Once all is discarded it will call {@link #close()}
     */
    public void discard() throws IOException {

        if (!complete) {
            ChannelListener<StreamSourceChannel> drainListener = ChannelListeners.drainListener(Long.MAX_VALUE,
                    new ChannelListener<StreamSourceChannel>() {
                        @Override
                        public void handleEvent(StreamSourceChannel channel) {
                            getReadSetter().set(null);
                            IoUtils.safeClose(channel);
                        }
                    }, new ChannelExceptionHandler<StreamSourceChannel>() {
                        @Override
                        public void handleException(StreamSourceChannel channel, IOException exception) {
                            getReadSetter().set(null);
                            wsChannel.markBroken();
                            IoUtils.safeClose(channel, wsChannel);
                        }
                    });
             getReadSetter().set(drainListener);
            resumeReads();
        } else {
            close();
        }
    }
    @Override
    public void suspendReads() {
        readsResumed = false;
        if(!complete) {
            channel.suspendReads();
        }
    }

    @Override
    public void resumeReads() {
        readsResumed = true;
        if(complete) {
            queueReadListener();
        } else {
            channel.resumeReads();
        }
    }

    @Override
    public boolean isReadResumed() {
        return readsResumed;
    }

    @Override
    public void wakeupReads() {
        readsResumed = true;
        queueReadListener();
        if (!complete) {
            channel.resumeReads();
        }
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
