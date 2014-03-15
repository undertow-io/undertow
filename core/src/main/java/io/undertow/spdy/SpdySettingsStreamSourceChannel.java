package io.undertow.spdy;

import io.undertow.server.protocol.framed.AbstractFramedChannel;
import org.xnio.Pooled;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/**
 * A spdy Settings frame
 *
 *
 * @author Stuart Douglas
 */
public class SpdySettingsStreamSourceChannel extends SpdyStreamSourceChannel {

    private final List<SpdySetting> settings;


    SpdySettingsStreamSourceChannel(AbstractFramedChannel<SpdyChannel, SpdyStreamSourceChannel, SpdyStreamSinkChannel> framedChannel, Pooled<ByteBuffer> data, long frameDataRemaining, List<SpdySetting> settings) {
        super(framedChannel, data, frameDataRemaining);
        this.settings = settings;
        lastFrame();
    }

    public List<SpdySetting> getSettings() {
        return Collections.unmodifiableList(settings);
    }
}
