package io.undertow.websockets.spi;

import java.nio.ByteBuffer;

import org.xnio.Pool;
import org.xnio.StreamConnection;

/**
 * @author Stuart Douglas
 */
public interface UpgradeCallback {

    void handleUpgrade(final StreamConnection channel, final Pool<ByteBuffer> buffers);
}
