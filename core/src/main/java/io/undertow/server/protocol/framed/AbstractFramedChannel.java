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
package io.undertow.server.protocol.framed;

import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import org.xnio.Buffers;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.CloseableChannel;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.UndertowMessages;
import io.undertow.conduits.IdleTimeoutConduit;
import io.undertow.util.ReferenceCountedPooled;
import org.xnio.channels.SuspendableWriteChannel;

/**
 * A {@link org.xnio.channels.ConnectedChannel} which can be used to send and receive Frames.
 * <p>
 * This provides a common base for framed protocols such as websockets and SPDY
 *
 * @author Stuart Douglas
 */
public abstract class AbstractFramedChannel<C extends AbstractFramedChannel<C, R, S>, R extends AbstractFramedStreamSourceChannel<C, R, S>, S extends AbstractFramedStreamSinkChannel<C, R, S>> implements ConnectedChannel {

    /**
     * The maximum number of buffers we will queue before suspending reads and
     * waiting for the buffers to be consumed
     *
     * TODO: make the configurable
     */
    private final int maxQueuedBuffers;

    private final StreamConnection channel;
    private final IdleTimeoutConduit idleTimeoutConduit;

    private final ChannelListener.SimpleSetter<C> closeSetter;
    private final ChannelListener.SimpleSetter<C> receiveSetter;
    private final ByteBufferPool bufferPool;

    /**
     * Frame priority implementation. This is used to determine the order in which frames get sent
     */
    private final FramePriority<C, R, S> framePriority;

    /**
     * List of frames that are ready to send
     */
    private final List<S> pendingFrames = new LinkedList<>();
    /**
     * Frames that are not yet read to send.
     */
    private final Deque<S> heldFrames = new ArrayDeque<>();

    /**
     * new frames to be sent. These will be added to either the pending or held frames list
     * depending on the {@link #framePriority} implementation in use.
     */
    private final Deque<S> newFrames = new LinkedBlockingDeque<>();

    private volatile long frameDataRemaining;
    private volatile R receiver;

    private volatile boolean receivesSuspended = true;

    @SuppressWarnings("unused")
    private volatile int readsBroken = 0;

    @SuppressWarnings("unused")
    private volatile int writesBroken = 0;

