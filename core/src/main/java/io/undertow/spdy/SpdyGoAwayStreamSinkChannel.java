package io.undertow.spdy;

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.ImmediatePooled;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
class SpdyGoAwayStreamSinkChannel extends SpdyControlFrameStreamSinkChannel {

    private final int status;
    private final int lastGoodStreamId;

    protected SpdyGoAwayStreamSinkChannel(SpdyChannel channel, int status, int lastGoodStreamId) {
        super(channel);
        this.status = status;
        this.lastGoodStreamId = lastGoodStreamId;
    }

    @Override
    protected SendFrameHeader createFrameHeader() {
        ByteBuffer buf = ByteBuffer.allocate(16);

        int firstInt = SpdyChannel.CONTROL_FRAME | (getChannel().getSpdyVersion() << 16) | 7;
        SpdyProtocolUtils.putInt(buf, firstInt);
        SpdyProtocolUtils.putInt(buf, 8);
        SpdyProtocolUtils.putInt(buf, lastGoodStreamId);
        SpdyProtocolUtils.putInt(buf, status);
        return new SendFrameHeader( new ImmediatePooled<ByteBuffer>(buf));
    }

    @Override
    protected boolean isLastFrame() {
        return true;
    }
}
