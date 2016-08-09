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

package io.undertow.protocols.alpn;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.eclipse.jetty.alpn.ALPN;

/**
 * Jetty ALPN implementation. This is the lowest priority
 *
 * @author Stuart Douglas
 */
public class JettyAlpnProvider implements ALPNProvider {

    private static final String PROTOCOL_KEY = JettyAlpnProvider.class.getName() + ".protocol";

    private static final boolean ENABLED;

    static {
        ENABLED = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
                try {
                    Class.forName("org.eclipse.jetty.alpn.ALPN", true, JettyAlpnProvider.class.getClassLoader());
                    return true;
                } catch (ClassNotFoundException e) {
                    return false;
                }
            }
        });
    }

    @Override
    public boolean isEnabled(SSLEngine sslEngine) {
        return ENABLED;
    }

    @Override
    public SSLEngine setProtocols(SSLEngine engine, String[] protocols) {
        return Impl.setProtocols(engine, protocols);
    }

    @Override
    public String getSelectedProtocol(SSLEngine engine) {
        SSLSession handshake = engine.getHandshakeSession();
        if (handshake != null) {
            return (String) handshake.getValue(PROTOCOL_KEY);
        }
        handshake = engine.getSession();
        if (handshake != null) {
            return (String) handshake.getValue(PROTOCOL_KEY);
        }
        return null;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    private static class Impl {

        static SSLEngine setProtocols(final SSLEngine engine, final String[] protocols) {
            if (engine.getUseClientMode()) {
                ALPN.put(engine, new ALPNClientSelectionProvider(Arrays.asList(protocols), engine));
            } else {
                ALPN.put(engine, new ALPN.ServerProvider() {
                    @Override
                    public void unsupported() {
                        ALPN.remove(engine);
                    }

                    @Override
                    public String select(List<String> strings) {
                        ALPN.remove(engine);
                        for (String p : protocols) {
                            if (strings.contains(p)) {
                                engine.getHandshakeSession().putValue(PROTOCOL_KEY, p);
                                return p;
                            }
                        }
                        return null;
                    }
                });
            }
            return engine;
        }
    }


    private static class ALPNClientSelectionProvider implements ALPN.ClientProvider {
        final List<String> protocols;
        private String selected;
        private final SSLEngine sslEngine;

        private ALPNClientSelectionProvider(List<String> protocols, SSLEngine sslEngine) {
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
