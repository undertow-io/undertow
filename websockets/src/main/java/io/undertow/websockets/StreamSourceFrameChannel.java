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
import java.util.concurrent.TimeUnit;


import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;

/**
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public abstract class StreamSourceFrameChannel implements StreamSourceChannel {

    private final WebSocketFrameType type;
    protected final StreamSourceChannel channel;
    protected final WebSocketChannel wsChannel;
    private final SimpleSetter<StreamSourceFrameChannel> closeSetter = new SimpleSetter<StreamSourceFrameChannel>();
    private volatile boolean closed;
    
    public StreamSourceFrameChannel(StreamSourceChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type) {
        this.channel = channel;
        this.wsChannel = wsChannel;
        this.type = type;
    }

    /**
     * Return the {@link WebSocketFrameType} or <code>null</code> if its not known at the calling time.
     * 
     * 
     */
    public WebSocketFrameType getType() {
        return type;
    }

    @Override
    public Setter<? extends StreamSourceChannel> getReadSetter() {
        return channel.getReadSetter();
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public void close() throws IOException {
        if(wsChannel.recycle(this)) {
            closed = true;
            ChannelListeners.invokeChannelListener(this, closeSetter.get());
        }
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
