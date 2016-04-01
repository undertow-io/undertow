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

package io.undertow.protocols.ajp;

import java.io.IOException;
import org.xnio.ChannelListener;
import io.undertow.connector.PooledByteBuffer;

import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.util.HeaderMap;

/**
 * @author Stuart Douglas
 */
public class AjpClientResponseStreamSourceChannel extends AbstractAjpClientStreamSourceChannel {

    private ChannelListener<AjpClientResponseStreamSourceChannel> finishListener;

    private final HeaderMap headers;
    private final int statusCode;
    private final String reasonPhrase;

    public AjpClientResponseStreamSourceChannel(AjpClientChannel framedChannel, HeaderMap headers, int statusCode, String reasonPhrase, PooledByteBuffer frameData, int remaining) {
        super(framedChannel, frameData, remaining);
        this.headers = headers;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    public HeaderMap getHeaders() {
        return headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public void setFinishListener(ChannelListener<AjpClientResponseStreamSourceChannel> finishListener) {
        this.finishListener = finishListener;
    }

    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        if(headerData instanceof AjpClientChannel.EndResponse) {
            lastFrame();
        }
    }
    @Override
    protected long updateFrameDataRemaining(PooledByteBuffer frameData, long frameDataRemaining) {
        if(frameDataRemaining > 0  && frameData.getBuffer().remaining() == frameDataRemaining) {
            //there is a null terminator on the end
            frameData.getBuffer().limit(frameData.getBuffer().limit() - 1);
            return frameDataRemaining - 1;
        }
        return frameDataRemaining;
    }

    @Override
    protected void complete() throws IOException {
        if(finishListener != null) {
            getFramedChannel().sourceDone();
            finishListener.handleEvent(this);
        }
    }

    @Override
    public void wakeupReads() {
        super.wakeupReads();
        getFramedChannel().resumeReceives();
    }

    @Override
    public void resumeReads() {
        super.resumeReads();
        getFramedChannel().resumeReceives();
    }

    @Override
    public void suspendReads() {
        getFramedChannel().suspendReceives();
        super.suspendReads();
    }
}
