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

package io.undertow.spdy;

import io.undertow.server.protocol.framed.SendFrameHeader;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
public abstract class SpdyStreamStreamSinkChannel extends SpdyStreamSinkChannel {

    private final int streamId;

    //flow control related items. Accessed under lock
    private int flowControlWindow;
    private int initialWindowSize; //we track the initial window size, and then re-query it to get any delta

    private SendFrameHeader header;

    SpdyStreamStreamSinkChannel(SpdyChannel channel, int streamId) {
        super(channel);
        this.streamId = streamId;
        this.flowControlWindow = channel.getInitialWindowSize();
        this.initialWindowSize = this.flowControlWindow;
    }

    public int getStreamId() {
        return streamId;
    }

    SendFrameHeader generateSendFrameHeader() {
        header = createFrameHeaderImpl();
        return header;
    }

    void clearHeader() {
        this.header = null;
    }

    @Override
    protected final SendFrameHeader createFrameHeader() {
        SendFrameHeader header = this.header;
        this.header = null;
        return header;
    }

    protected abstract SendFrameHeader createFrameHeaderImpl();

    /**
     * This method should be called before sending. It will return the amount of
     * data that can be sent, taking into account the stream and connection flow
     * control windows, and the toSend parameter.
     *
     * It will decrement the flow control windows by the amount that can be sent,
     * so this method should only be called as a frame is being queued.
     *
     * @return The number of bytes that can be sent
     */
    protected synchronized int grabFlowControlBytes(int toSend) {
        int newWindowSize = this.getChannel().getInitialWindowSize();
        int settingsDelta = newWindowSize - this.initialWindowSize;
        //first adjust for any settings frame updates
        this.initialWindowSize = newWindowSize;
        this.flowControlWindow += settingsDelta;

        int min = Math.min(toSend, this.flowControlWindow);
        int actualBytes = this.getChannel().grabFlowControlBytes(min);
        this.flowControlWindow -= actualBytes;
        return actualBytes;
    }

    synchronized void updateFlowControlWindow(final int delta) throws IOException {
        boolean exhausted = flowControlWindow == 0;
        flowControlWindow += delta;
        if(exhausted) {
            getChannel().notifyFlowControlAllowed();
        }
    }
}
