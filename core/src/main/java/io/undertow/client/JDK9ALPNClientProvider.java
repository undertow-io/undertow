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

import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.util.ALPN;
import io.undertow.util.ImmediatePooled;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Plaintext HTTP2 client provider that works using HTTP upgrade
 *
 * @author Stuart Douglas
 */
public class JDK9ALPNClientProvider implements ALPNClientSelector.ClientSelector {


    @Override
    public void runAlpn(final SslConnection connection, final ChannelListener<SslConnection> fallback, final ClientCallback<ClientConnection> failedListener, ALPNClientSelector.ALPNProtocol... details) {

        final SSLEngine sslEngine = UndertowXnioSsl.getSslEngine(connection);
        final Map<String, ALPNClientSelector.ALPNProtocol> protocolMap = new HashMap<>();
        String[] protocols = new String[details.length];
        for(int i = 0; i < details.length; ++i) {
            protocols[i] = details[i].getProtocol();
            protocolMap.put(details[i].getProtocol(), details[i]);
        }

        try {
            SSLParameters sslParameters = sslEngine.getSSLParameters();
            ALPN.JDK_9_ALPN_METHODS.setApplicationProtocols().invoke(sslParameters, (Object) protocols);
            sslEngine.setSSLParameters(sslParameters);
        } catch (Exception e) {
            fallback.handleEvent(connection);
            return;
        }

        try {
            connection.startHandshake();
            connection.getSourceChannel().getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                @Override
                public void handleEvent(StreamSourceChannel channel) {
                    try {
                        String selected = (String) ALPN.JDK_9_ALPN_METHODS.getApplicationProtocol().invoke(sslEngine);

                        if (selected != null) {
                            handleSelected(selected);
                        } else {
                            ByteBuffer buf = ByteBuffer.allocate(100);
                            int read = channel.read(buf);
                            if (read > 0) {
                                buf.flip();
                                PushBackStreamSourceConduit pb = new PushBackStreamSourceConduit(connection.getSourceChannel().getConduit());
                                pb.pushBack(new ImmediatePooled<>(buf));
                                connection.getSourceChannel().setConduit(pb);
                            }
                            selected = (String) ALPN.JDK_9_ALPN_METHODS.getApplicationProtocol().invoke(sslEngine);
                            if(selected != null) {
                                handleSelected(selected);
                            } else if(read > 0) {
                                connection.getSourceChannel().suspendReads();
                                fallback.handleEvent(connection);
                                return;
                            }
                        }
                    } catch (IOException e) {
                        failedListener.failed(e);
                    } catch (InvocationTargetException|IllegalAccessException e) {
                        failedListener.failed(new IOException(e));
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
            connection.getSourceChannel().resumeReads();
        } catch (IOException e) {
            failedListener.failed(e);
        } catch (Throwable e) {
            failedListener.failed(new IOException(e));
        }

    }

    @Override
    public boolean isEnabled() {
        return ALPN.JDK_9_ALPN_METHODS != null;
    }
}
