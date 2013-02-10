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

import javax.net.ssl.SSLSession;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.channels.BufferingStreamSinkChannel;
import io.undertow.channels.ReadTimeoutStreamSourceChannel;
import io.undertow.channels.WriteTimeoutStreamSinkChannel;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.SslChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * Open listener for HTTP server.  XNIO should be set up to chain the accept handler to post-accept open
 * listeners to this listener which actually initiates HTTP parsing.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpOpenListener implements ChannelListener<ConnectedStreamChannel>, OpenListener {

    private final Pool<ByteBuffer> bufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;

    public HttpOpenListener(final Pool<ByteBuffer> pool, final int bufferSize) {
        this(pool, OptionMap.EMPTY, bufferSize);
    }

    public HttpOpenListener(final Pool<ByteBuffer> pool, final OptionMap undertowOptions, final int bufferSize) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
        this.bufferSize = bufferSize;
    }

    public void handleEvent(final ConnectedStreamChannel channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        StreamSourceChannel readChannel = channel;
        StreamSinkChannel writeChannel = channel;
        //set read and write timeouts
        if (channel.supportsOption(Options.READ_TIMEOUT)) {
            readChannel = new ReadTimeoutStreamSourceChannel(readChannel);
        }
        if (channel.supportsOption(Options.WRITE_TIMEOUT)) {
            writeChannel = new WriteTimeoutStreamSinkChannel(writeChannel);
        }
        PipeLiningBuffer pipeLiningBuffer = null;
        if(undertowOptions.get(UndertowOptions.BUFFER_PIPELINED_DATA, false)) {
            pipeLiningBuffer = new BufferingStreamSinkChannel(writeChannel, bufferPool);
        }

        final PushBackStreamChannel pushBackStreamChannel = new PushBackStreamChannel(readChannel);
        SSLSession sslSession = null;
        if (channel instanceof SslChannel) {
            sslSession = ((SslChannel) channel).getSslSession();
        }
        HttpServerConnection connection = new HttpServerConnection(new AssembledConnectedStreamChannel(channel, readChannel, writeChannel), bufferPool, rootHandler, undertowOptions, bufferSize, sslSession, pipeLiningBuffer);
        HttpReadListener readListener = new HttpReadListener(writeChannel, pushBackStreamChannel, connection);
        pushBackStreamChannel.getReadSetter().set(readListener);
        readListener.handleEvent(pushBackStreamChannel);
    }

    @Override
    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    @Override
    public void setRootHandler(final HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
    }

    @Override
    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    @Override
    public void setUndertowOptions(final OptionMap undertowOptions) {
        if (undertowOptions == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("undertowOptions");
        }
        this.undertowOptions = undertowOptions;
    }
}
