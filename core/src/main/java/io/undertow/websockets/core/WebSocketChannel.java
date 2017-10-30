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
package io.undertow.websockets.core;

import io.undertow.conduits.IdleTimeoutConduit;
import io.undertow.server.protocol.framed.AbstractFramedChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.websockets.extensions.ExtensionFunction;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link org.xnio.channels.ConnectedChannel} which can be used to send and receive WebSocket Frames.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author Stuart Douglas
 */
public abstract class WebSocketChannel extends AbstractFramedChannel<WebSocketChannel, StreamSourceFrameChannel, StreamSinkFrameChannel> {

    private final boolean client;

    private final WebSocketVersion version;
    private final String wsUrl;

    private volatile boolean closeFrameReceived;
    private volatile boolean closeFrameSent;
    /**
     * If this is true then the web socket close was initiated by the remote peer
     */
    private volatile boolean closeInitiatedByRemotePeer;
    private volatile int closeCode = -1;
    private volatile String closeReason;
    private final String subProtocol;
    protected final boolean extensionsSupported;
    protected final ExtensionFunction extensionFunction;
    protected final boolean hasReservedOpCode;

    /**
     * an incoming frame that has not been created yet
     */
    private volatile PartialFrame partialFrame;

    private final Map<String, Object> attributes = Collections.synchronizedMap(new HashMap<String, Object>());

    protected StreamSourceFrameChannel fragmentedChannel;

    /**
     * Represents all web socket channels that are attached to the same endpoint.
     */
    private final Set<WebSocketChannel> peerConnections;

    /**
     * Create a new {@link WebSocketChannel}
     * 8
     *
     * @param connectedStreamChannel The {@link org.xnio.channels.ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                               Be aware that it already must be "upgraded".
     * @param bufferPool             The {@link org.xnio.Pool} which will be used to acquire {@link java.nio.ByteBuffer}'s from.
     * @param version                The {@link WebSocketVersion} of the {@link WebSocketChannel}
     * @param wsUrl                  The url for which the channel was created.
     * @param client
     * @param peerConnections        The concurrent set that is used to track open connections associtated with an endpoint
     */
    protected WebSocketChannel(final StreamConnection connectedStreamChannel, ByteBufferPool bufferPool, WebSocketVersion version, String wsUrl, String subProtocol, final boolean client, boolean extensionsSupported, final ExtensionFunction extensionFunction, Set<WebSocketChannel> peerConnections, OptionMap options) {
        super(connectedStreamChannel, bufferPool, new WebSocketFramePriority(), null, options);
        this.client = client;
        this.version = version;
        this.wsUrl = wsUrl;
        this.extensionsSupported = extensionsSupported;
        this.extensionFunction = extensionFunction;
        this.hasReservedOpCode = extensionFunction.hasExtensionOpCode();
        this.subProtocol = subProtocol;
        this.peerConnections = peerConnections;
        addCloseTask(new ChannelListener<WebSocketChannel>() {
            @Override
            public void handleEvent(WebSocketChannel channel) {
                extensionFunction.dispose();
                WebSocketChannel.this.peerConnections.remove(WebSocketChannel.this);
            }
        });
    }

    @Override
    protected IdleTimeoutConduit createIdleTimeoutChannel(final StreamConnection connectedStreamChannel) {
        return new IdleTimeoutConduit(connectedStreamChannel) {
            @Override
            protected void doClose() {
                WebSockets.sendClose(CloseMessage.GOING_AWAY, null, WebSocketChannel.this, null);
            }
        };
    }

    @Override
    protected boolean isLastFrameSent() {
        return closeFrameSent;
    }

    @Override
    protected boolean isLastFrameReceived() {
        return closeFrameReceived;
    }

    @Override
    protected void markReadsBroken(Throwable cause) {
        super.markReadsBroken(cause);
    }

