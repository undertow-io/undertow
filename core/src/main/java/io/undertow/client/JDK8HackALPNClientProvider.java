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

import io.undertow.protocols.ssl.ALPNHackSSLEngine;
import io.undertow.protocols.ssl.SslConduit;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.util.ImmediatePooled;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.ssl.SslConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDK8 hack based ALPN client provider
 *
 * @author Stuart Douglas
 */
public class JDK8HackALPNClientProvider implements ALPNClientSelector.ClientSelector {


    @Override
    public void runAlpn(SslConnection connection, ChannelListener<SslConnection> fallback, ClientCallback<ClientConnection> failedListener, ALPNClientSelector.ALPNProtocol... details) {

        final SslConnection sslConnection = connection;
        final SslConduit conduit = UndertowXnioSsl.getSslConduit(sslConnection);
        final ALPNHackSSLEngine sslEngine = new ALPNHackSSLEngine(conduit.getSSLEngine());
        conduit.setSslEngine(sslEngine);

        final Map<String, ALPNClientSelector.ALPNProtocol> protocolMap = new HashMap<>();
        List<String> protocols = new ArrayList<>(details.length);
        for(int i = 0; i < details.length; ++i) {
            protocols.add(details[i].getProtocol());
            protocolMap.put(details[i].getProtocol(), details[i]);
        }
        sslEngine.setApplicationProtocols(protocols);

        try {
            sslConnection.startHandshake();
            sslConnection.getSourceChannel().getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                @Override
                public void handleEvent(StreamSourceChannel channel) {

                    if (sslEngine.getSelectedApplicationProtocol() != null) {
                        handleSelected(sslEngine.getSelectedApplicationProtocol());
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
                            if(sslEngine.getSelectedApplicationProtocol() != null) {
                                handleSelected(sslEngine.getSelectedApplicationProtocol());
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
        return ALPNHackSSLEngine.ENABLED;
    }

}
