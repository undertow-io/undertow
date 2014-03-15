package io.undertow.spdy;

import io.undertow.server.protocol.framed.AbstractFramedStreamSinkChannel;

/**
 * @author Stuart Douglas
 */
public class SpdyStreamSinkChannel extends AbstractFramedStreamSinkChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> {

    SpdyStreamSinkChannel(SpdyChannel channel) {
        super(channel);
    }

    @Override
    protected boolean isLastFrame() {
        return false;
    }


}