    @Override
    protected void lastDataRead() {
        if(!closeFrameReceived && !closeFrameSent) {
            //the peer has likely already gone away, but try and send a close frame anyway
            //this will likely just result in the write() failing an immediate connection termination
            //which is what we want
            closeFrameReceived = true; //not strictly true, but the read side is gone
            try {
                sendClose();
            } catch (IOException e) {
                IoUtils.safeClose(this);
            }
        }
    }

    protected boolean isReadsBroken() {
        return super.isReadsBroken();
    }

    @Override
    protected FrameHeaderData parseFrame(ByteBuffer data) throws IOException {
        if (partialFrame == null) {
            partialFrame = receiveFrame();
        }
        try {
            partialFrame.handle(data);
        } catch (WebSocketException e) {
            //the data was corrupt
            //send a close message
            WebSockets.sendClose(new CloseMessage(CloseMessage.WRONG_CODE, e.getMessage()).toByteBuffer(), this, null);
            markReadsBroken(e);
            if (WebSocketLogger.REQUEST_LOGGER.isDebugEnabled()) {
                WebSocketLogger.REQUEST_LOGGER.debugf(e, "receive failed due to Exception");
            }

            throw new IOException(e);
        }
        if (partialFrame.isDone()) {
            PartialFrame p = this.partialFrame;
            this.partialFrame = null;
            return p;
        }
        return null;
    }


    /**
     * Create a new {@link io.undertow.websockets.core.StreamSourceFrameChannel}  which can be used to read the data of the received Frame
     *
     * @return channel                  A {@link io.undertow.websockets.core.StreamSourceFrameChannel} will be used to read a Frame from.
     *         This will return {@code null} if the right {@link io.undertow.websockets.core.StreamSourceFrameChannel} could not be detected with the given
     *         buffer and so more data is needed.
     */
    protected abstract PartialFrame receiveFrame();

    @Override
    protected StreamSourceFrameChannel createChannel(FrameHeaderData frameHeaderData, PooledByteBuffer frameData) {
        PartialFrame partialFrame = (PartialFrame) frameHeaderData;
        StreamSourceFrameChannel channel = partialFrame.getChannel(frameData);
        if (channel.getType() == WebSocketFrameType.CLOSE) {
            if(!closeFrameSent) {
                closeInitiatedByRemotePeer = true;
            }
            closeFrameReceived = true;
        }
        return channel;
    }

    public final boolean setAttribute(String key, Object value) {
        if (value == null) {
            return attributes.remove(key) != null;
        } else {
            return attributes.put(key, value) == null;
        }
    }

    public final Object getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Returns {@code true} if extensions are supported by this WebSocket Channel.
     */
    public boolean areExtensionsSupported() {
        return extensionsSupported;
    }

    @Override
    protected void handleBrokenSourceChannel(Throwable e) {
        if (e instanceof UnsupportedEncodingException) {
            getFramePriority().immediateCloseFrame();
            WebSockets.sendClose(new CloseMessage(CloseMessage.MSG_CONTAINS_INVALID_DATA, e.getMessage()).toByteBuffer(), this, null);
        } else if (e instanceof WebSocketInvalidCloseCodeException) {
            WebSockets.sendClose(new CloseMessage(CloseMessage.WRONG_CODE, e.getMessage()).toByteBuffer(), this, null);
        } else if (e instanceof WebSocketFrameCorruptedException) {
            getFramePriority().immediateCloseFrame();
            WebSockets.sendClose(new CloseMessage(CloseMessage.WRONG_CODE, e.getMessage()).toByteBuffer(), this, null);
        }
    }

    @Override
    protected void handleBrokenSinkChannel(Throwable e) {

    }

    /**
     * Returns an unmodifiable {@link Set} of the selected subprotocols if any.
     */
    @Deprecated
    public Set<String> getSubProtocols() {
        return Collections.singleton(subProtocol);
    }

    public String getSubProtocol() {
        return subProtocol;
    }

