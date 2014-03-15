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
package io.undertow.server.protocol.framed;

import io.undertow.UndertowMessages;
import io.undertow.conduits.IdleTimeoutConduit;
import io.undertow.util.ReferenceCountedPooled;
import io.undertow.websockets.core.WebSocketLogger;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static org.xnio.IoUtils.safeClose;

/**
 * A {@link org.xnio.channels.ConnectedChannel} which can be used to send and receive Frames.
 * <p/>
 * This provides a common base for framed protocols such as websockets and SPDY
 *
 * @author Stuart Douglas
 */
public abstract class AbstractFramedChannel<C extends AbstractFramedChannel<C, R, S>, R extends AbstractFramedStreamSourceChannel<C, R, S>, S extends AbstractFramedStreamSinkChannel<C, R, S>> implements ConnectedChannel {

    private final StreamConnection channel;
    private final IdleTimeoutConduit idleTimeoutConduit;

    private final ChannelListener.SimpleSetter<C> closeSetter;
    private final ChannelListener.SimpleSetter<C> receiveSetter;
    private final Pool<ByteBuffer> bufferPool;

    /**
     * Frame priority implementation. This is used to determine the order in which frames get sent
     */
    private final FramePriority<C, R, S> framePriority;

    /**
     * List of frames that are ready to send
     */
    private final List<S> pendingFrames = new LinkedList<S>();
    /**
     * Frames that are not yet read to send.
     */
    private final Deque<S> heldFrames = new ArrayDeque<S>();

    /**
     * new frames to be sent. These will be added to either the pending or held frames list
     * depending on the {@link #framePriority} implementation in use.
     */
    private final Deque<S> newFrames = new ArrayDeque<S>();

    private volatile R receiver = null;

    private boolean receivesSuspended = true;

    @SuppressWarnings("unused")
    private volatile int readsBroken = 0;

    @SuppressWarnings("unused")
    private volatile int writesBroken = 0;