    private static final AtomicIntegerFieldUpdater<AbstractFramedChannel> readsBrokenUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractFramedChannel.class, "readsBroken");
    private static final AtomicIntegerFieldUpdater<AbstractFramedChannel> writesBrokenUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractFramedChannel.class, "writesBroken");

    private volatile ReferenceCountedPooled readData = null;
    private final List<ChannelListener<C>> closeTasks = new CopyOnWriteArrayList<>();
    private volatile boolean flushingSenders = false;

    private final Set<AbstractFramedStreamSourceChannel<C, R, S>> receivers = new HashSet<>();

    @SuppressWarnings("unused")
    private volatile int outstandingBuffers;
    private volatile AtomicIntegerFieldUpdater<AbstractFramedChannel> outstandingBuffersUpdater = AtomicIntegerFieldUpdater.newUpdater(AbstractFramedChannel.class, "outstandingBuffers");

    private final LinkedBlockingDeque<Runnable> taskRunQueue = new LinkedBlockingDeque<>();
    private final OptionMap settings;

    /**
     * If this is true then the flush() method must be called to queue writes. This is provided to support batching
     */
    private volatile boolean requireExplicitFlush = false;
    private volatile boolean readChannelDone = false;

    private final ReferenceCountedPooled.FreeNotifier freeNotifier = new ReferenceCountedPooled.FreeNotifier() {
        @Override
        public void freed() {
            int res = outstandingBuffersUpdater.decrementAndGet(AbstractFramedChannel.this);
            if(!receivesSuspended && res == maxQueuedBuffers - 1) {
                synchronized (AbstractFramedChannel.this) {
                    if(outstandingBuffersUpdater.get(AbstractFramedChannel.this) < maxQueuedBuffers) {
                        if(UndertowLogger.REQUEST_IO_LOGGER.isTraceEnabled()) {
                            UndertowLogger.REQUEST_IO_LOGGER.tracef("Resuming reads on %s as buffers have been consumed", AbstractFramedChannel.this);
                        }
                        channel.getSourceChannel().resumeReads();
                    }
                }
            }
        }
    };

    private static final ChannelListener<AbstractFramedChannel> DRAIN_LISTENER = new ChannelListener<AbstractFramedChannel>() {
        @Override
        public void handleEvent(AbstractFramedChannel channel) {
            try {
                AbstractFramedStreamSourceChannel stream = channel.receive();
                if(stream != null) {
                    UndertowLogger.REQUEST_IO_LOGGER.debugf("Draining channel %s as no receive listener has been set", stream);
                    stream.getReadSetter().set(ChannelListeners.drainListener(Long.MAX_VALUE, null, null));
                    stream.wakeupReads();
                }
            } catch (IOException e) {
                IoUtils.safeClose(channel);
            }
        }
    };

    /**
     * Create a new {@link io.undertow.server.protocol.framed.AbstractFramedChannel}
     * 8
     *  @param connectedStreamChannel The {@link org.xnio.channels.ConnectedStreamChannel} over which the Frames should get send and received.
     *                               Be aware that it already must be "upgraded".
     * @param bufferPool             The {@link ByteBufferPool} which will be used to acquire {@link ByteBuffer}'s from.
     * @param framePriority
     * @param settings               The settings
     */
    protected AbstractFramedChannel(final StreamConnection connectedStreamChannel, ByteBufferPool bufferPool, FramePriority<C, R, S> framePriority, final PooledByteBuffer readData, OptionMap settings) {
        this.framePriority = framePriority;
        this.maxQueuedBuffers = settings.get(UndertowOptions.MAX_QUEUED_READ_BUFFERS, 10);
        this.settings = settings;
        if (readData != null) {
            if(readData.getBuffer().hasRemaining()) {
                this.readData = new ReferenceCountedPooled(readData, 1);
            } else {
                readData.close();
            }
        }
        if(bufferPool == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("bufferPool");
        }
        if(connectedStreamChannel == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("connectedStreamChannel");
        }
        IdleTimeoutConduit idle = createIdleTimeoutChannel(connectedStreamChannel);
        connectedStreamChannel.getSourceChannel().setConduit(idle);
        connectedStreamChannel.getSinkChannel().setConduit(idle);
        this.idleTimeoutConduit = idle;
        this.channel = connectedStreamChannel;
        this.bufferPool = bufferPool;

        closeSetter = new ChannelListener.SimpleSetter<>();
        receiveSetter = new ChannelListener.SimpleSetter<>();
        channel.getSourceChannel().getReadSetter().set(null);
        channel.getSourceChannel().suspendReads();

        channel.getSourceChannel().getReadSetter().set(new FrameReadListener());
        connectedStreamChannel.getSinkChannel().getWriteSetter().set(new FrameWriteListener());
        FrameCloseListener closeListener = new FrameCloseListener();
        connectedStreamChannel.getSinkChannel().getCloseSetter().set(closeListener);
        connectedStreamChannel.getSourceChannel().getCloseSetter().set(closeListener);
    }

    protected IdleTimeoutConduit createIdleTimeoutChannel(StreamConnection connectedStreamChannel) {
        return new IdleTimeoutConduit(connectedStreamChannel);
    }

    void runInIoThread(Runnable task) {
        this.taskRunQueue.add(task);
        try {
            getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    while (!taskRunQueue.isEmpty()) {
                        taskRunQueue.poll().run();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            //thread is shutting down
            while (!taskRunQueue.isEmpty()) {
                taskRunQueue.poll().run();
            }
        }
    }

    /**
     * Get the buffer pool for this connection.
     *
     * @return the buffer pool for this connection
     */
    public ByteBufferPool getBufferPool() {
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
     * <p>
     * Calling this method can also have the side effect of making additional data available to
     * existing source channels. In general if you suspend receives or don't have some other way
     * of calling this method then it can prevent frame channels for being fully consumed.
     */
    public synchronized R receive() throws IOException {
        if (readChannelDone && receiver == null) {
            //we have received the last frame, we just shut down and return
            //it would probably make more sense to have the last channel responsible for this
            //however it is much simpler just to have it here
            if(readData != null) {
                readData.close();
                readData = null;
            }
            channel.getSourceChannel().suspendReads();
            channel.getSourceChannel().shutdownReads();
            return null;
        }
        ReferenceCountedPooled pooled = this.readData;
        boolean hasData;
        if (pooled == null) {
            pooled = allocateReferenceCountedBuffer();
            if (pooled == null) {
                return null;
            }
            hasData = false;
        } else if(pooled.isFreed()) {
            //we attempt to re-used an existing buffer
            if(!pooled.tryUnfree()) {
                pooled = allocateReferenceCountedBuffer();
                if (pooled == null) {
                    return null;
                }
            } else {
                pooled.getBuffer().limit(pooled.getBuffer().capacity());
            }
            hasData = false;
        } else {
            hasData = pooled.getBuffer().hasRemaining();
        }
        boolean forceFree = false;
        int read = 0;
        try {
            if (!hasData) {
                pooled.getBuffer().clear();
                read = channel.getSourceChannel().read(pooled.getBuffer());
                if (read == 0) {
                    //no data, we just free the buffer
                    forceFree = true;
                    return null;
                } else if (read == -1) {
                    forceFree = true;
                    readChannelDone = true;
                    lastDataRead();
                    return null;
                } else if(isLastFrameReceived() && frameDataRemaining == 0) {
                    //we got data, although we should have received the last frame
                    forceFree = true;
                    markReadsBroken(new ClosedChannelException());
                }
                pooled.getBuffer().flip();
            }
            if (frameDataRemaining > 0) {
                if (frameDataRemaining >= pooled.getBuffer().remaining()) {
                    frameDataRemaining -= pooled.getBuffer().remaining();
                    if(receiver != null) {
                        //we still create a pooled view, this means that if the buffer is still active we can re-used it
                        //which prevents attacks based on sending lots of small fragments
                        ByteBuffer buf = pooled.getBuffer().duplicate();
                        pooled.getBuffer().position(pooled.getBuffer().limit());
                        PooledByteBuffer frameData = pooled.createView(buf);
                        receiver.dataReady(null, frameData);
                    } else {
                        //we are dropping a frame
                        pooled.close();
                        readData = null;
                    }
                    if(frameDataRemaining == 0) {
                        receiver = null;
                    }
                    return null;
                } else {
                    ByteBuffer buf = pooled.getBuffer().duplicate();
                    buf.limit((int) (buf.position() + frameDataRemaining));
                    pooled.getBuffer().position((int) (pooled.getBuffer().position() + frameDataRemaining));
                    frameDataRemaining = 0;
                    PooledByteBuffer frameData = pooled.createView(buf);
                    if(receiver != null) {
                        receiver.dataReady(null, frameData);
                    } else{
                        //we are dropping the frame
                        frameData.close();
                    }
                    receiver = null;
                }
                //if we read data into a frame we just return immediately, even if there is more remaining
                //see https://issues.jboss.org/browse/UNDERTOW-410
                //basically if we don't do this we loose some message ordering semantics
                //as the second message may be processed before the first one

                //this is problematic for HTTPS, where the read listener may also be invoked by a queued task
                //and not by the selector mechanism
                return null;
            }
            FrameHeaderData data = parseFrame(pooled.getBuffer());
            if (data != null) {
                PooledByteBuffer frameData;
                if (data.getFrameLength() >= pooled.getBuffer().remaining()) {
                    frameDataRemaining = data.getFrameLength() - pooled.getBuffer().remaining();
                    frameData = pooled.createView(pooled.getBuffer().duplicate());
                    pooled.getBuffer().position(pooled.getBuffer().limit());
                } else {
                    ByteBuffer buf = pooled.getBuffer().duplicate();
                    buf.limit((int) (buf.position() + data.getFrameLength()));
                    pooled.getBuffer().position((int) (pooled.getBuffer().position() + data.getFrameLength()));
                    frameData = pooled.createView(buf);
                }
                AbstractFramedStreamSourceChannel<?, ?, ?> existing = data.getExistingChannel();
                if (existing != null) {
                    if (data.getFrameLength() > frameData.getBuffer().remaining()) {
                        receiver = (R) existing;
                    }
                    existing.dataReady(data, frameData);
                    if(isLastFrameReceived()) {
                        handleLastFrame(existing);
                    }
                    return null;
                } else {
                    boolean moreData = data.getFrameLength() > frameData.getBuffer().remaining();
                    R newChannel = createChannel(data, frameData);
                    if (newChannel != null) {
                        if(!newChannel.isComplete()) {
                            receivers.add(newChannel);
                        }
                        if (moreData) {
                            receiver = newChannel;
                        }

                        if(isLastFrameReceived()) {
                            handleLastFrame(newChannel);
                        }
                    } else {
                        frameData.close();
                    }
                    return newChannel;
                }
            }
            return null;
        } catch (IOException|RuntimeException e) {
            //something has code wrong with parsing, close the read side
            //we don't close the write side, as the underlying implementation will most likely want to send an error
            markReadsBroken(e);
            forceFree = true;
            throw e;
        }finally {
            //if the receive caused the channel to break the close listener may be have been called
            //which will make readData null
            if (readData != null) {
                if (!pooled.getBuffer().hasRemaining() || forceFree) {
                    if(pooled.getBuffer().limit() * 2 > pooled.getBuffer().capacity() || forceFree) {
                        //if we have used more than half the buffer we don't allow it to be re-aquired
                        readData = null;
                    }
                    //even though this is freed we may un-free it if we get a new packet
                    //this prevents many small reads resulting in a large number of allocated buffers
                    pooled.close();

                }
            }
        }
    }

    /**
     * Called when the last frame has been received (note that their may still be data from the last frame than needs to be read)
     * @param newChannel The channel that received the last frame
     */
    private void handleLastFrame(AbstractFramedStreamSourceChannel newChannel) {
        //make a defensive copy
        Set<AbstractFramedStreamSourceChannel<C, R, S>> receivers = new HashSet<>(this.receivers);
        for(AbstractFramedStreamSourceChannel<C, R, S> r : receivers) {
            if(r != newChannel) {
                r.markStreamBroken();
            }
        }
    }

    private ReferenceCountedPooled allocateReferenceCountedBuffer() {
        if(maxQueuedBuffers > 0) {
            int expect;
            do {
                expect = outstandingBuffersUpdater.get(this);
                if (expect == maxQueuedBuffers) {
                    synchronized (this) {
                        //we need to re-read in a sync block, to prevent races
                        expect = outstandingBuffersUpdater.get(this);
                        if (expect == maxQueuedBuffers) {
                            if(UndertowLogger.REQUEST_IO_LOGGER.isTraceEnabled()) {
                                UndertowLogger.REQUEST_IO_LOGGER.tracef("Suspending reads on %s due to too many outstanding buffers", this);
                            }
                            channel.getSourceChannel().suspendReads();
                            return null;
                        }
                    }
                }
            } while (!outstandingBuffersUpdater.compareAndSet(this, expect, expect + 1));
        }
        PooledByteBuffer buf = bufferPool.allocate();
        return this.readData = new ReferenceCountedPooled(buf, 1, maxQueuedBuffers > 0 ? freeNotifier : null);
    }

    /**
     * Method than is invoked when read() returns -1.
     */
    protected void lastDataRead() {

    }

    /**
     * Method that creates the actual stream source channel implementation that is in use.
     *
     * @param frameHeaderData The header data, as returned by {@link #parseFrame(java.nio.ByteBuffer)}
     * @param frameData       Any additional data for the frame that has already been read. This may not be the complete frame contents
     * @return A new stream source channel
     */
    protected abstract R createChannel(FrameHeaderData frameHeaderData, PooledByteBuffer frameData) throws IOException;

    /**
     * Attempts to parse an incoming frame header from the data in the buffer.
     *
     * @param data The data that has been read from the channel
     * @return The frame header data, or <code>null</code> if the data was incomplete
     * @throws IOException If the data could not be parsed.
     */
    protected abstract FrameHeaderData parseFrame(ByteBuffer data) throws IOException;

    protected synchronized void recalculateHeldFrames() throws IOException {
        if (!heldFrames.isEmpty()) {
            framePriority.frameAdded(null, pendingFrames, heldFrames);
            flushSenders();
        }
    }

    /**
     * Flushes all ready stream sink conduits to the channel.
     * <p>
     * Frames will be batched up, to allow them all to be written out via a gathering
     * write. The {@link #framePriority} implementation will be invoked to decide which
     * frames are eligible for sending and in what order.
     */
    protected synchronized void flushSenders() {
        if(flushingSenders) {
            throw UndertowMessages.MESSAGES.recursiveCallToFlushingSenders();
        }
        flushingSenders = true;
        try {
            int toSend = 0;
            while (!newFrames.isEmpty()) {
                S frame = newFrames.poll();
                frame.preWrite();
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
                //if there is nothing to send we just attempt a flush on the underlying channel
                try {
                    if(channel.getSinkChannel().flush()) {
                        channel.getSinkChannel().suspendWrites();
                    }
                } catch (IOException e) {
                    safeClose(channel);
                    markWritesBroken(e);
                }
                return;
            }
            ByteBuffer[] data = new ByteBuffer[toSend * 3];
            int j = 0;
            it = pendingFrames.listIterator();
            try {
                while (j < toSend) {
                    S next = it.next();
                    //todo: rather than adding empty buffers just store the offsets
                    SendFrameHeader frameHeader = next.getFrameHeader();
                    PooledByteBuffer frameHeaderByteBuffer = frameHeader.getByteBuffer();
                    ByteBuffer frameTrailerBuffer = frameHeader.getTrailer();
                    data[j * 3] = frameHeaderByteBuffer != null
                            ? frameHeaderByteBuffer.getBuffer()
                            : Buffers.EMPTY_BYTE_BUFFER;
                    data[(j * 3) + 1] = next.getBuffer() == null ? Buffers.EMPTY_BYTE_BUFFER : next.getBuffer();
                    data[(j * 3) + 2] = frameTrailerBuffer != null ? frameTrailerBuffer : Buffers.EMPTY_BYTE_BUFFER;
                    ++j;
                }
                long toWrite = Buffers.remaining(data);
                long res;
                do {
                    res = channel.getSinkChannel().write(data);
                    toWrite -= res;
                } while (res > 0 && toWrite > 0);
                int max = toSend;

                while (max > 0) {
                    S sinkChannel = pendingFrames.get(0);
                    PooledByteBuffer frameHeaderByteBuffer = sinkChannel.getFrameHeader().getByteBuffer();
                    ByteBuffer frameTrailerBuffer = sinkChannel.getFrameHeader().getTrailer();
                    if (frameHeaderByteBuffer != null && frameHeaderByteBuffer.getBuffer().hasRemaining()
                            || sinkChannel.getBuffer() != null && sinkChannel.getBuffer().hasRemaining()
                            || frameTrailerBuffer != null && frameTrailerBuffer.hasRemaining()) {
                        break;
                    }
                    sinkChannel.flushComplete();
                    pendingFrames.remove(sinkChannel);
                    max--;
                }
                if (!pendingFrames.isEmpty() || !channel.getSinkChannel().flush()) {
                    channel.getSinkChannel().resumeWrites();
                } else {
                    channel.getSinkChannel().suspendWrites();
                }
                if (pendingFrames.isEmpty() && finalFrame) {
                    //all data has been sent. Close gracefully
                    channel.getSinkChannel().shutdownWrites();
                    if (!channel.getSinkChannel().flush()) {
                        channel.getSinkChannel().setWriteListener(ChannelListeners.flushingChannelListener(null, null));
                        channel.getSinkChannel().resumeWrites();
                    }
                }

            } catch (IOException e) {
                safeClose(channel);
                markWritesBroken(e);
            }
        } finally {
            flushingSenders = false;
            if(!newFrames.isEmpty()) {
                runInIoThread(new Runnable() {
                    @Override
                    public void run() {
                        flushSenders();
                    }
                });
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
     * <p>
     * Depending on the {@link FramePriority} implementation in use the channel may or may not be added to the actual
     * pending queue
     *
     * @param channel The channel
     */
    protected void queueFrame(final S channel) throws IOException {
        assert !newFrames.contains(channel);
        if (isWritesBroken() || !this.channel.getSinkChannel().isOpen() || channel.isBroken() || !channel.isOpen()) {
            IoUtils.safeClose(channel);
            throw UndertowMessages.MESSAGES.channelIsClosed();
        }
        newFrames.add(channel);

        if (!requireExplicitFlush || channel.isBufferFull()) {
            flush();
        }
    }

    public void flush() {
        if (!flushingSenders) {
            if(channel.getIoThread() == Thread.currentThread()) {
                flushSenders();
            } else {
                runInIoThread(new Runnable() {
                    @Override
                    public void run() {
                        flushSenders();
                    }
                });
            }
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
        if (readData != null && !readData.isFreed()) {
            channel.getSourceChannel().wakeupReads();
        } else {
            channel.getSourceChannel().resumeReads();
        }
    }

    public boolean isReceivesResumed() {
        return !receivesSuspended;
    }

    /**
     * Forcibly closes the {@link io.undertow.server.protocol.framed.AbstractFramedChannel}.
     */
    @Override
    public void close() throws IOException {
        safeClose(channel);
        if(readData != null) {
            readData.close();
            readData = null;
        }
    }

    @Override
    public Setter<? extends AbstractFramedChannel> getCloseSetter() {
        return closeSetter;
    }

    /**
     * Called when a source sub channel fails to fulfil its contract, and leaves the channel in an inconsistent state.
     * <p>
     * The underlying read side will be forcibly closed.
     *
     * @param cause The possibly null cause
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void markReadsBroken(Throwable cause) {
        if (readsBrokenUpdater.compareAndSet(this, 0, 1)) {
            if(receiver != null) {
                receiver.markStreamBroken();
            }
            for(AbstractFramedStreamSourceChannel<C, R, S> r : receivers) {
                r.markStreamBroken();
            }

            handleBrokenSourceChannel(cause);
            safeClose(channel.getSourceChannel());
            closeSubChannels();
        }
    }

    /**
     * Method that is called when the channel is being forcibly closed, and all sub stream sink/source
     * channels should also be forcibly closed.
     */
    protected abstract void closeSubChannels();



    /**
     * Called when a sub channel fails to fulfil its contract, and leaves the channel in an inconsistent state.
     * <p>
     * The underlying channel will be closed, and any sub channels that have writes resumed will have their
     * listeners notified. It is expected that these listeners will then attempt to use the channel, and their standard
     * error handling logic will take over.
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

    }

    void notifyClosed(AbstractFramedStreamSourceChannel<C, R, S> channel) {
        synchronized (AbstractFramedChannel.this) {
            receivers.remove(channel);
        }
    }


    /**
     * {@link org.xnio.ChannelListener} which delegates the read notification to the appropriate listener
     */
    private final class FrameReadListener implements ChannelListener<StreamSourceChannel> {
        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void handleEvent(final StreamSourceChannel channel) {
            //clear the task queue before reading
            while (!taskRunQueue.isEmpty()) {
                taskRunQueue.poll().run();
            }

            final R receiver = AbstractFramedChannel.this.receiver;
            if ((readChannelDone || receivesSuspended) && receiver == null) {
                channel.suspendReads();
                return;
            } else {
                ChannelListener listener = receiveSetter.get();
                if(listener == null) {
                    listener = DRAIN_LISTENER;
                }
                UndertowLogger.REQUEST_IO_LOGGER.tracef("Invoking receive listener", receiver);
                ChannelListeners.invokeChannelListener(AbstractFramedChannel.this, listener);
            }
            if (readData != null  && !readData.isFreed() && channel.isOpen()) {
                try {
                    runInIoThread(new Runnable() {
                        @Override
                        public void run() {
                            ChannelListeners.invokeChannelListener(channel, FrameReadListener.this);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    IoUtils.safeClose(AbstractFramedChannel.this);
                }
            }
        }
    }

    private class FrameWriteListener implements ChannelListener<StreamSinkChannel> {
        @Override
        public void handleEvent(final StreamSinkChannel channel) {
            flushSenders();
        }
    }

    /**
     * close listener, just goes through and activates any sub channels to make sure their listeners are invoked
     */
    private class FrameCloseListener implements ChannelListener<CloseableChannel> {

        private boolean sinkClosed;
        private boolean sourceClosed;

        @Override
        public void handleEvent(final CloseableChannel c) {

            if (Thread.currentThread() != c.getIoThread() && !c.getWorker().isShutdown()) {
                runInIoThread(new Runnable() {
                    @Override
                    public void run() {
                        ChannelListeners.invokeChannelListener(c, FrameCloseListener.this);
                    }
                });
                return;
            }


            if(c instanceof  StreamSinkChannel) {
                sinkClosed = true;
            } else if(c instanceof StreamSourceChannel) {
                sourceClosed = true;
            }
            if(!sourceClosed || !sinkClosed) {
                return; //both sides need to be closed
            } else if(readData != null && !readData.isFreed()) {
                //we make sure there is no data left to receive, if there is then we invoke the receive listener
                runInIoThread(new Runnable() {
                    @Override
                    public void run() {
                        while (readData != null  && !readData.isFreed()) {
                            int rem = readData.getBuffer().remaining();
                            ChannelListener listener = receiveSetter.get();
                            if(listener == null) {
                                listener = DRAIN_LISTENER;
                            }
                            ChannelListeners.invokeChannelListener(AbstractFramedChannel.this, listener);
                            if(!AbstractFramedChannel.this.isOpen()) {
                                break;
                            }
                            if (readData != null && rem == readData.getBuffer().remaining()) {
                                break;//make sure we are making progress
                            }
                        }
                        handleEvent(c);
                    }
                });

                return;
            }
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
                    for(AbstractFramedStreamSourceChannel<C, R, S> r : new ArrayList<>(receivers)) {
                        IoUtils.safeClose(r);
                    }
                }
            } finally {
                try {
                    for (ChannelListener<C> task : closeTasks) {
                        ChannelListeners.invokeChannelListener((C) AbstractFramedChannel.this, task);
                    }
                } finally {
                    synchronized (AbstractFramedChannel.this) {
                        closeSubChannels();
                        if (readData != null) {
                            readData.close();
                            readData = null;
                        }
                    }
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
        return getClass().getSimpleName() + " peer " + channel.getPeerAddress() + " local " + channel.getLocalAddress() + "[ " + (receiver == null ? "No Receiver" : receiver.toString()) + " " + pendingFrames.toString() + " -- " + heldFrames.toString() + " -- " + newFrames.toString() + "]";
    }

    protected StreamConnection getUnderlyingConnection() {
        return channel;
    }



    protected ChannelExceptionHandler<SuspendableWriteChannel> writeExceptionHandler() {
        return new ChannelExceptionHandler<SuspendableWriteChannel>() {
            @Override
            public void handleException(SuspendableWriteChannel channel, IOException exception) {
                markWritesBroken(exception);
            }
        };
    }

    public boolean isRequireExplicitFlush() {
        return requireExplicitFlush;
    }

    public void setRequireExplicitFlush(boolean requireExplicitFlush) {
        this.requireExplicitFlush = requireExplicitFlush;
    }

    protected OptionMap getSettings() {
        return settings;
    }
}
