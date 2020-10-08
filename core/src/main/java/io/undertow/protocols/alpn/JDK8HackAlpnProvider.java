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

import java.util.Arrays;
import javax.net.ssl.SSLEngine;

import io.undertow.protocols.ssl.ALPNHackSSLEngine;

/**
 * Open listener adaptor for ALPN connections that uses the SSLExplorer based approach and hack into the JDK8
 * SSLEngine via reflection.
 *
 * @author Stuart Douglas
 */
public class JDK8HackAlpnProvider implements ALPNProvider {

    @Override
    public boolean isEnabled(SSLEngine sslEngine) {
        return ALPNHackSSLEngine.isEnabled(sslEngine);
    }

    @Override
    public SSLEngine setProtocols(SSLEngine engine, String[] protocols) {
        ALPNHackSSLEngine newEngine = engine instanceof ALPNHackSSLEngine ? (ALPNHackSSLEngine) engine : new ALPNHackSSLEngine(engine);
        newEngine.setApplicationProtocols(Arrays.asList(protocols));
        return newEngine;
    }

    @Override
    public String getSelectedProtocol(SSLEngine engine) {
        return ((ALPNHackSSLEngine) engine).getSelectedApplicationProtocol();
    }

    @Override
    public int getPriority() {
        return 300;
    }

    @Override
    public String toString() {
        return "JDK8AlpnProvider";
    }
}
