package io.undertow.spdy;

import org.xnio.Pool;

import java.nio.ByteBuffer;

/**
 * Parser for SPDY ping frames.
 *
 * @author Stuart Douglas
 */
class SpdyRstStreamParser extends PushBackParser {

    private int statusCode;

    public SpdyRstStreamParser(Pool<ByteBuffer> bufferPool, int frameLength) {
        super(bufferPool, frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource) {
        if (resource.remaining() < 8) {
            return;
        }
        streamId = SpdyProtocolUtils.readInt(resource);
        statusCode = SpdyProtocolUtils.readInt(resource);

    }

    public int getStatusCode() {
        return statusCode;
    }
}
