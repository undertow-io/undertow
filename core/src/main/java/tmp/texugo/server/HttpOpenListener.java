/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tmp.texugo.server;

import java.nio.ByteBuffer;
import org.xnio.ChannelListener;
import org.xnio.Pool;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;

/**
 * Open listener for HTTP server.  XNIO should be set up to chain the accept handler to post-accept open
 * listeners to this listener which actually initiates HTTP parsing.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpOpenListener implements ChannelListener<ConnectedStreamChannel> {

    private final Pool<ByteBuffer> bufferPool;

    HttpOpenListener(final Pool<ByteBuffer> pool) {
        bufferPool = pool;
    }

    public void handleEvent(final ConnectedStreamChannel channel) {
        final PushBackStreamChannel pushBackStreamChannel = new PushBackStreamChannel(channel);
        HttpReadListener readListener = new HttpReadListener(bufferPool);
        pushBackStreamChannel.getReadSetter().set(readListener);
        readListener.handleEvent(pushBackStreamChannel);
        channel.resumeReads();
    }
}
