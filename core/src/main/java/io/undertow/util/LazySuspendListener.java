package io.undertow.util;

import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;

/**
 * @author Stuart Douglas
 */
public class LazySuspendListener implements ChannelListener<StreamSourceChannel> {

    public static final LazySuspendListener INSTANCE = new LazySuspendListener();

    private LazySuspendListener() {

    }

    @Override
    public void handleEvent(StreamSourceChannel channel) {
        channel.suspendReads();
    }
}
