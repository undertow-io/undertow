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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.undertow.UndertowLogger;
import io.undertow.websockets.version00.WebSocket00Channel;
import org.xnio.ChannelListener;
import org.xnio.ChannelListener.Setter;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import static org.xnio.IoUtils.safeClose;

/**
 * A {@link ConnectedChannel} which can be used to send and receive WebSocket Frames.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author Stuart Douglas
 */
public abstract class WebSocketChannel implements ConnectedChannel {

    private final Queue<StreamSinkFrameChannel> senders = new ConcurrentLinkedQueue<StreamSinkFrameChannel>();
    private final ConnectedStreamChannel channel;
    private final WebSocketVersion version;
    private final String wsUrl;
    private final Setter<WebSocketChannel> closeSetter;
    private final ChannelListener.SimpleSetter<WebSocketChannel> receiveSetter;
    private final PushBackStreamChannel pushBackStreamChannel;
    private final Pool<ByteBuffer> bufferPool;

    private volatile StreamSourceFrameChannel receiver;
    /**
     * an incoming frame that has not been created yet
     */
    private volatile PartialFrame partialFrame;

    private boolean receivesSuspended;

    /**
     * Create a new {@link WebSocketChannel}
     * 8
     *
     * @param channel    The {@link ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                   Be aware that it already must be "upgraded".
     * @param bufferPool The {@link Pool} which will be used to acquire {@link ByteBuffer}'s from.
     * @param version    The {@link WebSocketVersion} of the {@link WebSocketChannel}
     * @param wsUrl      The url for which the {@link WebSocket00Channel} was created.
     */
    protected WebSocketChannel(final ConnectedStreamChannel channel, Pool<ByteBuffer> bufferPool, WebSocketVersion version, String wsUrl) {
        this.channel = channel;
        this.version = version;
        this.wsUrl = wsUrl;
        this.bufferPool = bufferPool;
        this.closeSetter = ChannelListeners.getDelegatingSetter(channel.getCloseSetter(), this);
        this.receiveSetter = new ChannelListener.SimpleSetter<WebSocketChannel>();
        pushBackStreamChannel = new PushBackStreamChannel(channel);
        pushBackStreamChannel.getReadSetter().set(new WebSocketReadListener());
        channel.getWriteSetter().set(new WebSocketWriteListener());
    }


    /**
     * Get the buffer pool for this connection.
     *
     * @return the buffer pool for this connection
     */
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    protected boolean isInUse(StreamSinkChannel channel) {
        return senders.peek() == channel;
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
     * Get the request URI scheme. Normally this is one of {@code ws} or {@code wss}.
     *
     * @return the request URI scheme
     */
    public String getRequestScheme() {
        if (getUrl().startsWith("wss:")) {
            return "wss";
        } else {
            return "ws";
        }
    }

    /**
     * Return <code>true</code> if this is handled via WebSocket Secure.
     */
    public boolean isSecure() {
        return getRequestScheme().equals("wss");
    }

    /**
     * Return the URL of the WebSocket endpoint.
     *
     * @return url The URL of the endpoint
     */
    public String getUrl() {
        return wsUrl;
    }

    /**
     * Return the {@link WebSocketVersion} which is used
     *
     * @return version The {@link WebSocketVersion} which is in use
     */
    public WebSocketVersion getVersion() {
        return version;
    }

    /**
     * Get the source address of the WebSocket Channel.
     *
     * @return the source address of the WebSocket Channel
     */
    public InetSocketAddress getSourceAddress() {
        return getPeerAddress(InetSocketAddress.class);
    }

    /**
     * Get the destination address of the WebSocket Channel.
     *
     * @return the destination address of the WebSocket Channel
     */
    public InetSocketAddress getDestinationAddress() {
        return getLocalAddress(InetSocketAddress.class);
    }


    /**
     * Async receive, returns null if no frame is ready. Otherwise returns a
     * channel that can be used to read the frame contents.
     */
    public StreamSourceFrameChannel receive() throws IOException {
        final Pooled<ByteBuffer> pooled = getBufferPool().allocate();
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;

        try {
            PartialFrame partialFrame = this.partialFrame;
            if (partialFrame == null) {
                partialFrame = receiveFrame(new StreamSourceChannelControl());
            }

            int res;
            while (!partialFrame.isDone()) {
                buffer.clear();
                try {
                    res = channel.read(buffer);
                } catch (IOException e) {
                    if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                        UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
                    }
                    safeClose(channel);
                    throw e;
                }
                if (res == 0) {
                    return null;
                }
                if (res == -1) {
                    try {
                        channel.shutdownReads();

                    } catch (IOException e) {
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                        }
                        // nothing we can do here.. close
                        IoUtils.safeClose(channel);
                        throw e;
                    }
                    throw WebSocketMessages.MESSAGES.channelClosed();
                }
                buffer.flip();
                try {
                    partialFrame.handle(buffer, pushBackStreamChannel);
                } catch (WebSocketException e) {
                    //the data was corrupt
                    if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                        UndertowLogger.REQUEST_LOGGER.debugf(e, "receive failed due to Exception");
                    }
                    // nothing we can do here.. close
                    IoUtils.safeClose(channel);
                    throw new IOException(e);
                }
            }

            if (buffer.hasRemaining()) {
                // something was left in the buffer, push it back so it can be processed by the actual Source
                pushBackStreamChannel.unget(pooled);
                free = false;
            }

