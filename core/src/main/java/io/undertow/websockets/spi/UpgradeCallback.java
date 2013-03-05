package io.undertow.websockets.spi;

import java.nio.ByteBuffer;

import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author Stuart Douglas
 */
public interface UpgradeCallback {

    void handleUpgrade(final ConnectedStreamChannel channel, final Pool<ByteBuffer> buffers);
}
