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
import io.undertow.server.DelegateOpenListener;
import io.undertow.server.OpenListener;
import org.eclipse.jetty.alpn.ALPN;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Open listener adaptor for ALPN connections
 *
 * Not a proper open listener as such, but more a mechanism for selecting between them
 *
 * @author Stuart Douglas
 */
public class AlpnOpenListener implements ChannelListener<StreamConnection> {

    private static final String PROTOCOL_KEY = AlpnOpenListener.class.getName() + ".protocol";

    private final Pool<ByteBuffer> bufferPool;

    private final Map<String, DelegateOpenListener> listeners = new HashMap<>();
    private final String fallbackProtocol;

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool, String fallbackProtocol, DelegateOpenListener fallbackListener) {
        this.bufferPool = bufferPool;
        this.fallbackProtocol = fallbackProtocol;
        if(fallbackProtocol != null && fallbackListener != null) {
            listeners.put(fallbackProtocol, fallbackListener);
        }
    }

    public AlpnOpenListener(Pool<ByteBuffer> bufferPool, DelegateOpenListener httpListener) {
        this(bufferPool, "http/1.1", httpListener);
    }


    public AlpnOpenListener(Pool<ByteBuffer> bufferPool) {
        this(bufferPool, null, null);
    }

    public AlpnOpenListener addProtocol(String name, DelegateOpenListener listener) {
        listeners.put(name, listener);
        return this;
    }

    public void handleEvent(final StreamConnection channel) {
        if (UndertowLogger.REQUEST_LOGGER.isTraceEnabled()) {
            UndertowLogger.REQUEST_LOGGER.tracef("Opened connection with %s", channel.getPeerAddress());
        }
        final AlpnConnectionListener potentialConnection = new AlpnConnectionListener(channel);
        channel.getSourceChannel().setReadListener(potentialConnection);
        final SSLEngine sslEngine = JsseXnioSsl.getSslEngine((SslConnection) channel);
        final String existing = (String) sslEngine.getSession().getValue(PROTOCOL_KEY);
        ALPN.put(sslEngine, new ALPN.ServerProvider() {
            @Override
            public void unsupported() {
                if (existing == null || !listeners.containsKey(existing)) {
                    if(fallbackProtocol == null) {
                        UndertowLogger.REQUEST_IO_LOGGER.noALPNFallback(channel.getPeerAddress());
                        IoUtils.safeClose(channel);
                    }
                    potentialConnection.selected = fallbackProtocol;
                } else {
                    potentialConnection.selected = existing;
                }
            }

            @Override
            public String select(List<String> strings) {

                ALPN.remove(sslEngine);
                for (String s : strings) {
                    OpenListener listener = listeners.get(s);
                    if (listener != null) {
                        potentialConnection.selected = s;
                        sslEngine.getSession().putValue(PROTOCOL_KEY, s);
                        return s;
                    }
                }

                if(fallbackProtocol == null) {
                    UndertowLogger.REQUEST_IO_LOGGER.noALPNFallback(channel.getPeerAddress());
                    IoUtils.safeClose(channel);
                    return null;
                }
                sslEngine.getSession().putValue(PROTOCOL_KEY, fallbackProtocol);
                potentialConnection.selected = fallbackProtocol;
                return fallbackProtocol;
            }
        });
        potentialConnection.handleEvent(channel.getSourceChannel());

    }

    private class AlpnConnectionListener implements ChannelListener<StreamSourceChannel> {
        private String selected;
        private final StreamConnection channel;

        private AlpnConnectionListener(StreamConnection channel) {
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
                    if(selected != null) {
                        DelegateOpenListener listener = listeners.get(selected);
                        source.getReadSetter().set(null);
                        listener.handleEvent(channel, buffer);
                        free = false;
                        return;
                    } else if(res > 0) {
                        if(fallbackProtocol == null) {
                            UndertowLogger.REQUEST_IO_LOGGER.noALPNFallback(channel.getPeerAddress());
                            IoUtils.safeClose(channel);
                            return;
                        }
                        DelegateOpenListener listener = listeners.get(fallbackProtocol);
                        source.getReadSetter().set(null);
                        listener.handleEvent(channel, buffer);
                        free = false;
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
}
