package io.undertow.spdy;

import io.undertow.server.protocol.framed.AbstractFramedChannel;
import org.xnio.Pooled;

import java.nio.ByteBuffer;

/**
 * A SPDY Ping frame
 *
 * @author Stuart Douglas
 */
public class SpdyGoAwayStreamSourceChannel extends SpdyStreamSourceChannel {

    private final int status;
    private final int lastGoodStreamId;

    SpdyGoAwayStreamSourceChannel(AbstractFramedChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining, int status, int lastGoodStreamId) {
        super(framedChannel, data, frameDataRemaining);
        this.status = status;
        this.lastGoodStreamId = lastGoodStreamId;
        lastFrame();
    }

    public int getStatus() {
        return status;
    }

    public int getLastGoodStreamId() {
        return lastGoodStreamId;
    }
}