    public boolean isCloseFrameReceived() {
        return closeFrameReceived;
    }

    public boolean isCloseFrameSent() {
        return closeFrameSent;
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
     * Return {@code true} if this is handled via WebSocket Secure.
     */
    public boolean isSecure() {
        return "wss".equals(getRequestScheme());
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

    public boolean isClient() {
        return client;
    }

    /**
     * Returns a new {@link StreamSinkFrameChannel} for sending the given {@link WebSocketFrameType} with the given payload.
     * If this method is called multiple times, subsequent {@link StreamSinkFrameChannel}'s will not be writable until all previous frames
     * were completely written.
     *
     * @param type        The {@link WebSocketFrameType} for which a {@link StreamSinkChannel} should be created
     */
    public final StreamSinkFrameChannel send(WebSocketFrameType type) throws IOException {
        if(closeFrameSent || (closeFrameReceived && type != WebSocketFrameType.CLOSE)) {
            throw WebSocketMessages.MESSAGES.channelClosed();
        }
        if (isWritesBroken()) {
            throw WebSocketMessages.MESSAGES.streamIsBroken();
        }


        StreamSinkFrameChannel ch = createStreamSinkChannel(type);
        getFramePriority().addToOrderQueue(ch);
        if (type == WebSocketFrameType.CLOSE) {
            closeFrameSent = true;
        }
        return ch;
    }

    /**
     * Send a Close frame without a payload
     */
    public void sendClose() throws IOException {
        closeReason = "";
        closeCode = CloseMessage.NORMAL_CLOSURE;
        StreamSinkFrameChannel closeChannel = send(WebSocketFrameType.CLOSE);
        closeChannel.shutdownWrites();
        if (!closeChannel.flush()) {
            closeChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                    null, new ChannelExceptionHandler<StreamSinkChannel>() {
                @Override
                public void handleException(final StreamSinkChannel channel, final IOException exception) {
                    IoUtils.safeClose(WebSocketChannel.this);
                }
            }
            ));
            closeChannel.resumeWrites();
        }
    }


    /**
     * Create a new StreamSinkFrameChannel which can be used to send a WebSocket Frame of the type {@link WebSocketFrameType}.
     *
     * @param type        The {@link WebSocketFrameType} of the WebSocketFrame which will be send over this {@link StreamSinkFrameChannel}
     */
    protected abstract StreamSinkFrameChannel createStreamSinkChannel(WebSocketFrameType type);


    protected WebSocketFramePriority getFramePriority() {
        return (WebSocketFramePriority) super.getFramePriority();
    }

    /**
     * Returns all 'peer' web socket connections that were created from the same endpoint.
     *
     *
     * @return all 'peer' web socket connections
     */
    public Set<WebSocketChannel> getPeerConnections() {
        return Collections.unmodifiableSet(peerConnections);
    }

    /**
     * If this is true the session is being closed because the remote peer sent a close frame
     * @return <code>true</code> if the remote peer closed the connection
     */
    public boolean isCloseInitiatedByRemotePeer() {
        return closeInitiatedByRemotePeer;
    }

    /**
     * Interface that represents a frame channel that is in the process of being created
     */
    public interface PartialFrame extends FrameHeaderData {

        /**
         * @return The channel, or null if the channel is not available yet
         */
        StreamSourceFrameChannel getChannel(final PooledByteBuffer data);

        /**
         * Handles the data, any remaining data will be pushed back
         */
        void handle(ByteBuffer data) throws WebSocketException;

        /**
         * @return true if the channel is available
         */
        boolean isDone();
    }

    /**
     *
     * @return The close reason
     */
    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    /**
     *
     * @return The close code
     */
    public int getCloseCode() {
        return closeCode;
    }

    public void setCloseCode(int closeCode) {
        this.closeCode = closeCode;
    }

    public ExtensionFunction getExtensionFunction() {
        return extensionFunction;
    }
}
