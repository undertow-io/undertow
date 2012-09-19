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
import io.undertow.UndertowOptions;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
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

    private volatile OptionMap undertowOptions;

    public HttpOpenListener(final Pool<ByteBuffer> pool) {
        this(pool, OptionMap.EMPTY);
    }

    public HttpOpenListener(final Pool<ByteBuffer> pool, final OptionMap undertowOptions) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
    }

    public void handleEvent(final ConnectedStreamChannel channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        final PushBackStreamChannel pushBackStreamChannel = new PushBackStreamChannel(channel);
        HttpServerConnection connection = new HttpServerConnection(channel, bufferPool, rootHandler, undertowOptions);
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

    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    public void setUndertowOptions(final OptionMap undertowOptions) {
        if(undertowOptions == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        this.undertowOptions = undertowOptions;
    }
}
