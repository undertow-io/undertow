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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLEngine;

import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.ssl.SslConnection;
import io.undertow.protocols.alpn.ALPNManager;
import io.undertow.protocols.alpn.ALPNProvider;
import io.undertow.protocols.ssl.SslConduit;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.util.ImmediatePooled;

/**
 * @author Stuart Douglas
 */
public class ALPNClientSelector {

    private ALPNClientSelector() {

    }

    public static void runAlpn(final SslConnection sslConnection, final ChannelListener<SslConnection> fallback, final ClientCallback<ClientConnection> failedListener, final ALPNProtocol... details) {
        SslConduit conduit = UndertowXnioSsl.getSslConduit(sslConnection);

        final ALPNProvider provider = ALPNManager.INSTANCE.getProvider(conduit.getSSLEngine());
        if (provider == null) {
            fallback.handleEvent(sslConnection);
            return;
        }
        String[] protocols = new String[details.length];
        final Map<String, ALPNProtocol> protocolMap = new HashMap<>();
        for (int i = 0; i < protocols.length; ++i) {
            protocols[i] = details[i].getProtocol();
            protocolMap.put(details[i].getProtocol(), details[i]);
        }
        final SSLEngine sslEngine = provider.setProtocols(conduit.getSSLEngine(), protocols);
        conduit.setSslEngine(sslEngine);
        final AtomicReference<Boolean> handshakeDone = new AtomicReference<>(false);

        try {
            sslConnection.startHandshake();
            sslConnection.getHandshakeSetter().set(new ChannelListener<SslConnection>() {
                @Override
                public void handleEvent(SslConnection channel) {
                    if(handshakeDone.get()) {
                        return;
                    }
                    handshakeDone.set(true);
                }
            });
            sslConnection.getSourceChannel().getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                @Override
                public void handleEvent(StreamSourceChannel channel) {

                    String selectedProtocol = provider.getSelectedProtocol(sslEngine);
                    if (selectedProtocol != null) {
                        handleSelected(selectedProtocol);
                    } else {
                        ByteBuffer buf = ByteBuffer.allocate(100);
                        try {
                            int read = channel.read(buf);
                            if (read > 0) {
                                buf.flip();
                                PushBackStreamSourceConduit pb = new PushBackStreamSourceConduit(sslConnection.getSourceChannel().getConduit());
                                pb.pushBack(new ImmediatePooled<>(buf));
                                sslConnection.getSourceChannel().setConduit(pb);
                            } else if (read == -1) {
                                failedListener.failed(new ClosedChannelException());
                            }
                            selectedProtocol = provider.getSelectedProtocol(sslEngine);
                            if (selectedProtocol != null) {
                                handleSelected(selectedProtocol);
                            } else if (read > 0 || handshakeDone.get()) {
                                sslConnection.getSourceChannel().suspendReads();
                                fallback.handleEvent(sslConnection);
                                return;
                            }
                        } catch (IOException e) {
                            failedListener.failed(e);
                        }
                    }
                }

                private void handleSelected(String selected) {
                    if (selected.isEmpty()) {
                        sslConnection.getSourceChannel().suspendReads();
                        fallback.handleEvent(sslConnection);
                        return;
                    } else {
                        ALPNClientSelector.ALPNProtocol details = protocolMap.get(selected);
                        if (details == null) {
                            //should never happen
                            sslConnection.getSourceChannel().suspendReads();
                            fallback.handleEvent(sslConnection);
                            return;
                        } else {
                            sslConnection.getSourceChannel().suspendReads();
                            details.getSelected().handleEvent(sslConnection);
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

    public static class ALPNProtocol {
        private final ChannelListener<SslConnection> selected;
        private final String protocol;

        public ALPNProtocol(ChannelListener<SslConnection> selected, String protocol) {
            this.selected = selected;
            this.protocol = protocol;
        }

        public ChannelListener<SslConnection> getSelected() {
            return selected;
        }

        public String getProtocol() {
            return protocol;
        }
    }
}
