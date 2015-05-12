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

import io.undertow.server.protocol.framed.AbstractFramedStreamSinkChannel;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class StreamSinkFrameChannel extends AbstractFramedStreamSinkChannel<WebSocketChannel, StreamSourceFrameChannel, StreamSinkFrameChannel> {

    private final WebSocketFrameType type;

    private int rsv;

    protected StreamSinkFrameChannel(WebSocketChannel channel, WebSocketFrameType type) {
        super(channel);
        this.type = type;
    }

    /**
     * Return the RSV for the extension. Default is 0.
     */
    public int getRsv() {
        return rsv;
    }

    /**
     * Set the RSV which is used for extensions.
     * <p>
     * This can only be set before any write or transfer operations where passed
     * to the wrapped {@link org.xnio.channels.StreamSinkChannel}, after that an {@link IllegalStateException} will be thrown.
     *
     */
    public void setRsv(int rsv) {
        if (!areExtensionsSupported() && rsv != 0) {
            throw WebSocketMessages.MESSAGES.extensionsNotSupported();
        }
        this.rsv = rsv;
    }

    /**
     * {@code true} if fragmentation is supported for the {@link WebSocketFrameType}.
     */
    public boolean isFragmentationSupported() {
        return false;
    }

    /**
     * {@code true} if extensions are supported for the {@link WebSocketFrameType}.
     */
    public boolean areExtensionsSupported() {
        return false;
    }

    /**
     * Return the {@link WebSocketFrameType} for which the {@link StreamSinkFrameChannel} was obtained.
     */
    public WebSocketFrameType getType() {
        return type;
    }

    public WebSocketChannel getWebSocketChannel() {
        return getChannel();
    }

    @Override
    protected boolean isLastFrame() {
        return type == WebSocketFrameType.CLOSE;
    }

    public boolean isFinalFragment() {
        return super.isFinalFrameQueued();
    }
}
