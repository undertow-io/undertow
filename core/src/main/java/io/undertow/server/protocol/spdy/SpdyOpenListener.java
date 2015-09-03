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

import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.conduits.BytesReceivedStreamSourceConduit;
import io.undertow.conduits.BytesSentStreamSinkConduit;
import io.undertow.protocols.spdy.SpdyChannel;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.ConnectorStatisticsImpl;
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.XnioByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.Pool;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;


/**
 * Open listener for SPDY server
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SpdyOpenListener implements ChannelListener<StreamConnection>, DelegateOpenListener {

    public static final String SPDY_3_1 = "spdy/3.1";

    private final ByteBufferPool bufferPool;
    private final ByteBufferPool heapBufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;
    private volatile boolean statisticsEnabled;
    private final ConnectorStatisticsImpl connectorStatistics;

    @Deprecated
    public SpdyOpenListener(final Pool<ByteBuffer> pool, final Pool<ByteBuffer> heapBufferPool) {
        this(pool, heapBufferPool, OptionMap.EMPTY);
    }

    @Deprecated
    public SpdyOpenListener(final Pool<ByteBuffer> pool, final Pool<ByteBuffer> heapBufferPool, final OptionMap undertowOptions) {
        this(new XnioByteBufferPool(pool), new XnioByteBufferPool(heapBufferPool), undertowOptions);
    }

    public SpdyOpenListener(final ByteBufferPool pool, final ByteBufferPool heapBufferPool) {
        this(pool, heapBufferPool, OptionMap.EMPTY);
    }

    public SpdyOpenListener(final ByteBufferPool pool, final ByteBufferPool heapBufferPool, final OptionMap undertowOptions) {
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

    @Override
    public void handleEvent(StreamConnection channel) {
        handleEvent(channel, null);
    }

    public void handleEvent(final StreamConnection channel, PooledByteBuffer buffer) {

        //cool, we have a spdy connection.
        SpdyChannel spdyChannel = new SpdyChannel(channel, bufferPool, buffer, heapBufferPool, false, undertowOptions);
        Integer idleTimeout = undertowOptions.get(UndertowOptions.IDLE_TIMEOUT);
        if (idleTimeout != null && idleTimeout > 0) {
            spdyChannel.setIdleTimeout(idleTimeout);
        }

        if(statisticsEnabled) {
            channel.getSinkChannel().setConduit(new BytesSentStreamSinkConduit(channel.getSinkChannel().getConduit(), connectorStatistics.sentAccumulator()));
            channel.getSourceChannel().setConduit(new BytesReceivedStreamSourceConduit(channel.getSourceChannel().getConduit(), connectorStatistics.receivedAccumulator()));
        }
        spdyChannel.getReceiveSetter().set(new SpdyReceiveListener(rootHandler, getUndertowOptions(), bufferSize, connectorStatistics));
        spdyChannel.resumeReceives();
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

    @Override
    public ConnectorStatistics getConnectorStatistics() {
        if(statisticsEnabled) {
            return connectorStatistics;
        }
        return null;
    }

}
