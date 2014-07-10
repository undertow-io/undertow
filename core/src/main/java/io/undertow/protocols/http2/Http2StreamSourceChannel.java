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
import java.nio.channels.FileChannel;
import org.xnio.Bits;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;

/**
 * @author Stuart Douglas
 */
public class Http2StreamSourceChannel extends AbstractHttp2StreamSourceChannel {

    /**
     * Flag that is set if the headers frame has the end stream flag set, but not end headers
     * which means the last continuation frame is the end of the stream.
     */
    private boolean headersEndStream = false;
    private boolean rst = false;
    private final HeaderMap headers;
    private final int streamId;
    private HeaderMap newHeaders = null;
    private Http2HeadersStreamSinkChannel response;
    private int flowControlWindow;
    private ChannelListener<Http2StreamSourceChannel> completionListener;

    Http2StreamSourceChannel(Http2Channel framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining, HeaderMap headers, int streamId) {
        super(framedChannel, data, frameDataRemaining);
        this.headers = headers;
        this.streamId = streamId;
        this.flowControlWindow = framedChannel.getInitialReceiveWindowSize();
    }

    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        handleFinalFrame((Http2FrameHeaderParser) headerData);
    }

    void handleFinalFrame(Http2FrameHeaderParser headerData) {
        Http2FrameHeaderParser data = headerData;
        if (data.type == Http2Channel.FRAME_TYPE_DATA) {
            if (Bits.anyAreSet(data.flags, Http2Channel.DATA_FLAG_END_STREAM)) {
                this.lastFrame();
            }
        } else if (data.type == Http2Channel.FRAME_TYPE_HEADERS) {
            if (Bits.allAreSet(data.flags, Http2Channel.HEADERS_FLAG_END_STREAM)) {
                if (Bits.allAreSet(data.flags, Http2Channel.HEADERS_FLAG_END_HEADERS)) {
                    this.lastFrame();
                } else {
                    //continuation frames are coming, then we end the stream
                    headersEndStream = true;
                }
            }
        } else if (headersEndStream && data.type == Http2Channel.FRAME_TYPE_CONTINUATION) {
            if (Bits.anyAreSet(data.flags, Http2Channel.CONTINUATION_FLAG_END_HEADERS)) {
                this.lastFrame();
            }
        }
    }

    public Http2HeadersStreamSinkChannel getResponseChannel() {
        if (response != null) {
            return response;
        }
        response = new Http2HeadersStreamSinkChannel(getHttp2Channel(), streamId);
        getHttp2Channel().registerStreamSink(response);
        return response;
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
        updateFlowControlWindow((int) read);
        return read;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        handleNewHeaders();
        long read = super.transferTo(position, count, target);
        updateFlowControlWindow((int) read);
        return read;
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
        if (read <= 0) {
            return;
        }
        flowControlWindow -= read;
        //TODO: RST stream if flow control limits are exceeded?
        //TODO: make this configurable, we should be able to set the policy that is used to determine when to update the window size
        Http2Channel spdyChannel = getHttp2Channel();
        spdyChannel.updateReceiveFlowControlWindow(read);
        int initialWindowSize = spdyChannel.getInitialReceiveWindowSize();
        if (flowControlWindow < (initialWindowSize / 2)) {
            int delta = initialWindowSize - flowControlWindow;
            flowControlWindow += delta;
            spdyChannel.sendUpdateWindowSize(streamId, delta);
        }
    }

    @Override
    protected void complete() throws IOException {
        super.complete();
        if (completionListener != null) {
            ChannelListeners.invokeChannelListener(this, completionListener);
        }
    }

    public HeaderMap getHeaders() {
        return headers;
    }

    public ChannelListener<Http2StreamSourceChannel> getCompletionListener() {
        return completionListener;
    }

    public void setCompletionListener(ChannelListener<Http2StreamSourceChannel> completionListener) {
        this.completionListener = completionListener;
    }

    @Override
    void rstStream() {
        if (rst) {
            return;
        }
        rst = true;
        markStreamBroken();
        getHttp2Channel().sendRstStream(streamId, Http2Channel.ERROR_CANCEL);
    }

    @Override
    protected void channelForciblyClosed() {
        if (completionListener != null) {
            completionListener.handleEvent(this);
        }
        rstStream();
    }

    public int getStreamId() {
        return streamId;
    }
}
