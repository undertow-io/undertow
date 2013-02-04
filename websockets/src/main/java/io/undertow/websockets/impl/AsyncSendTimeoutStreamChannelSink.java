/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.impl;

import io.undertow.channels.DelegatingStreamSinkChannel;
import io.undertow.websockets.core.WebSocketChannel;
import org.xnio.IoUtils;
import org.xnio.XnioExecutor;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * {@link StreamSinkChannel} wrapper which will close the {@link WebSocketChannel} if it was not closed before
 * the specified asyncSendTimeout.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class AsyncSendTimeoutStreamChannelSink extends DelegatingStreamSinkChannel<AsyncSendTimeoutStreamChannelSink> {
    private final XnioExecutor.Key key;

    public AsyncSendTimeoutStreamChannelSink(final WebSocketChannel wsChannel, StreamSinkChannel channel, int asyncSendTimeout) {
        super(channel);
        if (asyncSendTimeout > 0) {
            key = channel.getWriteThread().executeAfter(new Runnable() {
                @Override
                public void run() {
                    IoUtils.safeClose(wsChannel);
                }
            }, asyncSendTimeout, TimeUnit.MILLISECONDS);
        } else {
            key = null;
        }
    }

    @Override
    public void close() throws IOException {
        if (key != null) {
            key.remove();
        }
        super.close();
    }
}
