package io.undertow.spdy;

import org.xnio.Pool;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
class SpdySettingsParser extends PushBackParser {

    private int length = -1;

    private int count = 0;

    private final List<SpdySetting> settings = new ArrayList<SpdySetting>();

    public SpdySettingsParser(Pool<ByteBuffer> bufferPool, int frameLength) {
        super(bufferPool, frameLength);
    }

    @Override
    protected void handleData(ByteBuffer resource) {
        if (length == -1) {
            if (resource.remaining() < 4) {
                return;
            }
            length = (resource.get() & 0xFF) << 24;
            length += (resource.get() & 0xFF) << 16;
            length += (resource.get() & 0xFF) << 8;
            length += (resource.get() & 0xFF);
        }
        while (count < length) {
            if (resource.remaining() < 8) {
                return;
            }
            int flags = resource.get() & 0xFF;
            int id = (resource.get() & 0xFF) << 16;
            id += (resource.get() & 0xFF) << 8;
            id += (resource.get() & 0xFF);
            int value = (resource.get() & 0xFF) << 24;
            value += (resource.get() & 0xFF) << 16;
            value += (resource.get() & 0xFF) << 8;
            value += (resource.get() & 0xFF);
            boolean found = false;
            //according to the spec we MUST ignore duplicates
            for (SpdySetting existing : settings) {
                if (existing.getId() == id) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                settings.add(new SpdySetting(flags, id, value));
            }
            count++;
        }
    }

    public List<SpdySetting> getSettings() {
        return settings;
    }
}
