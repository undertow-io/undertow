package io.undertow.spdy;

import io.undertow.server.protocol.framed.AbstractFramedChannel;
import io.undertow.server.protocol.framed.AbstractFramedStreamSourceChannel;
import io.undertow.server.protocol.framed.FrameHeaderData;
import org.xnio.Bits;
import org.xnio.Pooled;

import java.nio.ByteBuffer;

/**
 * SPDY stream source channel
 *
 * @author Stuart Douglas
 */
public class SpdyStreamSourceChannel extends AbstractFramedStreamSourceChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> {


    SpdyStreamSourceChannel(AbstractFramedChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> framedChannel) {
        super(framedChannel);
    }

    SpdyStreamSourceChannel(AbstractFramedChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining) {
        super(framedChannel, data, frameDataRemaining);
    }

    @Override
    protected void handleHeaderData(FrameHeaderData headerData) {
        SpdyChannel.SpdyFrameParser data = (SpdyChannel.SpdyFrameParser) headerData;
        if(Bits.anyAreSet(data.flags, SpdyChannel.FLAG_FIN)) {
            this.lastFrame();
        }
    }

    @Override
    protected SpdyChannel getFramedChannel() {
        return (SpdyChannel) super.getFramedChannel();
    }

    public SpdyChannel getSpdyChannel() {
        return getFramedChannel();
    }

    @Override
    protected void lastFrame() {
        super.lastFrame();
    }
}
