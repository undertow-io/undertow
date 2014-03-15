package io.undertow.spdy;

import org.xnio.Pool;

import java.nio.ByteBuffer;
import java.util.zip.Inflater;

/**
 * Parser for SPDY headers frames.
 *
 * @author Stuart Douglas
 */
class SpdyHeadersParser extends SpdyHeaderBlockParser {

    public SpdyHeadersParser(Pool<ByteBuffer> bufferPool, SpdyChannel channel, int frameLength, Inflater inflater) {
        super(bufferPool, channel,frameLength, inflater);
    }

    @Override
    protected boolean handleBeforeHeader(ByteBuffer resource) {
        if (resource.remaining() < 4) {
            return false;
        }
        streamId = SpdyProtocolUtils.readInt(resource);
        return true;
    }
}