    private static final AtomicIntegerFieldUpdater<AbstractFramedChannel> readsBrokenUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractFramedChannel.class, "readsBroken");
    private static final AtomicIntegerFieldUpdater<AbstractFramedChannel> writesBrokenUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractFramedChannel.class, "writesBroken");

    private ReferenceCountedPooled<ByteBuffer> readData = null;
    private final List<ChannelListener<C>> closeTasks = new CopyOnWriteArrayList<ChannelListener<C>>();

    /**
     * Create a new {@link io.undertow.server.protocol.framed.AbstractFramedChannel}
     * 8
     *
     * @param connectedStreamChannel The {@link org.xnio.channels.ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                               Be aware that it already must be "upgraded".
     * @param bufferPool             The {@link org.xnio.Pool} which will be used to acquire {@link java.nio.ByteBuffer}'s from.
     * @param framePriority
     */
    protected AbstractFramedChannel(final StreamConnection connectedStreamChannel, Pool<ByteBuffer> bufferPool, FramePriority<C, R, S> framePriority, final Pooled<ByteBuffer> readData) {
        this.framePriority = framePriority;
        if(readData != null) {
            this.readData = new ReferenceCountedPooled<ByteBuffer>(readData, 1);
        }
        IdleTimeoutConduit idle = new IdleTimeoutConduit(connectedStreamChannel.getSinkChannel().getConduit(), connectedStreamChannel.getSourceChannel().getConduit());
        connectedStreamChannel.getSourceChannel().setConduit(idle);
        connectedStreamChannel.getSinkChannel().setConduit(idle);
        this.idleTimeoutConduit = idle;
        this.channel = connectedStreamChannel;
        this.bufferPool = bufferPool;

        closeSetter = new ChannelListener.SimpleSetter<C>();
        receiveSetter = new ChannelListener.SimpleSetter<C>();
        channel.getSourceChannel().getReadSetter().set(null);
        channel.getSourceChannel().suspendReads();

        channel.getSourceChannel().getReadSetter().set(new FrameReadListener());
        connectedStreamChannel.getSinkChannel().getWriteSetter().set(new FrameWriteListener());
        connectedStreamChannel.getSinkChannel().getCloseSetter().set(new FrameCloseListener());
    }

    /**
     * Get the buffer pool for this connection.
     *
     * @return the buffer pool for this connection
     */
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return channel.getLocalAddress(type);
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return channel.getIoThread();
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
    public <T> T setOption(Option<T> option, T value) throws IOException {
        return channel.setOption(option, value);
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return channel.getPeerAddress(type);
    }

    /**
     * Get the source address of the Channel.
     *
     * @return the source address of the Channel
     */
    public InetSocketAddress getSourceAddress() {
        return getPeerAddress(InetSocketAddress.class);
    }

    /**
     * Get the destination address of the Channel.
     *
     * @return the destination address of the Channel
     */
    public InetSocketAddress getDestinationAddress() {
        return getLocalAddress(InetSocketAddress.class);
    }

    /**
     * receive method, returns null if no frame is ready. Otherwise returns a
     * channel that can be used to read the frame contents.
     * <p/>
     * Calling this method can also have the side effect of making additional data available to
     * existing source channels. In general if you suspend receives or don't have some other way
     * of calling this method then it can prevent frame channels for being fully consumed.
     */
    public synchronized R receive() throws IOException {
        if (isLastFrameReceived()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        if (receiver != null) {
            return null;
        }
        ReferenceCountedPooled<ByteBuffer> pooled = this.readData;
        boolean hasData;
        if (pooled == null) {
            Pooled<ByteBuffer> buf = bufferPool.allocate();
            this.readData = pooled = new ReferenceCountedPooled<ByteBuffer>(buf, 1);
            hasData = false;
        } else {
            hasData = pooled.getResource().hasRemaining();
        }
        boolean forceFree = false;
        int read = 0;
        try {
            if (!hasData) {
                pooled.getResource().clear();
                read = channel.getSourceChannel().read(pooled.getResource());
                if (read == 0) {
                    //no data, we just free the buffer
                    forceFree = true;
                    return null;
                } else if (read == -1) {
                    try {
                        channel.getSourceChannel().shutdownReads();
                    } catch (IOException e) {
                        if (WebSocketLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            WebSocketLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                        }
                        // nothing we can do here.. close
                        safeClose(channel.getSourceChannel());
                        throw e;
                    }
                    throw UndertowMessages.MESSAGES.channelIsClosed();
                }
                pooled.getResource().flip();
            }
            FrameHeaderData data = parseFrame(pooled.getResource());
            if (data != null) {
                Pooled<ByteBuffer> frameData;
                if (data.getFrameLength() > pooled.getResource().remaining()) {
                    frameData = pooled.createView(pooled.getResource().duplicate());
                    pooled.getResource().position(pooled.getResource().limit());
                } else {
                    ByteBuffer buf = pooled.getResource().duplicate();
                    buf.limit((int) (buf.position() + data.getFrameLength()));
                    pooled.getResource().position((int) (pooled.getResource().position() + data.getFrameLength()));
                    frameData = pooled.createView(buf);
                }
                AbstractFramedStreamSourceChannel<?, ?, ?> existing = data.getExistingChannel();
                if (existing != null) {
                    existing.dataReady(data, frameData);
                    if (data.getFrameLength() > frameData.getResource().remaining()) {
                        receiver = (R) existing;
                    }
                    return null;
                } else {
                    R newChannel = createChannel(data, frameData);
                    if (data.getFrameLength() > frameData.getResource().remaining()) {
                        receiver = newChannel;
                    }
                    return newChannel;
                }
            }
            return null;
        } catch (IOException e) {
            //something has code wrong with parsing, close the read side
            //we don't close the write side, as the underlying implementation will most likely want to send an error
            markReadsBroken(e);
            forceFree = true;
            throw e;
        } finally {
            if (!pooled.getResource().hasRemaining() || forceFree) {
                pooled.free();
                this.readData = null;
            }
        }
    }

    /**
     * Method that creates the actual stream source channel implementation that is in use.
     *
     * @param frameHeaderData The header data, as returned by {@link #parseFrame(java.nio.ByteBuffer)}
     * @param frameData       Any additional data for the frame that has already been read. This may not be the complete frame contents
     * @return A new stream source channel
     */
    protected abstract R createChannel(FrameHeaderData frameHeaderData, Pooled<ByteBuffer> frameData) throws IOException;

    /**
     * Attempts to parse an incoming frame header from the data in the buffer.
     *
     * @param data The data that has been read from the channel
     * @return The frame header data, or <code>null</code> if the data was incomplete
     * @throws IOException If the data could not be parsed.
     */
    protected abstract FrameHeaderData parseFrame(ByteBuffer data) throws IOException;

    protected synchronized void recalculateHeldFrames() {
        if(!heldFrames.isEmpty()) {
            framePriority.frameAdded(null, pendingFrames, heldFrames);
        }
    }

    /**
     * Flushes all ready stream sink conduits to the channel.
     * <p/>
     * Frames will be batched up, to allow them all to be written out via a gathering
     * write. The {@link #framePriority} implementation will be invoked to decide which
     * frames are eligible for sending and in what order.
     *
     * @throws IOException
     */
    protected synchronized void flushSenders() throws IOException {
        int toSend = 0;
        while (!newFrames.isEmpty()) {
            S frame = newFrames.poll();
            if (framePriority.insertFrame(frame, pendingFrames)) {
                if (!heldFrames.isEmpty()) {
                    framePriority.frameAdded(frame, pendingFrames, heldFrames);
                }
            } else {
                heldFrames.add(frame);
            }
        }

        boolean finalFrame = false;
        ListIterator<S> it = pendingFrames.listIterator();
        while (it.hasNext()) {
            S sender = it.next();
            if (sender.isReadyForFlush()) {
                ++toSend;
            } else {
                break;
            }
            if (sender.isLastFrame()) {
                finalFrame = true;
            }
        }
        if (toSend == 0) {
            return;
        }
        ByteBuffer[] data = new ByteBuffer[toSend * 3];
        int j = 0;
        it = pendingFrames.listIterator();
        while (j < toSend) {
            S next = it.next();
            //todo: rather than adding empty buffers just store the offsets
            SendFrameHeader frameHeader = next.getFrameHeader();
            data[j * 3] = frameHeader.getByteBuffer().getResource();
            data[(j * 3) + 1] = next.getBuffer();
            data[(j * 3) + 2] = next.getFrameFooter();
            ++j;
        }
        long toWrite = Buffers.remaining(data);

        long res;
        do {
            try {
                res = channel.getSinkChannel().write(data);
                toWrite -= res;
            } catch (IOException e) {
                IoUtils.safeClose(channel);
                markWritesBroken(e);
                throw e;
            }
        } while (res > 0 && toWrite > 0);
        int max = toSend;

        while (max > 0) {
            S sinkChannel = pendingFrames.get(0);
            if (sinkChannel.getFrameHeader().getByteBuffer().getResource().hasRemaining()
                    || sinkChannel.getBuffer().hasRemaining()
                    || sinkChannel.getFrameFooter().hasRemaining()) {
                break;
            }
            sinkChannel.flushComplete();
            pendingFrames.remove(sinkChannel);
            max--;
        }
        if (!pendingFrames.isEmpty()) {
            pendingFrames.get(0).activated();
        }
        if (pendingFrames.isEmpty() && finalFrame) {
            //all data has been sent. Close gracefully
            channel.getSinkChannel().shutdownWrites();
            if (!channel.getSinkChannel().flush()) {
                channel.getSinkChannel().setWriteListener(ChannelListeners.flushingChannelListener(null, null));
                channel.getSinkChannel().resumeWrites();
            }
        }
    }

    void awaitWritable() throws IOException {
        this.channel.getSinkChannel().awaitWritable();
    }

    void awaitWritable(long time, TimeUnit unit) throws IOException {
        this.channel.getSinkChannel().awaitWritable(time, unit);
    }

    /**
     * Queues a new frame to be sent, and attempts a flush if this is the first frame in the new frame queue.
     * <p/>
     * Depending on the {@link FramePriority} implementation in use the channel may or may not be added to the actual
     * pending queue
     *
     * @param channel The channel
     */
    protected synchronized void queueFrame(final S channel) throws IOException {
        assert !newFrames.contains(channel);
        if(isWritesBroken() || !this.channel.getSinkChannel().isOpen()) {
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        newFrames.add(channel);
        if (newFrames.peek() == channel) {
            flushSenders();
        }
    }

    /**
     * Returns true if the protocol specific final frame has been received.
     *
     * @return <code>true</code> If the last frame has been received
     */
    protected abstract boolean isLastFrameReceived();

    /**
     * @return <code>true</code> If the last frame has been sent
     */
    protected abstract boolean isLastFrameSent();

    /**
     * Method that is invoked when the read side of the channel is broken. This generally happens on a protocol error.
     */
    protected abstract void handleBrokenSourceChannel(Throwable e);

    /**
     * Method that is invoked when then write side of a channel is broken. This generally happens on a protocol error.
     */
    protected abstract void handleBrokenSinkChannel(Throwable e);

    /**
     * Return the {@link org.xnio.ChannelListener.Setter} which will holds the {@link org.xnio.ChannelListener} that gets notified once a frame was
     * received.
     */
    public Setter<C> getReceiveSetter() {
        return receiveSetter;
    }

    /**
     * Suspend the receive of new frames via {@link #receive()}
     */
    public synchronized void suspendReceives() {
        receivesSuspended = true;
        if (receiver == null) {
            channel.getSourceChannel().suspendReads();
        }
    }

    /**
     * Resume the receive of new frames via {@link #receive()}
     */
    public synchronized void resumeReceives() {
        receivesSuspended = false;
        if (receiver == null) {
            if (readData != null) {
                channel.getSourceChannel().wakeupReads();
            } else {
                channel.getSourceChannel().resumeReads();
            }
        }
    }

    public boolean isReceivesResumed() {
        return !receivesSuspended;
    }

    /**
     * Forcibly closes the {@link io.undertow.server.protocol.framed.AbstractFramedChannel}.
     *
     */
    @Override
    public void close() throws IOException {
        IoUtils.safeClose(channel);
        wakeupWrites();
    }

    @Override
    public Setter<? extends AbstractFramedChannel> getCloseSetter() {
        return closeSetter;
    }

    /**
     * Called when a source sub channel fails to fulfil its contract, and leaves the channel in an inconsistent state.
     * <p/>
     * The underlying read side will be forcibly closed.
     * @param cause The possibly null cause
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void markReadsBroken(Throwable cause) {
        if (readsBrokenUpdater.compareAndSet(this, 0 ,1)) {
            handleBrokenSourceChannel(cause);
            safeClose(channel.getSourceChannel());

            R receiver = this.receiver;
            if (receiver != null && receiver.isReadResumed()) {
                ChannelListeners.invokeChannelListener(receiver.getIoThread(), receiver, ((ChannelListener.SimpleSetter) receiver.getReadSetter()).get());
            }
        }
    }


    /**
     * Called when a sub channel fails to fulfil its contract, and leaves the channel in an inconsistent state.
     * <p/>
     * The underlying channel will be closed, and any sub channels that have writes resumed will have their
     * listeners notified. It is expected that these listeners will then attempt to use the channel, and their standard
     * error handling logic will take over.
     *
     *
     * @param cause The possibly null cause
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void markWritesBroken(Throwable cause) {
        if (writesBrokenUpdater.compareAndSet(this, 0, 1)) {
            handleBrokenSinkChannel(cause);
            safeClose(channel.getSinkChannel());
            synchronized (this) {
                for (final S channel : pendingFrames) {
                    channel.markBroken();
                }
                pendingFrames.clear();
                for (final S channel : newFrames) {
                    channel.markBroken();
                }
                newFrames.clear();
                for (final S channel : heldFrames) {
                    channel.markBroken();
                }
                heldFrames.clear();
            }
        }
    }

    protected boolean isWritesBroken() {
        return writesBrokenUpdater.get(this) != 0;
    }

    protected boolean isReadsBroken() {
        return readsBrokenUpdater.get(this) != 0;
    }


    void resumeWrites() {
        channel.getSinkChannel().resumeWrites();
    }

    void suspendWrites() {
        channel.getSinkChannel().suspendWrites();
    }

    void wakeupWrites() {
        channel.getSinkChannel().wakeupWrites();
    }

    StreamSourceChannel getSourceChannel() {
        return channel.getSourceChannel();
    }

    void notifyFrameReadComplete(AbstractFramedStreamSourceChannel<C, R, S> channel) {
        synchronized (AbstractFramedChannel.this) {
            if (isLastFrameReceived()) {
                IoUtils.safeClose(AbstractFramedChannel.this.channel.getSourceChannel());
            }
            if (channel == receiver) {
                receiver = null;
                if (receivesSuspended) {
                    AbstractFramedChannel.this.channel.getSourceChannel().suspendReads();
                } else {
                    AbstractFramedChannel.this.channel.getSourceChannel().resumeReads();
                }
            }
        }
    }

    /**
     * {@link org.xnio.ChannelListener} which delegates the read notification to the appropriate listener
     */
    private final class FrameReadListener implements ChannelListener<StreamSourceChannel> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void handleEvent(final StreamSourceChannel channel) {
            final R receiver = AbstractFramedChannel.this.receiver;
            if (receiver != null) {
                invokeReadListener(channel, receiver);
            } else if (isLastFrameReceived() || receivesSuspended) {
                channel.suspendReads();
                return;
            } else {
                final ChannelListener listener = receiveSetter.get();
                if (listener != null) {
                    WebSocketLogger.REQUEST_LOGGER.debugf("Invoking receive listener", receiver);
                    ChannelListeners.invokeChannelListener(AbstractFramedChannel.this, listener);
                    if (AbstractFramedChannel.this.receiver != null) {
                        //successful receive
                        //now invoke the read listener if necessary for performance reasons
                        invokeReadListener(channel, AbstractFramedChannel.this.receiver);
                    }
                } else {
                    channel.suspendReads();
                }
            }
            if (readData != null && channel.isOpen()) {
                ChannelListeners.invokeChannelListener(channel.getIoThread(), channel, this);
            }
        }

        private void invokeReadListener(StreamSourceChannel channel, R receiver) {
            final ChannelListener listener = ((SimpleSetter) receiver.getReadSetter()).get();
            if (listener != null) {
                WebSocketLogger.REQUEST_LOGGER.debugf("Invoking read listener %s on %s", listener, receiver);
                ChannelListeners.invokeChannelListener(receiver, listener);
            } else {
                WebSocketLogger.REQUEST_LOGGER.debugf("Suspending reads on channel %s due to no listener", receiver);
                channel.suspendReads();
            }
        }
    }

    private class FrameWriteListener implements ChannelListener<StreamSinkChannel> {
        @Override
        public void handleEvent(final StreamSinkChannel channel) {
            synchronized (AbstractFramedChannel.this) {
                //first we invoke the write listeners
                for (S sender : pendingFrames) {
                    if (sender.isWriteResumed()) {
                        ChannelListeners.invokeChannelListener(sender, sender.getWriteListener());
                    }
                }
                if(pendingFrames.isEmpty()) {
                    channel.suspendWrites();
                }
            }
        }
    }

    /**
     * close listener, just goes through and activates any sub channels to make sure their listeners are invoked
     */
    private class FrameCloseListener implements ChannelListener<StreamSinkChannel> {

        @Override
        public void handleEvent(final StreamSinkChannel c) {
            R receiver = AbstractFramedChannel.this.receiver;
            try {
            if (receiver != null && receiver.isOpen() && receiver.isReadResumed()) {
                ChannelListeners.invokeChannelListener(receiver, ((SimpleSetter) receiver.getReadSetter()).get());
            }
            synchronized (AbstractFramedChannel.this) {
                for (final S channel : pendingFrames) {
                    //if this was a clean shutdown there should not be any senders
                    channel.markBroken();
                }
                for (final S channel : newFrames) {
                    //if this was a clean shutdown there should not be any senders
                    channel.markBroken();
                }
                for (final S channel : heldFrames) {
                    //if this was a clean shutdown there should not be any senders
                    channel.markBroken();
                }
            }
            } finally {
                try {
                    for(ChannelListener<C> task : closeTasks) {
                        ChannelListeners.invokeChannelListener((C)AbstractFramedChannel.this, task);
                    }
                } finally {
                    ChannelListeners.invokeChannelListener((C) AbstractFramedChannel.this, closeSetter.get());
                }
            }
        }
    }

    public void setIdleTimeout(long timeout) {
        idleTimeoutConduit.setIdleTimeout(timeout);
    }

    public long getIdleTimeout() {
        return idleTimeoutConduit.getIdleTimeout();
    }

    protected FramePriority<C, R, S> getFramePriority() {
        return framePriority;
    }

    public void addCloseTask(final ChannelListener<C> task) {
        closeTasks.add(task);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[ " + (receiver == null ? "No Receiver" : receiver.toString()) + " " + pendingFrames.toString() + " -- " + heldFrames.toString() + " -- " + newFrames.toString()+ "]" ;
    }

    protected StreamConnection getUnderlyingConnection() {
        return channel;
    }
}
