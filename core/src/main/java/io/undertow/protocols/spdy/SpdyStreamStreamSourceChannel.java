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

package io.undertow.protocols.spdy;

import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public class SpdyStreamStreamSourceChannel extends SpdyStreamSourceChannel {


    private boolean rst = false;
    private final HeaderMap headers;
    private final int streamId;
    private HeaderMap newHeaders = null;
    private int flowControlWindow;
    private ChannelListener<SpdyStreamStreamSourceChannel> completionListener;

    SpdyStreamStreamSourceChannel(SpdyChannel framedChannel, PooledByteBuffer data, long frameDataRemaining, HeaderMap headers, int streamId) {
        super(framedChannel, data, frameDataRemaining);
        this.headers = headers;
        this.streamId = streamId;
        this.flowControlWindow = framedChannel.getInitialWindowSize();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        handleNewHeaders();
        int read = super.read(dst);
        updateFlowControlWindow(read);
        return read;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        handleNewHeaders();
        long read = super.read(dsts, offset, length);
        updateFlowControlWindow((int) read);
        return read;
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        handleNewHeaders();
        long read = super.read(dsts);
        updateFlowControlWindow((int) read);
        return read;
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel streamSinkChannel) throws IOException {
        handleNewHeaders();
        long read = super.transferTo(count, throughBuffer, streamSinkChannel);
        updateFlowControlWindow((int) read + throughBuffer.remaining());
        return read;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        handleNewHeaders();
        long read = super.transferTo(position, count, target);
        updateFlowControlWindow((int) read);
        return read;
    }
    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        super.handleHeaderData(headerData);

        SpdyChannel.SpdyFrameParser data = (SpdyChannel.SpdyFrameParser) headerData;
        if(data.parser instanceof SpdyHeadersParser) {
            addNewHeaders(((SpdyHeadersParser)data.parser).getHeaderMap());
        }
    }

    /**
     * Merge any new headers from HEADERS blocks into the exchange.
     */
    private synchronized void handleNewHeaders() {
        if (newHeaders != null) {
            for (HeaderValues header : newHeaders) {
                headers.addAll(header.getHeaderName(), header);
            }
            newHeaders = null;
        }
    }

    synchronized void addNewHeaders(HeaderMap headers) {
        if (newHeaders != null) {
            newHeaders = headers;
        } else {
            for (HeaderValues header : headers) {
                newHeaders.addAll(header.getHeaderName(), header);
            }
        }
    }

    private void updateFlowControlWindow(final int read) {
        if(read <= 0) {
            return;
        }
        flowControlWindow -= read;
        //TODO: RST stream if flow control limits are exceeded?
        //TODO: make this configurable, we should be able to set the policy that is used to determine when to update the window size
        SpdyChannel spdyChannel = getSpdyChannel();
        spdyChannel.updateReceiveFlowControlWindow(read);
        int initialWindowSize = spdyChannel.getInitialWindowSize();
        if(flowControlWindow < (initialWindowSize / 2)) {
            int delta = initialWindowSize - flowControlWindow;
            flowControlWindow += delta;
            spdyChannel.sendUpdateWindowSize(streamId, delta);
        }
    }

    @Override
    protected void complete() throws IOException {
        super.complete();
        if(completionListener != null) {
            ChannelListeners.invokeChannelListener(this, completionListener);
        }
    }

    public HeaderMap getHeaders() {
        return headers;
    }

    public ChannelListener<SpdyStreamStreamSourceChannel> getCompletionListener() {
        return completionListener;
    }

    public void setCompletionListener(ChannelListener<SpdyStreamStreamSourceChannel> completionListener) {
        this.completionListener = completionListener;
    }

    @Override
    void rstStream() {
        if(rst) {
            return;
        }
        rst = true;
        markStreamBroken();
        getSpdyChannel().sendRstStream(streamId, SpdyChannel.RST_STATUS_CANCEL);
    }

    @Override
    protected void channelForciblyClosed() {
        if(completionListener != null) {
            completionListener.handleEvent(this);
        }
        rstStream();
    }

    public int getStreamId() {
        return streamId;
    }
}
