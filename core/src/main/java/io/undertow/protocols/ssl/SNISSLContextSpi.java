/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.protocols.ssl;

import java.security.KeyManagementException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * SSLContext that can be used to do SNI matching.
 *
 * This
 */
class SNISSLContextSpi extends SSLContextSpi {

    private final SNIContextMatcher matcher;

    SNISSLContextSpi(SNIContextMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    protected void engineInit(KeyManager[] keyManagers, TrustManager[] trustManagers, SecureRandom secureRandom) throws KeyManagementException {
        //noop
    }

    @Override
    protected SSLSocketFactory engineGetSocketFactory() {
        return matcher.getDefaultContext().getSocketFactory();
    }

    @Override
    protected SSLServerSocketFactory engineGetServerSocketFactory() {
        return matcher.getDefaultContext().getServerSocketFactory();
    }

    @Override
    protected SSLEngine engineCreateSSLEngine() {
        return new SNISSLEngine(matcher);
    }

    @Override
    protected SSLEngine engineCreateSSLEngine(String s, int i) {
        return new SNISSLEngine(matcher, s, i);
    }

    @Override
    protected SSLSessionContext engineGetServerSessionContext() {
        return matcher.getDefaultContext().getServerSessionContext();
    }

    @Override
    protected SSLSessionContext engineGetClientSessionContext() {
        return matcher.getDefaultContext().getClientSessionContext();
    }
}
