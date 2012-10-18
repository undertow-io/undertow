
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

import static org.xnio.IoUtils.safeClose;
import io.undertow.UndertowLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioWorker;
import org.xnio.ChannelListener.Setter;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * 
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public abstract class WebSocketChannel implements ConnectedChannel {

    final AtomicReference<StreamSourceFrameChannel> receiver = new AtomicReference<StreamSourceFrameChannel>();
    final ConcurrentLinkedQueue<StreamSinkFrameChannel> currentSender = new ConcurrentLinkedQueue<StreamSinkFrameChannel>();
    private final ConnectedStreamChannel channel;
    private final WebSocketVersion version;
    private final String wsUrl;
    private final Setter<WebSocketChannel> closeSetter;
    private final PushBackStreamChannel pushBackStreamChannel;
    private final Pool<ByteBuffer> bufferPool;

    public WebSocketChannel(final ConnectedStreamChannel channel, Pool<ByteBuffer> bufferPool, WebSocketVersion version, String wsUrl) {
        this.channel = channel;
        this.version = version;
        this.wsUrl = wsUrl;
        this.bufferPool = bufferPool;
        closeSetter = ChannelListeners.getDelegatingSetter(channel.getCloseSetter(), this);
        pushBackStreamChannel = new PushBackStreamChannel(channel);
        pushBackStreamChannel.getReadSetter().set(new WebSocketReadListener());
    }


    /**
     * Get the buffer pool for this connection.
     *
     * @return the buffer pool for this connection
     */
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }
    
    void remove(StreamSinkFrameChannel channel) throws IOException {
        if (currentSender.peek() == channel) {
            // TODO: I think thats not safe
            if (currentSender.remove(channel)) {
                channel.flush();
                StreamSinkFrameChannel ch = currentSender.peek();
                synchronized(ch.writeWaitLock) {
                    // notify threads that may wait because of  StreamSinkFrameChannel.await*()
                    ch.writeWaitLock.notify();
                }
                ChannelListeners.invokeChannelListener(ch, ch.closeSetter.get());
            }
        } else {
            currentSender.remove(channel);
        }
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
    public StreamSourceFrameChannel receive() {
        return receiver.getAndSet(null);
    }

    /**
     * Close the {@link WebSocketChannel} and also all {@link StreamSinkFrameChannel}'s and {@link StreamSourceFrameChannel} that was acquired
     */
    @Override
    public void close() throws IOException {
        IOException ex = null;
        StreamSourceFrameChannel channel = receiver.get();
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                ex = e;
            }
        }
        for (;;) {
            StreamSinkFrameChannel ch = currentSender.poll();
            if (ch == null) {
                break;
            }
            try {
                ch.close();
            } catch (IOException e) {
                if (ex == null) {
                    ex = e;
                }
            }
        }
        try {
            pushBackStreamChannel.close();
        } catch (IOException e) {
            if (ex == null) {
                ex = e;
            }
        }
        try {
            channel.close();
        } catch (IOException e) {
            if (ex == null) {
                ex = e;
            }
        }
        
        if (ex != null) {
            throw ex;
        }
    }
    
    /**
     * Returns a new {@link StreamSinkFrameChannel} for sending the given {@link WebSocketFrameType} with the given payload.
     * If this method is called multiple times, subsequent {@link StreamSinkFrameChannel}'s will not be writable until all previous frames
     * were completely written.
     * 
     * @param type              The {@link WebSocketFrameType} for which a {@link StreamSinkChannel} should be created
     * @param payloadSize       The size of the payload which will be included in the WebSocket Frame. This may be 0 if you want
     *                          to transmit no payload at all.
     */
    public StreamSinkFrameChannel send(WebSocketFrameType type, long payloadSize) {
        if (payloadSize < 0) {
            throw new IllegalArgumentException("The payloadSize must be >= 0");
        }
        StreamSinkFrameChannel ch = create(channel, type, payloadSize);
        boolean o = currentSender.offer(ch);
        assert o;
        return ch;
    }

    public void sendClose() throws IOException {
        StreamSinkFrameChannel closeChannel = create(channel, WebSocketFrameType.CLOSE, 0);
        closeChannel.close();
    }

    public ChannelListener.Setter<? extends WebSocketChannel> getReceiveSetter() {
        return null;
    }

    @Override
    public ChannelListener.Setter<? extends WebSocketChannel> getCloseSetter() {
        return closeSetter;
    }

    /**
     * Create a new {@link StreamSourceFrameChannel}  which can be used to read the data of the received WebSocket Frame
     * 
     * @param channel   The {@link PushBackStreamChannel} to wrap
     * @return ch       The {@link StreamSourceFrameChannel} which can be used to read data
     * @throws WebSocketException 
     */
    protected abstract StreamSourceFrameChannel create(Pooled<ByteBuffer> buffer, PushBackStreamChannel channel) throws WebSocketException;
    
    /**
     * Create a new StreamSinkFrameChannel which can be used to send a WebSocket Frame of the type {@link WebSocketFrameType}.
     * 
     * @param channel           The {@link StreamSinkChannel} to wrap
     * @param type              The {@link WebSocketFrameType} of the WebSocketFrame which will be send over this {@link StreamSinkFrameChannel}
     * @param payloadSize       The size of the payload to transmit. May be 0 if non payload at all should be included. 
     * @return ch               The {@link StreamSinkFrameChannel} that was created
     */
    protected abstract StreamSinkFrameChannel create(StreamSinkChannel channel, WebSocketFrameType type, long payloadSize);
    
    
    private final class WebSocketReadListener implements ChannelListener<PushBackStreamChannel> {
        public void handleEvent(final PushBackStreamChannel channel) {
            final Pooled<ByteBuffer> pooled = getBufferPool().allocate();
            final ByteBuffer buffer = pooled.getResource();
            boolean free = true;

            try {
                StreamSourceFrameChannel sourceChannel = null;
                int res;
                do {
                    buffer.clear();
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                            UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException");
                        }
                        safeClose(channel);
                        return;
                    }
                    if (res == 0) {
                        if(!channel.isReadResumed()) {
                            channel.getReadSetter().set(this);
                            channel.resumeReads();
                        }
                        return;
                    }
                    if (res == -1) {
                        try {
                            channel.shutdownReads();
                            
                        } catch (IOException e) {
                            if (UndertowLogger.REQUEST_LOGGER.isDebugEnabled()) {
                                UndertowLogger.REQUEST_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                            }
                            // fuck it, it's all ruined
                            IoUtils.safeClose(channel);
                            return;
                        }
                        return;
                    }
                    //TODO: we need to handle parse errors
                    buffer.flip();

                } while ((sourceChannel = create(pooled, pushBackStreamChannel)) == null);
                
                receiver.set(sourceChannel);
                // we remove ourselves as the read listener from the channel;
                // if the http handler doesn't set any then reads will suspend, which is the right thing to do
                channel.getReadSetter().set(null);
                channel.suspendReads();

                try {
                   

                } catch (Throwable t) {
                    UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
                    IoUtils.safeClose(channel);
                    IoUtils.safeClose(WebSocketChannel.this);
                }
            } catch (WebSocketException e) {
                UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
                IoUtils.safeClose(channel);
                IoUtils.safeClose(WebSocketChannel.this);
            } finally {
                if (free) pooled.free();
            }
        }
    }
}
