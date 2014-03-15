package io.undertow.spdy;

import org.xnio.Pool;

import java.nio.ByteBuffer;

/**
 * Parser for SPDY ping frames.
 *
 * @author Stuart Douglas
 */
class SpdyWindowUpdateParser extends PushBackParser {

    private int deltaWindowSize;

    public SpdyWindowUpdateParser(Pool<ByteBuffer> bufferPool, int frameLength) {
        super(bufferPool, frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource) {
        if (resource.remaining() < 8) {
            return;
        }
        streamId = SpdyProtocolUtils.readInt(resource);
        deltaWindowSize = SpdyProtocolUtils.readInt(resource);

    }

    public int getDeltaWindowSize() {
        return deltaWindowSize;
    }
}