            pushBackStreamChannel.suspendReads();
            this.partialFrame = null;
            return partialFrame.getChannel();

        } finally {
            if (free) {
                pooled.free();
            }
        }
    }


    public Setter<WebSocketChannel> getReceiveSetter() {
        return receiveSetter;
    }

    public synchronized void suspendReceives() {
        receivesSuspended = true;
        pushBackStreamChannel.suspendReads();
    }

    public synchronized void resumeReceives() {
        receivesSuspended = false;
        pushBackStreamChannel.resumeReads();
    }

    /**
     * Close the {@link WebSocketChannel}.
     */
    @Override
    public void close() throws IOException {
        channel.close();
    }

    /**
     * Returns a new {@link StreamSinkFrameChannel} for sending the given {@link WebSocketFrameType} with the given payload.
     * If this method is called multiple times, subsequent {@link StreamSinkFrameChannel}'s will not be writable until all previous frames
     * were completely written.
     *
     * @param type        The {@link WebSocketFrameType} for which a {@link StreamSinkChannel} should be created
     * @param payloadSize The size of the payload which will be included in the WebSocket Frame. This may be 0 if you want
     *                    to transmit no payload at all.
     */
    public StreamSinkFrameChannel send(WebSocketFrameType type, long payloadSize) {
        if (payloadSize < 0) {
            throw new IllegalArgumentException("The payloadSize must be >= 0");
        }
        StreamSinkFrameChannel ch = create(channel, type, payloadSize);
        boolean o = senders.offer(ch);
        assert o;

        if (isInUse(ch)) {
            // Channel is first in the queue so mark it as active
            ch.active();
        }
        return ch;
    }

    public void sendClose() throws IOException {
        StreamSinkFrameChannel closeChannel = create(channel, WebSocketFrameType.CLOSE, 0);
        closeChannel.close();
    }

    @Override
    public ChannelListener.Setter<? extends WebSocketChannel> getCloseSetter() {
        return closeSetter;
    }

    /**
     * Create a new {@link StreamSourceFrameChannel}  which can be used to read the data of the received WebSocket Frame
     *
     * @param streamSourceChannelControl@return
     *         channel                  A {@link StreamSourceFrameChannel} will be used to read a Frame from.
     *         This will return <code>null</code> if the right {@link StreamSourceFrameChannel} could not be detected with the given
     *         buffer and so more data is needed.
     * @throws WebSocketException Is thrown if something goes wrong
     */
    protected abstract PartialFrame receiveFrame(final StreamSourceChannelControl streamSourceChannelControl);

    /**
     * Create a new StreamSinkFrameChannel which can be used to send a WebSocket Frame of the type {@link WebSocketFrameType}.
     *
     * @param channel     The {@link StreamSinkChannel} to wrap
     * @param type        The {@link WebSocketFrameType} of the WebSocketFrame which will be send over this {@link StreamSinkFrameChannel}
     * @param payloadSize The size of the payload to transmit. May be 0 if non payload at all should be included.
     */
    protected abstract StreamSinkFrameChannel create(StreamSinkChannel channel, WebSocketFrameType type, long payloadSize);

    /**
     * Mark the given {@link StreamSinkFrameChannel} as complete and so remove the obtained ones. Calling this method will also
     * take care of call {@link StreamSinkFrameChannel#active()} on the new active {@link StreamSinkFrameChannel}.
     */
    protected final void complete(StreamSinkFrameChannel channel) {
        if (senders.peek() == channel) {
            if (senders.remove(channel)) {
                StreamSinkFrameChannel ch = senders.peek();
                ch.active();
            }
        }
    }

    /**
     * {@link ChannelListener} which delegates the read notification to the appropriate listener
     */
    private final class WebSocketReadListener implements ChannelListener<PushBackStreamChannel> {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public void handleEvent(final PushBackStreamChannel channel) {
            final StreamSourceFrameChannel receiver = WebSocketChannel.this.receiver;
            if (receiver != null) {
                final ChannelListener listener = ((SimpleSetter) receiver.getReadSetter()).get();
                if (listener != null) {
                    ChannelListeners.invokeChannelListener(receiver, listener);
                } else {
                    channel.suspendReads();
                }
            } else {
                final ChannelListener listener = receiveSetter.get();
                if (listener != null) {
                    ChannelListeners.invokeChannelListener(WebSocketChannel.this, listener);
                } else {
                    channel.suspendReads();
                }
            }
        }
    }


    private class WebSocketWriteListener implements ChannelListener<ConnectedStreamChannel> {
        @Override
        public void handleEvent(final ConnectedStreamChannel channel) {
            StreamSinkFrameChannel ch = senders.peek();
            if (ch != null) {
                ChannelListeners.invokeChannelListener(ch, ch.writeSetter.get());
            }
        }
    }


    /**
     * Interface that represenets a channel that is in the process of being created
     */
    public interface PartialFrame {

        /**
         * @return The channel, or null if the channel is not availble yet
         */
        StreamSourceFrameChannel getChannel();

        /**
         * Handles the data, any remaining data will be pushed back
         *
         * @param data
         */
        void handle(ByteBuffer data, final PushBackStreamChannel channel) throws WebSocketException;

        /**
         * @return true if the channel is available
         */
        boolean isDone();
    }

    public class StreamSourceChannelControl {

        private StreamSourceChannelControl() {

        }

        public void readFrameDone(StreamSourceFrameChannel channel) {
            synchronized (WebSocketChannel.this) {
                if (channel == receiver) {
                    receiver = null;
                    if (receivesSuspended) {
                        pushBackStreamChannel.suspendReads();
                    } else {
                        pushBackStreamChannel.resumeReads();
                    }
                }

            }
        }
    }


}
