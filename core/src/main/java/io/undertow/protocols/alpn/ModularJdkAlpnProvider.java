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

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

/**
 * Open listener adaptor for ALPN connections that use the Modular JDK API
 * <p>
 * Not a proper open listener as such, but more a mechanism for selecting between them
 *
 * @author Stuart Douglas
 */
public class ModularJdkAlpnProvider implements ALPNProvider {

    @Override
    public boolean isEnabled(final SSLEngine sslEngine) {
        return true;
    }

    @Override
    public SSLEngine setProtocols(final SSLEngine engine, final String[] protocols) {
        SSLParameters sslParameters = engine.getSSLParameters();
        sslParameters.setApplicationProtocols(protocols);
        engine.setSSLParameters(sslParameters);
        return engine;
    }

    @Override
    public String getSelectedProtocol(final SSLEngine engine) {
        return engine.getApplicationProtocol();
    }

    @Override
    public int getPriority() {
        return 200;
    }

    @Override
    public String toString() {
        return "ModularJdkAlpnProvider";
    }

}
