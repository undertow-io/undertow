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
import io.undertow.util.ALPN;
import org.xnio.ChannelListener;
import org.xnio.ssl.SslConnection;

/**
 * @author Stuart Douglas
 */
public class ALPNClientSelector {

    private static final ClientSelector SELECTOR;
    static {
        if(ALPN.JDK_9_ALPN_METHODS != null) {
            SELECTOR = new JDK9ALPNClientProvider();
        } else if(ALPNHackSSLEngine.ENABLED) {
            SELECTOR = new JDK8HackALPNClientProvider();
        } else {
            SELECTOR = new JettyALPNClientProvider();
        }
    }

    private ALPNClientSelector() {

    }

    public static void runAlpn(SslConnection connection, ChannelListener<SslConnection> fallback, ClientCallback<ClientConnection> failedListener, ALPNProtocol... details) {
        SELECTOR.runAlpn(connection, fallback, failedListener, details);
    }

    public static boolean isEnabled() {
        return SELECTOR.isEnabled();
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

    interface ClientSelector {

        void runAlpn(SslConnection connection, ChannelListener<SslConnection> fallback,ClientCallback<ClientConnection> failedListener, ALPNProtocol... details);

        boolean isEnabled();
    }
}
