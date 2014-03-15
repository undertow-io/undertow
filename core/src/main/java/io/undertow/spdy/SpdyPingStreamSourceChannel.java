package io.undertow.spdy;

import io.undertow.server.protocol.framed.AbstractFramedChannel;
import org.xnio.Pooled;

import java.nio.ByteBuffer;

/**
 * A SPDY Ping frame
 *
 * @author Stuart Douglas
 */
public class SpdyPingStreamSourceChannel extends SpdyStreamSourceChannel {

    private final int id;

    SpdyPingStreamSourceChannel(AbstractFramedChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining, int id) {
        super(framedChannel, data, frameDataRemaining);
        this.id = id;
        lastFrame();
    }

    public int getId() {
        return id;
    }
}
