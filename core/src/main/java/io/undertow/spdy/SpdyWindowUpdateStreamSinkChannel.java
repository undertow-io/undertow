package io.undertow.spdy;

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.ImmediatePooled;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
class SpdyWindowUpdateStreamSinkChannel extends SpdyControlFrameStreamSinkChannel {

    private final int streamId;
    private final int deltaWindowSize;

    protected SpdyWindowUpdateStreamSinkChannel(SpdyChannel channel, int streamId, int deltaWindowSize) {
        super(channel);
        this.streamId = streamId;
        this.deltaWindowSize = deltaWindowSize;
    }

    @Override
    protected SendFrameHeader createFrameHeader() {
        ByteBuffer buf = ByteBuffer.allocate(16);

        int firstInt = SpdyChannel.CONTROL_FRAME | (getChannel().getSpdyVersion() << 16) | 9;
        SpdyProtocolUtils.putInt(buf, firstInt);
        SpdyProtocolUtils.putInt(buf, 8);
        SpdyProtocolUtils.putInt(buf, streamId);
        SpdyProtocolUtils.putInt(buf, deltaWindowSize);
        return new SendFrameHeader(new ImmediatePooled<ByteBuffer>(buf));
    }

}
