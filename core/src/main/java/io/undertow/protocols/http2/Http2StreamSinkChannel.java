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

package io.undertow.protocols.http2;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.protocol.framed.SendFrameHeader;
import org.xnio.IoUtils;

/**
 * Stream sink channel used for HTTP2 communication.
 *
 * @author Stuart Douglas
 * @author Flavia Rainone
 */
public abstract class Http2StreamSinkChannel extends AbstractHttp2StreamSinkChannel {

    private final int streamId;
    private volatile boolean reset = false;

    //flow control related items. Accessed under lock
    private int flowControlWindow;
    private int initialWindowSize; //we track the initial window size, and then re-query it to get any delta

    private SendFrameHeader header;

    private final Object flowControlLock = new Object();

    Http2StreamSinkChannel(Http2Channel channel, int streamId) {
        super(channel);
        this.streamId = streamId;
        this.flowControlWindow = channel.getInitialSendWindowSize();
        this.initialWindowSize = this.flowControlWindow;
    }

    public int getStreamId() {
        return streamId;
    }

    SendFrameHeader generateSendFrameHeader() {
        header = createFrameHeaderImpl();
        return header;
    }

    protected abstract SendFrameHeader createFrameHeaderImpl();

    void clearHeader() {
        this.header = null;
    }

    @Override
    protected void channelForciblyClosed() throws IOException {
        getChannel().removeStreamSink(getStreamId());
        if (reset) {
            return;
        }
        reset = true;
        if (streamId % 2 == (getChannel().isClient() ? 1 : 0)) {
            //we initiated the stream
            //we only actually reset if we have sent something to the other endpoint
            if (isFirstDataWritten() && !getChannel().isThisGoneAway()) {
                getChannel().sendRstStream(streamId, Http2Channel.ERROR_CANCEL);
            }
        } else if(!getChannel().isThisGoneAway()) {
            getChannel().sendRstStream(streamId, Http2Channel.ERROR_STREAM_CLOSED);
        }
        markBroken();
    }

    @Override
    protected final SendFrameHeader createFrameHeader() {
        SendFrameHeader header = this.header;
        this.header = null;
        return header;
    }

    @Override
    protected void handleFlushComplete(boolean channelClosed) {
        if (channelClosed) {
            getChannel().removeStreamSink(getStreamId());
        }
        if(reset) {
            IoUtils.safeClose(this);
        }
    }

    /**
     * This method should be called before sending. It will return the amount of
     * data that can be sent, taking into account the stream and connection flow
     * control windows, and the toSend parameter.
     * <p>
     * It will decrement the flow control windows by the amount that can be sent,
     * so this method should only be called as a frame is being queued.
     *
     * @return The number of bytes that can be sent
     */
    protected int grabFlowControlBytes(int toSend) {
        synchronized (flowControlLock) {
            if (toSend == 0) {
                return 0;
            }
            int newWindowSize = this.getChannel().getInitialSendWindowSize();
            int settingsDelta = newWindowSize - this.initialWindowSize;
            //first adjust for any settings frame updates
            if (settingsDelta != 0) {
                this.initialWindowSize = newWindowSize;
                this.flowControlWindow += settingsDelta;
                flowControlLock.notifyAll();
            }

            int min = Math.min(toSend, this.flowControlWindow);
            int actualBytes = this.getChannel().grabFlowControlBytes(min);
            this.flowControlWindow -= actualBytes;
            return actualBytes;
        }
    }

    void updateFlowControlWindow(final int delta) throws IOException {
        boolean exhausted;
        synchronized (flowControlLock) {
            exhausted = flowControlWindow <= 0;
            long ld = delta;
            ld += flowControlWindow;
            if (ld > Integer.MAX_VALUE) {
                getChannel().sendRstStream(streamId, Http2Channel.ERROR_FLOW_CONTROL_ERROR);
                markBroken();
                return;
            }
            flowControlWindow += delta;
            flowControlLock.notifyAll();
        }
        if (exhausted) {
            getChannel().notifyFlowControlAllowed();
            if (isWriteResumed()) {
                resumeWritesInternal(true);
            }
        }
    }


    protected PooledByteBuffer[] allocateAll(PooledByteBuffer[] allHeaderBuffers, PooledByteBuffer currentBuffer) {
        PooledByteBuffer[] ret;
        if (allHeaderBuffers == null) {
            ret = new PooledByteBuffer[2];
            ret[0] = currentBuffer;
            ret[1] = getChannel().getBufferPool().allocate();
            ByteBuffer newBuffer = ret[1].getBuffer();
            if(newBuffer.remaining() > getChannel().getSendMaxFrameSize()) {
                newBuffer.limit(newBuffer.position() + getChannel().getSendMaxFrameSize()); //make sure the buffers are not too large to go over the max frame size
            }
        } else {
            ret = new PooledByteBuffer[allHeaderBuffers.length + 1];
            System.arraycopy(allHeaderBuffers, 0, ret, 0, allHeaderBuffers.length);
            ret[ret.length - 1] = getChannel().getBufferPool().allocate();
            ByteBuffer newBuffer = ret[ret.length - 1].getBuffer();
            if(newBuffer.remaining() > getChannel().getSendMaxFrameSize()) {
                newBuffer.limit(newBuffer.position() + getChannel().getSendMaxFrameSize());
            }
        }
        return ret;
    }

    /**
     * Method that is invoked when the stream is reset.
     */
    void rstStream() {
        if (reset) {
            return;
        }
        reset = true;
        if(!isReadyForFlush()) {
            IoUtils.safeClose(this);
        }
        getChannel().removeStreamSink(getStreamId());
    }
}
