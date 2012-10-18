
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
import java.util.concurrent.atomic.AtomicBoolean;

import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListener.SimpleSetter;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * 
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public abstract class StreamSinkFrameChannel implements StreamSinkChannel {

    private final WebSocketFrameType type;
    protected final StreamSinkChannel channel;
    private final WebSocketChannel wsChannel;
    SimpleSetter<StreamSinkFrameChannel> closeSetter = new SimpleSetter<StreamSinkFrameChannel>();
    SimpleSetter<StreamSinkFrameChannel> writeSetter = new SimpleSetter<StreamSinkFrameChannel>();
    
    private volatile boolean closed;
    private AtomicBoolean writesDone = new AtomicBoolean();
    protected final long payloadSize;
    final Object writeWaitLock = new Object();

    public StreamSinkFrameChannel(StreamSinkChannel channel, WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize) {
        this.channel = channel;
        this.wsChannel = wsChannel;
        this.type = type;
        this.payloadSize = payloadSize;
    }

    protected WebSocketChannel getWebSocketChannel() {
        return wsChannel;
    }

    @Override
    public Setter<? extends StreamSinkChannel> getWriteSetter() {
        return writeSetter;
    }


    /**
     * Return the {@link WebSocketFrameType} for which the {@link StreamSinkFrameChannel} was obtained.
     * 
     * 
     */
    public WebSocketFrameType getType() {
        return type;
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public final void close() throws IOException {
        if (!closed) {
            closed = true;
            flush();
            if (close0()) {
                remove();
            }

        }
    }


    protected void remove() throws IOException {
        wsChannel.remove(this);
    }

    protected abstract boolean close0() throws IOException ;
    
    @Override
    public final long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        checkClosed();
        return write0(srcs, offset, length);
    }
    
    protected abstract long write0(ByteBuffer[] srcs, int offset, int length) throws IOException;

    @Override
    public final long write(ByteBuffer[] srcs) throws IOException {
        checkClosed();
        return write0(srcs);
    }

    protected abstract long write0(ByteBuffer[] srcs) throws IOException;



    @Override
    public final int write(ByteBuffer src) throws IOException {
        checkClosed();
        return write0(src);
    }
    
    protected abstract int write0(ByteBuffer src) throws IOException;



    @Override
    public final long transferFrom(FileChannel src, long position, long count) throws IOException {
        checkClosed();
        return transferFrom0(src, position, count);
    }
    
    protected abstract long transferFrom0(FileChannel src, long position, long count) throws IOException;


    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        checkClosed();
        return transferFrom0(source, count, throughBuffer);
    }

    protected abstract long transferFrom0(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException;


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
    public Setter<? extends StreamSinkChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public void suspendWrites() {
        if (wsChannel.currentSender.peek() == this) {
            channel.suspendWrites();
        }
    }


    @Override
    public void resumeWrites() {
        if (wsChannel.currentSender.peek() == this) {
            channel.suspendWrites();
        }        
    }


    @Override
    public boolean isWriteResumed() {
        if (wsChannel.currentSender.peek() == this) {
            return channel.isWriteResumed();
        } else {
            return false;
        }
    }


    @Override
    public void wakeupWrites() {
        if (wsChannel.currentSender.peek() == this) {
            channel.wakeupWrites();
        }
        ChannelListeners.invokeChannelListener(this, writeSetter.get());

    }


    @Override
    public void shutdownWrites() throws IOException {
        if (writesDone.compareAndSet(false, true)) {
            flush();
        }
    }


    @Override
    public void awaitWritable() throws IOException {
        if (wsChannel.currentSender.peek() == this) {
            channel.awaitWritable();
        } else {
            try {
                synchronized (writeWaitLock) {
                    writeWaitLock.wait();
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }


    @Override
    public void awaitWritable(long time, TimeUnit timeUnit) throws IOException {
        if (wsChannel.currentSender.peek() == this) {
            channel.awaitWritable(time, timeUnit);
        } else {
            try {
                synchronized (writeWaitLock) {
                    writeWaitLock.wait(timeUnit.toMillis(time));
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }


    @Override
    public XnioExecutor getWriteThread() {
        return channel.getWriteThread();
    }


    @Override
    public boolean flush() throws IOException {
        if (wsChannel.currentSender.peek() == this) {
            return channel.flush();
        }
        return false;
    }

    private void checkClosed() throws IOException {
        if (!isOpen()) {
            throw new IOException("Channel already closed");
        }
    }
}
