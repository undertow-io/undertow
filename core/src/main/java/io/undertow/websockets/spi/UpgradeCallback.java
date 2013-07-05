package io.undertow.websockets.spi;

import org.xnio.Pool;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public interface UpgradeCallback {

    void handleUpgrade(final StreamConnection channel, final Pool<ByteBuffer> buffers);
}
