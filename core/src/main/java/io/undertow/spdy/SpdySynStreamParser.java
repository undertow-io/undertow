package io.undertow.spdy;

import org.xnio.Pool;

import java.nio.ByteBuffer;
import java.util.zip.Inflater;

/**
 * Parser for SPDY syn stream frames
 *
 * @author Stuart Douglas
 */
class SpdySynStreamParser extends SpdyHeaderBlockParser {

    private static final int STREAM_ID_MASK = ~(1 << 7);
    private int associatedToStreamId = -1;
    private int priority = -1;

    public SpdySynStreamParser(Pool<ByteBuffer> bufferPool, SpdyChannel channel, int frameLength, Inflater inflater) {
        super(bufferPool, channel, frameLength, inflater);
    }

    protected boolean handleBeforeHeader(ByteBuffer resource) {
        if (streamId == -1) {
            if (resource.remaining() < 4) {
                return false;
            }
            streamId = (resource.get() & STREAM_ID_MASK & 0xFF) << 24;
            streamId += (resource.get() & 0xFF) << 16;
            streamId += (resource.get() & 0xFF) << 8;
            streamId += (resource.get() & 0xFF);
        }
        if (associatedToStreamId == -1) {
            if (resource.remaining() < 4) {
                return false;
            }
            associatedToStreamId = (resource.get() & STREAM_ID_MASK & 0xFF) << 24;
            associatedToStreamId += (resource.get() & 0xFF) << 16;
            associatedToStreamId += (resource.get() & 0xFF) << 8;
            associatedToStreamId += (resource.get() & 0xFF);
        }
        if (priority == -1) {
            if (resource.remaining() < 2) {
                return false;
            }
            priority = (resource.get() >> 5) & 0xFF;
            resource.get(); //unused at the moment
        }
        return true;
    }

    public int getAssociatedToStreamId() {
        return associatedToStreamId;
    }

    public int getPriority() {
        return priority;
    }
}
