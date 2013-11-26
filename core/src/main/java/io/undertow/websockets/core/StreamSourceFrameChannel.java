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

import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Base class for processes Frame bases StreamSourceChannels.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public abstract class StreamSourceFrameChannel extends AbstractFramedStreamSourceChannel<WebSocketChannel, StreamSourceFrameChannel, StreamSinkFrameChannel> {

    protected final WebSocketFrameType type;

    private boolean finalFragment;
    private final int rsv;
    private final long payloadSize;

    protected StreamSourceFrameChannel(WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize, Pooled<ByteBuffer> pooled, long frameLength) {
        this(wsChannel, type, payloadSize, 0, true, pooled, frameLength);
    }

    protected StreamSourceFrameChannel(WebSocketChannel wsChannel, WebSocketFrameType type, long payloadSize, int rsv, boolean finalFragment, Pooled<ByteBuffer> pooled, long frameLength) {
        super(wsChannel, pooled, frameLength);
        this.type = type;
        this.finalFragment = finalFragment;
        this.rsv = rsv;
        this.payloadSize = payloadSize;
    }

    /**
     * Return the {@link WebSocketFrameType} or {@code null} if its not known at the calling time.
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
     */
    public int getRsv() {
        return rsv;
    }

    int getWebSocketFrameCount() {
        return getReadFrameCount();
    }

    /**
     * Discard the frame, which means all data that would be part of the frame will be discarded.
     * <p/>
     * Once all is discarded it will call {@link #close()}
     */
    public void discard() throws IOException {
        if (isOpen()) {
            ChannelListener<StreamSourceChannel> drainListener = ChannelListeners.drainListener(Long.MAX_VALUE,
                    new ChannelListener<StreamSourceChannel>() {
                        @Override
                        public void handleEvent(StreamSourceChannel channel) {
                            IoUtils.safeClose(StreamSourceFrameChannel.this);
                        }
                    }, new ChannelExceptionHandler<StreamSourceChannel>() {
                        @Override
                        public void handleException(StreamSourceChannel channel, IOException exception) {
                            getFramedChannel().markReadsBroken(exception);
                        }
                    }
            );
            getReadSetter().set(drainListener);
            resumeReads();
        } else {
            close();
        }
    }

    @Override
    protected WebSocketChannel getFramedChannel() {
        return (WebSocketChannel) super.getFramedChannel();
    }

    public WebSocketChannel getWebSocketChannel() {
        return getFramedChannel();
    }

    public void finalFrame() {
        this.lastFrame();
        this.finalFragment = true;
    }


}
