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
import io.undertow.connector.PooledByteBuffer;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

import io.undertow.server.protocol.framed.FrameHeaderData;
import io.undertow.util.HeaderMap;

/**
 * @author Stuart Douglas
 */
public class Http2StreamSourceChannel extends AbstractHttp2StreamSourceChannel implements Http2Stream{

    /**
     * Flag that is set if the headers frame has the end stream flag set, but not end headers
     * which means the last continuation frame is the end of the stream.
     */
    private boolean headersEndStream = false;
    private boolean rst = false;
    private final HeaderMap headers;
    private final int streamId;
    private Http2HeadersStreamSinkChannel response;
    private int flowControlWindow;
    private ChannelListener<Http2StreamSourceChannel> completionListener;

    private int remainingPadding;

    /**
     * This is a bit of a hack, basically it allows the container to delay sending a RST_STREAM on a channel that is knows is broken,
     * because it wants to delay the RST until after the response has been set
     *
     * Used for handling the super nasty 100-continue logic
     */
    private boolean ignoreForceClose = false;

    Http2StreamSourceChannel(Http2Channel framedChannel, PooledByteBuffer data, long frameDataRemaining, HeaderMap headers, int streamId) {
        super(framedChannel, data, frameDataRemaining);
        this.headers = headers;
        this.streamId = streamId;
        this.flowControlWindow = framedChannel.getInitialReceiveWindowSize();
    }

    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        Http2FrameHeaderParser data = (Http2FrameHeaderParser) headerData;
        Http2PushBackParser parser = data.getParser();
        if(parser instanceof Http2DataFrameParser) {
            remainingPadding = ((Http2DataFrameParser) parser).getPadding();
        }
        handleFinalFrame(data);
    }

    @Override
    protected long updateFrameDataRemaining(PooledByteBuffer data, long frameDataRemaining) {
        long actualDataRemaining = frameDataRemaining - remainingPadding;
        if(data.getBuffer().remaining() > actualDataRemaining) {
            long paddingThisBuffer = data.getBuffer().remaining() - actualDataRemaining;
            data.getBuffer().limit((int) (data.getBuffer().position() + actualDataRemaining));
            remainingPadding -= paddingThisBuffer;
            try {
                updateFlowControlWindow((int) paddingThisBuffer);
            } catch (IOException e) {
                IoUtils.safeClose(getFramedChannel());
                throw new RuntimeException(e);
            }
            return frameDataRemaining - paddingThisBuffer;
        }
        return frameDataRemaining;
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
        int read = super.read(dst);
        updateFlowControlWindow(read);
        return read;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long read = super.read(dsts, offset, length);
        updateFlowControlWindow((int) read);
        return read;
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        long read = super.read(dsts);
        updateFlowControlWindow((int) read);
        return read;
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel streamSinkChannel) throws IOException {
        long read = super.transferTo(count, throughBuffer, streamSinkChannel);
        updateFlowControlWindow((int) read + throughBuffer.remaining());
        return read;
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        long read = super.transferTo(position, count, target);
        updateFlowControlWindow((int) read);
        return read;
    }

    private void updateFlowControlWindow(final int read) throws IOException {
        if (read <= 0) {
            return;
        }
        flowControlWindow -= read;
        //TODO: RST stream if flow control limits are exceeded?
        //TODO: make this configurable, we should be able to set the policy that is used to determine when to update the window size
        Http2Channel http2Channel = getHttp2Channel();
        http2Channel.updateReceiveFlowControlWindow(read);
        int initialWindowSize = http2Channel.getInitialReceiveWindowSize();
        //TODO: this is not great, as we may have already received all the data so there is no need, need to have a way to figure out if all data is buffered
        if (flowControlWindow < (initialWindowSize / 2)) {
            int delta = initialWindowSize - flowControlWindow;
            flowControlWindow += delta;
            http2Channel.sendUpdateWindowSize(streamId, delta);
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
        if(isComplete()) {
            ChannelListeners.invokeChannelListener(this, completionListener);
        }
    }

    @Override
    void rstStream(int error) {
        if (rst) {
            return;
        }
        rst = true;
        markStreamBroken();
    }

    @Override
    protected void channelForciblyClosed() {
        if (completionListener != null) {
            completionListener.handleEvent(this);
        }
        if(!ignoreForceClose) {
            getHttp2Channel().sendRstStream(streamId, Http2Channel.ERROR_CANCEL);
        }
        markStreamBroken();
    }

    public void setIgnoreForceClose(boolean ignoreForceClose) {
        this.ignoreForceClose = ignoreForceClose;
    }

    public boolean isIgnoreForceClose() {
        return ignoreForceClose;
    }

    public int getStreamId() {
        return streamId;
    }

    boolean isHeadersEndStream() {
        return headersEndStream;
    }

    @Override
    public String toString() {
        return "Http2StreamSourceChannel{" +
                "headers=" + headers +
                '}';
    }
}
