package io.undertow.spdy;

import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Stuart Douglas
 */
public class SpdySynReplyStreamSourceChannel extends SpdyStreamSourceChannel {

    private final HeaderMap headers;
    private final int streamId;
    private HeaderMap newHeaders = null;
    private int flowControlWindow;

    SpdySynReplyStreamSourceChannel(SpdyChannel framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining, HeaderMap headers, int streamId) {
        super(framedChannel, data, frameDataRemaining);
        this.headers = headers;
        this.streamId = streamId;
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
        flowControlWindow -= read;
        //TODO: RST stream if flow control limits are exceeded?
        //TODO: make this configurable, we should be able to set the policy that is used to determine when to update the window size
        SpdyChannel spdyChannel = getSpdyChannel();
        spdyChannel.updateReceiveFlowControlWindow(read);
        int initialWindowSize = spdyChannel.getInitialWindowSize();
        if (flowControlWindow < (initialWindowSize / 2)) {
            spdyChannel.sendUpdateWindowSize(streamId, initialWindowSize - flowControlWindow);
        }
    }

    public HeaderMap getHeaders() {
        return headers;
    }

    public int getStreamId() {
        return streamId;
    }
}
