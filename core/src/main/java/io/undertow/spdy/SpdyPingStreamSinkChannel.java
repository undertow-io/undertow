package io.undertow.spdy;

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.ImmediatePooled;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
class SpdyPingStreamSinkChannel extends SpdyControlFrameStreamSinkChannel {

    private final int id;

    protected SpdyPingStreamSinkChannel(SpdyChannel channel, int id) {
        super(channel);
        this.id = id;
    }

    @Override
    protected SendFrameHeader createFrameHeader() {
        ByteBuffer buf = ByteBuffer.allocate(12);

        int firstInt = SpdyChannel.CONTROL_FRAME | (getChannel().getSpdyVersion() << 16) | 2;
        SpdyProtocolUtils.putInt(buf, firstInt);
        SpdyProtocolUtils.putInt(buf, 4); //we back fill the length
        SpdyProtocolUtils.putInt(buf, id);
        return new SendFrameHeader(new ImmediatePooled<ByteBuffer>(buf));
    }

}
