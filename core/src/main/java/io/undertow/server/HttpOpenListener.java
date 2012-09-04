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

package io.undertow.server;

import java.nio.ByteBuffer;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
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
public final class HttpOpenListener implements ChannelListener<ConnectedStreamChannel> {

    private final Pool<ByteBuffer> bufferPool;

    private volatile HttpHandler rootHandler;

    /**
     * The maximum number of requests that can be processed at a time for a given connection.
     */
    private final int maxConcurrentRequestsPerConnection;

    public HttpOpenListener(final Pool<ByteBuffer> pool) {
        this(pool, 1);
    }
    public HttpOpenListener(final Pool<ByteBuffer> pool, int maxConcurrentRequestsPerConnection) {
        if(maxConcurrentRequestsPerConnection <= 0) {
            throw UndertowMessages.MESSAGES.maximumConcurrentRequestsMustBeLargerThanZero();
        }
        this.bufferPool = pool;
        this.maxConcurrentRequestsPerConnection = maxConcurrentRequestsPerConnection;
    }

    public void handleEvent(final ConnectedStreamChannel channel) {
        if(UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        final PushBackStreamChannel pushBackStreamChannel = new PushBackStreamChannel(channel);
        HttpServerConnection connection = new HttpServerConnection(channel, bufferPool, rootHandler, maxConcurrentRequestsPerConnection);
        HttpReadListener readListener = new HttpReadListener(channel, connection);
        pushBackStreamChannel.getReadSetter().set(readListener);
        readListener.handleEvent(pushBackStreamChannel);
    }

    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    public void setRootHandler(final HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
    }

    public int getMaxConcurrentRequestsPerConnection() {
        return maxConcurrentRequestsPerConnection;
    }
}
