package io.undertow.spdy;

import org.xnio.Pool;

import java.nio.ByteBuffer;

/**
 * Parser for SPDY ping frames.
 *
 * @author Stuart Douglas
 */
class SpdyPingParser extends PushBackParser {

    private int id;

    public SpdyPingParser(Pool<ByteBuffer> bufferPool, int frameLength) {
        super(bufferPool, frameLength);
    }

    @Override
    protected void finished() {
    }

    @Override
    protected void handleData(ByteBuffer resource) {
        if (resource.remaining() < 4) {
            return;
        }
        id = SpdyProtocolUtils.readInt(resource);
    }

    public int getId() {
        return id;
    }
}
