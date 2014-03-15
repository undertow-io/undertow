package io.undertow.spdy;

import org.xnio.Pool;

import java.nio.ByteBuffer;

/**
 * Parser for SPDY ping frames.
 *
 * @author Stuart Douglas
 */
public class SpdyGoAwayParser extends PushBackParser {

    private int statusCode;
    private int lastGoodStreamId;

    public SpdyGoAwayParser(Pool<ByteBuffer> bufferPool, int frameLength) {
        super(bufferPool, frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource) {
        if (resource.remaining() < 8) {
            return;
        }
        lastGoodStreamId = SpdyProtocolUtils.readInt(resource);
        statusCode = SpdyProtocolUtils.readInt(resource);

    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getLastGoodStreamId() {
        return lastGoodStreamId;
    }
}
