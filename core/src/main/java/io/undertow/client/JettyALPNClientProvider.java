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

package io.undertow.client;

import io.undertow.UndertowLogger;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.util.ImmediatePooled;
import org.eclipse.jetty.alpn.ALPN;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jetty ALPN client provider
 *
 * @author Stuart Douglas
 */
public class JettyALPNClientProvider implements ALPNClientSelector.ClientSelector {

    private static final String PROTOCOL_KEY = JettyALPNClientProvider.class.getName() + ".protocol";

    private static final Method ALPN_PUT_METHOD;

    static {
        Method npnPutMethod;
        try {
            Class<?> npnClass = Class.forName("org.eclipse.jetty.alpn.ALPN", false, JettyALPNClientProvider.class.getClassLoader());
            npnPutMethod = npnClass.getDeclaredMethod("put", SSLEngine.class, Class.forName("org.eclipse.jetty.alpn.ALPN$Provider", false, JettyALPNClientProvider.class.getClassLoader()));
        } catch (Exception e) {
            UndertowLogger.CLIENT_LOGGER.jettyALPNNotFound("HTTP2");
            npnPutMethod = null;
        }
        ALPN_PUT_METHOD = npnPutMethod;
    }

    @Override
    public void runAlpn(SslConnection connection, ChannelListener<SslConnection> fallback, ClientCallback<ClientConnection> failedListener, ALPNClientSelector.ALPNProtocol... details) {

        final SslConnection sslConnection = connection;
        final SSLEngine sslEngine = UndertowXnioSsl.getSslEngine(sslConnection);

        final Map<String, ALPNClientSelector.ALPNProtocol> protocolMap = new HashMap<>();
        List<String> protocols = new ArrayList<>(details.length);
        for(int i = 0; i < details.length; ++i) {
            protocols.add(details[i].getProtocol());
            protocolMap.put(details[i].getProtocol(), details[i]);
        }

        final ALPNSelectionProvider selectionProvider = new ALPNSelectionProvider(protocols, sslEngine);
        try {
            ALPN_PUT_METHOD.invoke(null, sslEngine, selectionProvider);
        } catch (Exception e) {
            fallback.handleEvent(sslConnection);
            return;
        }

        try {
            sslConnection.startHandshake();
            sslConnection.getSourceChannel().getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                @Override
                public void handleEvent(StreamSourceChannel channel) {

                    if (selectionProvider.selected != null) {
                        handleSelected(selectionProvider.selected);
                    } else {
                        ByteBuffer buf = ByteBuffer.allocate(100);
                        try {
                            int read = channel.read(buf);
                            if (read > 0) {
                                buf.flip();
                                PushBackStreamSourceConduit pb = new PushBackStreamSourceConduit(connection.getSourceChannel().getConduit());
                                pb.pushBack(new ImmediatePooled<>(buf));
                                connection.getSourceChannel().setConduit(pb);
                            } else if (read == -1) {
                                failedListener.failed(new ClosedChannelException());
                            }
                            if (selectionProvider.selected == null) {
                                selectionProvider.selected = (String) sslEngine.getSession().getValue(PROTOCOL_KEY);
                            }
                            if(selectionProvider.selected != null) {
                                handleSelected(selectionProvider.selected);
                            } else if(read > 0) {
                                sslConnection.getSourceChannel().suspendReads();
                                fallback.handleEvent(sslConnection);
                                return;
                            }
                        } catch (IOException e) {
                            failedListener.failed(e);
                        }
                    }
                }

                protected void handleSelected(String selected) {
                    if (selected.isEmpty()) {
                        connection.getSourceChannel().suspendReads();
                        fallback.handleEvent(connection);
                        return;
                    } else {
                        ALPNClientSelector.ALPNProtocol details = protocolMap.get(selected);
                        if(details == null) {
                            //should never happen
                            connection.getSourceChannel().suspendReads();
                            fallback.handleEvent(connection);
                            return;
                        } else {
                            connection.getSourceChannel().suspendReads();
                            details.getSelected().handleEvent(connection);
                        }
                    }
                }
            });
            sslConnection.getSourceChannel().resumeReads();
        } catch (IOException e) {
            failedListener.failed(e);
        } catch (Throwable e) {
            failedListener.failed(new IOException(e));
        }

    }

    public boolean isEnabled() {
        return ALPN_PUT_METHOD != null;
    }


    private static class ALPNSelectionProvider implements ALPN.ClientProvider {
        final List<String> protocols;
        private String selected;
        private final SSLEngine sslEngine;

        private ALPNSelectionProvider(List<String> protocols, SSLEngine sslEngine) {
            this.protocols = protocols;
            this.sslEngine = sslEngine;
        }

        @Override
        public boolean supports() {
            return true;
        }

        @Override
        public List<String> protocols() {
            return protocols;
        }

        @Override
        public void unsupported() {
            selected = "";
        }

        @Override
        public void selected(String s) {
            ALPN.remove(sslEngine);
            selected = s;
            sslEngine.getHandshakeSession().putValue(PROTOCOL_KEY, selected);
        }
    }
}
