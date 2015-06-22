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

package io.undertow.server.protocol.spdy;

import io.undertow.conduits.BytesReceivedStreamSourceConduit;
import io.undertow.conduits.BytesSentStreamSinkConduit;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.ConnectorStatisticsImpl;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.protocols.spdy.SpdyChannel;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;


/**
 * Open listener for SPDY that uses direct connections rather than ALPN. Not used 'in the wild', but
 * useful for using SPDY in a proxy situation where the overhead of SSL is not desirable.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SpdyPlainOpenListener implements ChannelListener<StreamConnection>, OpenListener {

    private final ByteBufferPool bufferPool;
    private final ByteBufferPool heapBufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;
    private volatile boolean statisticsEnabled;
    private final ConnectorStatisticsImpl connectorStatistics;

    public SpdyPlainOpenListener(final ByteBufferPool pool, final ByteBufferPool heapBufferPool) {
        this(pool, heapBufferPool, OptionMap.EMPTY);
    }

    public SpdyPlainOpenListener(final ByteBufferPool pool, final ByteBufferPool heapBufferPool, final OptionMap undertowOptions) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
        PooledByteBuffer buf = pool.allocate();
        this.bufferSize = buf.getBuffer().remaining();
        buf.close();
        this.heapBufferPool = heapBufferPool;
        PooledByteBuffer buff = heapBufferPool.allocate();
        try {
            if (!buff.getBuffer().hasArray()) {
                throw UndertowMessages.MESSAGES.mustProvideHeapBuffer();
            }
        } finally {
            buff.close();
        }
        connectorStatistics = new ConnectorStatisticsImpl();
        statisticsEnabled = undertowOptions.get(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false);
    }

    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        SpdyChannel spdy = new SpdyChannel(channel, bufferPool, null, heapBufferPool, false, undertowOptions);
        Integer idleTimeout = undertowOptions.get(UndertowOptions.IDLE_TIMEOUT);
        if (idleTimeout != null && idleTimeout > 0) {
            spdy.setIdleTimeout(idleTimeout);
        }
        if(statisticsEnabled) {
            channel.getSinkChannel().setConduit(new BytesSentStreamSinkConduit(channel.getSinkChannel().getConduit(), connectorStatistics.sentAccumulator()));
            channel.getSourceChannel().setConduit(new BytesReceivedStreamSourceConduit(channel.getSourceChannel().getConduit(), connectorStatistics.receivedAccumulator()));
        }
        spdy.getReceiveSetter().set(new SpdyReceiveListener(rootHandler, getUndertowOptions(), bufferSize, statisticsEnabled ? connectorStatistics : null));
        spdy.resumeReceives();

    }

    @Override
    public ConnectorStatistics getConnectorStatistics() {
        if(statisticsEnabled) {
            return connectorStatistics;
        }
        return null;
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
        statisticsEnabled = undertowOptions.get(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false);
    }

    @Override
    public ByteBufferPool getBufferPool() {
        return bufferPool;
    }

}
