/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.protocol.http;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.conduits.ReadTimeoutStreamSourceConduit;
import io.undertow.conduits.WriteTimeoutStreamSinkConduit;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.StreamConnection;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Open listener for HTTP server.  XNIO should be set up to chain the accept handler to post-accept open
 * listeners to this listener which actually initiates HTTP parsing.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpOpenListener implements ChannelListener<StreamConnection>, OpenListener {

    private final Pool<ByteBuffer> bufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;

    private volatile HttpRequestParser parser;

    public HttpOpenListener(final Pool<ByteBuffer> pool, final int bufferSize) {
        this(pool, OptionMap.EMPTY, bufferSize);
    }

    public HttpOpenListener(final Pool<ByteBuffer> pool, final OptionMap undertowOptions, final int bufferSize) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
        this.bufferSize = bufferSize;
        parser = HttpRequestParser.instance(undertowOptions);
    }

    @Override
    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }

        //set read and write timeouts
        try {
            Integer readTimeout = channel.getOption(Options.READ_TIMEOUT);
            Integer idleTimeout = undertowOptions.get(UndertowOptions.IDLE_TIMEOUT);
            if ((readTimeout == null || readTimeout <= 0) && idleTimeout != null) {
                readTimeout = idleTimeout;
            } else if (readTimeout != null && idleTimeout != null && idleTimeout > 0) {
                readTimeout = Math.min(readTimeout, idleTimeout);
            }
            if (readTimeout != null && readTimeout > 0) {
                channel.getSourceChannel().setConduit(new ReadTimeoutStreamSourceConduit(channel.getSourceChannel().getConduit(), channel, this));
            }
            Integer writeTimeout = channel.getOption(Options.WRITE_TIMEOUT);
            if ((writeTimeout == null || writeTimeout <= 0) && idleTimeout != null) {
                writeTimeout = idleTimeout;
            } else if (writeTimeout != null && idleTimeout != null && idleTimeout > 0) {
                writeTimeout = Math.min(writeTimeout, idleTimeout);
            }
            if (writeTimeout != null && writeTimeout > 0) {
                channel.getSinkChannel().setConduit(new WriteTimeoutStreamSinkConduit(channel.getSinkChannel().getConduit(), channel, this));
            }
        } catch (IOException e) {
            IoUtils.safeClose(channel);
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
        }


        HttpServerConnection connection = new HttpServerConnection(channel, bufferPool, rootHandler, undertowOptions, bufferSize);
        HttpReadListener readListener = new HttpReadListener(connection, parser);

        connection.setReadListener(readListener);
        readListener.newRequest();
        channel.getSourceChannel().setReadListener(readListener);
        readListener.handleEvent(channel.getSourceChannel());
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
        this.parser = HttpRequestParser.instance(undertowOptions);
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }
}
