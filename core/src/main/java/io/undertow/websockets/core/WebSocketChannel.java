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
package io.undertow.websockets.core;

import io.undertow.server.protocol.framed.AbstractFramedChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
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

    private boolean closeFrameReceived;
    private boolean closeFrameSent;
    private final Set<String> subProtocols;
    private final boolean extensionsSupported;
    /**
     * an incoming frame that has not been created yet
     */
    private volatile PartialFrame partialFrame;

    private final Map<String, Object> attributes = Collections.synchronizedMap(new HashMap<String, Object>());

    protected StreamSourceFrameChannel fragmentedChannel;

    /**
     * Create a new {@link WebSocketChannel}
     * 8
     *
     * @param connectedStreamChannel The {@link org.xnio.channels.ConnectedStreamChannel} over which the WebSocket Frames should get send and received.
     *                               Be aware that it already must be "upgraded".
     * @param bufferPool             The {@link org.xnio.Pool} which will be used to acquire {@link java.nio.ByteBuffer}'s from.
     * @param version                The {@link io.undertow.websockets.core.WebSocketVersion} of the {@link io.undertow.websockets.core.WebSocketChannel}
     * @param wsUrl                  The url for which the channel was created.
     * @param client
     */
    protected WebSocketChannel(final StreamConnection connectedStreamChannel, Pool<ByteBuffer> bufferPool, WebSocketVersion version, String wsUrl, Set<String> subProtocols, final boolean client, boolean extensionsSupported) {
        super(connectedStreamChannel, bufferPool, new WebSocketFramePriority(), null);
        this.client = client;
        this.version = version;
        this.wsUrl = wsUrl;
        this.extensionsSupported = extensionsSupported;
        this.subProtocols = subProtocols;
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
            markReadsBroken(e);
            if (WebSocketLogger.REQUEST_LOGGER.isDebugEnabled()) {
                WebSocketLogger.REQUEST_LOGGER.debugf(e, "receive failed due to Exception");
            }
            //send a close message
            WebSockets.sendClose(new CloseMessage(CloseMessage.WRONG_CODE, e.getMessage()).toByteBuffer(), this, null);

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
    protected StreamSourceFrameChannel createChannel(FrameHeaderData frameHeaderData, Pooled<ByteBuffer> frameData) {
        PartialFrame partialFrame = (PartialFrame) frameHeaderData;
        StreamSourceFrameChannel channel = partialFrame.getChannel(frameData);
        if (channel.getType() == WebSocketFrameType.CLOSE) {
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
    public Set<String> getSubProtocols() {
        return subProtocols;
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
     * @param payloadSize The size of the payload which will be included in the WebSocket Frame. This may be 0 if you want
     *                    to transmit no payload at all.
     */
    public final StreamSinkFrameChannel send(WebSocketFrameType type, long payloadSize) throws IOException {
        if (payloadSize < 0) {
            throw WebSocketMessages.MESSAGES.negativePayloadLength();
        }
        if (isWritesBroken()) {
            throw WebSocketMessages.MESSAGES.streamIsBroken();
        }


        StreamSinkFrameChannel ch = createStreamSinkChannel(type, payloadSize);
        getFramePriority().addToOrderQueue(ch);
        if (type == WebSocketFrameType.CLOSE) {
            closeFrameSent = true;
        }
        return ch;
    }

    /**
     * Returns a new {@link StreamSinkFrameChannel} for sending the given {@link WebSocketFrameType} with the given payload.
     * If this method is called multiple times, subsequent {@link StreamSinkFrameChannel}'s will not be writable until all previous frames
     * were completely written.
     *
     * @param type The {@link WebSocketFrameType} for which a {@link StreamSinkChannel} should be created
     */
    public final StreamSinkFrameChannel send(WebSocketFrameType type) throws IOException {
        if (isWritesBroken()) {
            throw WebSocketMessages.MESSAGES.streamIsBroken();
        }
        StreamSinkFrameChannel ch = createStreamSinkChannel(type, -1);
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
        StreamSinkFrameChannel closeChannel = send(WebSocketFrameType.CLOSE, 0);
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
        }
    }


    /**
     * Create a new StreamSinkFrameChannel which can be used to send a WebSocket Frame of the type {@link WebSocketFrameType}.
     *
     * @param type        The {@link WebSocketFrameType} of the WebSocketFrame which will be send over this {@link StreamSinkFrameChannel}
     * @param payloadSize The size of the payload to transmit. May be 0 if non payload at all should be included, or -1 if unknown
     */
    protected abstract StreamSinkFrameChannel createStreamSinkChannel(WebSocketFrameType type, long payloadSize);


    protected WebSocketFramePriority getFramePriority() {
        return (WebSocketFramePriority) super.getFramePriority();
    }


    /**
     * Interface that represents a frame channel that is in the process of being created
     */
    public interface PartialFrame extends FrameHeaderData {

        /**
         * @return The channel, or null if the channel is not available yet
         */
        StreamSourceFrameChannel getChannel(final Pooled<ByteBuffer> data);

        /**
         * Handles the data, any remaining data will be pushed back
         */
        void handle(ByteBuffer data) throws WebSocketException;

        /**
         * @return true if the channel is available
         */
        boolean isDone();
    }
}
