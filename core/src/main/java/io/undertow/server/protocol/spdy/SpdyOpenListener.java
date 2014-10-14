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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import javax.net.ssl.SSLEngine;

import io.undertow.conduits.BytesReceivedStreamSourceConduit;
import io.undertow.conduits.BytesSentStreamSinkConduit;
import io.undertow.server.ConnectorStatistics;
import io.undertow.server.ConnectorStatisticsImpl;
import org.eclipse.jetty.alpn.ALPN;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.SslConnection;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.protocols.spdy.SpdyChannel;
import io.undertow.server.HttpHandler;
import io.undertow.server.OpenListener;
import io.undertow.server.protocol.http.HttpOpenListener;


/**
 * Open listener for SPDY server
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SpdyOpenListener implements ChannelListener<StreamConnection>, OpenListener {

    private static final String PROTOCOL_KEY = SpdyOpenListener.class.getName() + ".protocol";

    private static final String SPDY_3 = "spdy/3";
    private static final String SPDY_3_1 = "spdy/3.1";
    private static final String HTTP_1_1 = "http/1.1";
    private final Pool<ByteBuffer> bufferPool;
    private final Pool<ByteBuffer> heapBufferPool;
    private final int bufferSize;

    private volatile HttpHandler rootHandler;

    private volatile OptionMap undertowOptions;
    private final HttpOpenListener delegate;
    private volatile boolean statisticsEnabled;
    private final ConnectorStatisticsImpl connectorStatistics;

    public SpdyOpenListener(final Pool<ByteBuffer> pool, final Pool<ByteBuffer> heapBufferPool) {
        this(pool, heapBufferPool, OptionMap.EMPTY, null);
    }

    public SpdyOpenListener(final Pool<ByteBuffer> pool, final Pool<ByteBuffer> heapBufferPool, final OptionMap undertowOptions) {
        this(pool, heapBufferPool, undertowOptions, null);
    }

    public SpdyOpenListener(final Pool<ByteBuffer> pool, final Pool<ByteBuffer> heapBufferPool,  HttpOpenListener httpDelegate) {
        this(pool, heapBufferPool, OptionMap.EMPTY, httpDelegate);
    }

    public SpdyOpenListener(final Pool<ByteBuffer> pool, final Pool<ByteBuffer> heapBufferPool, final OptionMap undertowOptions, HttpOpenListener httpDelegate) {
        this.undertowOptions = undertowOptions;
        this.bufferPool = pool;
        Pooled<ByteBuffer> buf = pool.allocate();
        this.bufferSize = buf.getResource().remaining();
        buf.free();
        this.delegate = httpDelegate;
        this.heapBufferPool = heapBufferPool;
        Pooled<ByteBuffer> buff = heapBufferPool.allocate();
        try {
            if (!buff.getResource().hasArray()) {
                throw UndertowMessages.MESSAGES.mustProvideHeapBuffer();
            }
        } finally {
            buff.free();
        }
        connectorStatistics = new ConnectorStatisticsImpl();
        statisticsEnabled = undertowOptions.get(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false);
    }

    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        final PotentialSPDYConnection potentialConnection = new PotentialSPDYConnection(channel);
        channel.getSourceChannel().setReadListener(potentialConnection);
        final SSLEngine sslEngine = JsseXnioSsl.getSslEngine((SslConnection) channel);
        final String existing = (String) sslEngine.getSession().getValue(PROTOCOL_KEY);

        ALPN.put(sslEngine, new ALPN.ServerProvider() {
            @Override
            public void unsupported() {
                if(existing == null) {
                    potentialConnection.selected = HTTP_1_1;
                } else {
                    potentialConnection.selected = existing;
                }
            }

            @Override
            public String select(List<String> strings) {
                ALPN.remove(sslEngine);
                for (String s : strings) {
                    if (s.equals(SPDY_3_1)) {
                        potentialConnection.selected = s;
                        sslEngine.getSession().putValue(PROTOCOL_KEY, s);
                        return s;
                    }
                }
                sslEngine.getSession().putValue(PROTOCOL_KEY, HTTP_1_1);
                potentialConnection.selected = HTTP_1_1;
                return HTTP_1_1;
            }
        });
        potentialConnection.handleEvent(channel.getSourceChannel());

    }

    @Override
    public HttpHandler getRootHandler() {
        return rootHandler;
    }

    @Override
    public void setRootHandler(final HttpHandler rootHandler) {
        this.rootHandler = rootHandler;
        if (delegate != null) {
            delegate.setRootHandler(rootHandler);
        }
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
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    private class PotentialSPDYConnection implements ChannelListener<StreamSourceChannel> {
        private String selected;
        private final StreamConnection channel;

        private PotentialSPDYConnection(StreamConnection channel) {
            this.channel = channel;
        }

        @Override
        public void handleEvent(StreamSourceChannel source) {
            Pooled<ByteBuffer> buffer = bufferPool.allocate();
            boolean free = true;
            try {
                while (true) {
                    int res = channel.getSourceChannel().read(buffer.getResource());
                    if (res == -1) {
                        IoUtils.safeClose(channel);
                        return;
                    }
                    buffer.getResource().flip();
                    if (SPDY_3.equals(selected) || SPDY_3_1.equals(selected)) {

                        //cool, we have a spdy connection.
                        SpdyChannel channel = new SpdyChannel(this.channel, bufferPool, buffer, heapBufferPool, false);
                        Integer idleTimeout = undertowOptions.get(UndertowOptions.IDLE_TIMEOUT);
                        if (idleTimeout != null && idleTimeout > 0) {
                            channel.setIdleTimeout(idleTimeout);
                        }
                        free = false;

                        if(statisticsEnabled) {
                            this.channel.getSinkChannel().setConduit(new BytesSentStreamSinkConduit(this.channel.getSinkChannel().getConduit(), connectorStatistics.sentAccumulator()));
                            this.channel.getSourceChannel().setConduit(new BytesReceivedStreamSourceConduit(this.channel.getSourceChannel().getConduit(), connectorStatistics.receivedAccumulator()));
                        }
                        channel.getReceiveSetter().set(new SpdyReceiveListener(rootHandler, getUndertowOptions(), bufferSize, connectorStatistics));
                        channel.resumeReceives();
                        return;
                    } else if (HTTP_1_1.equals(selected) || res > 0) {
                        if (delegate == null) {
                            UndertowLogger.REQUEST_IO_LOGGER.couldNotInitiateSpdyConnection();
                            IoUtils.safeClose(channel);
                            return;
                        }
                        channel.getSourceChannel().setReadListener(null);
                        if (res > 0) {
                            PushBackStreamSourceConduit pushBackStreamSourceConduit = new PushBackStreamSourceConduit(channel.getSourceChannel().getConduit());
                            channel.getSourceChannel().setConduit(pushBackStreamSourceConduit);
                            pushBackStreamSourceConduit.pushBack(buffer);
                            free = false;
                        }
                        delegate.handleEvent(channel);
                        return;
                    } else if (res == 0) {
                        channel.getSourceChannel().resumeReads();
                        return;
                    }
                }

            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(channel);
            } finally {
                if (free) {
                    buffer.free();
                }
            }
        }
    }
    @Override
    public ConnectorStatistics getConnectorStatistics() {
        if(statisticsEnabled) {
            return connectorStatistics;
        }
        return null;
    }
}
