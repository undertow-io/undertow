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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import io.undertow.UndertowLogger;

/**
 * Open listener adaptor for ALPN connections that use the JDK9 API
 * <p>
 * Not a proper open listener as such, but more a mechanism for selecting between them
 *
 * @author Stuart Douglas
 */
public class JDK9AlpnProvider implements ALPNProvider {


    public static final JDK9ALPNMethods JDK_9_ALPN_METHODS;

    static {
        JDK_9_ALPN_METHODS = AccessController.doPrivileged(new PrivilegedAction<JDK9ALPNMethods>() {
            @Override
            public JDK9ALPNMethods run() {
                try {
                    Method setApplicationProtocols = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
                    Method getApplicationProtocol = SSLEngine.class.getMethod("getApplicationProtocol");
                    UndertowLogger.ROOT_LOGGER.debug("Using JDK9 ALPN");
                    return new JDK9ALPNMethods(setApplicationProtocols, getApplicationProtocol);
                } catch (Exception e) {
                    UndertowLogger.ROOT_LOGGER.debug("JDK9 ALPN not supported", e);
                    return null;
                }
            }
        });
    }

    public static class JDK9ALPNMethods {
        private final Method setApplicationProtocols;
        private final Method getApplicationProtocol;

        JDK9ALPNMethods(Method setApplicationProtocols, Method getApplicationProtocol) {
            this.setApplicationProtocols = setApplicationProtocols;
            this.getApplicationProtocol = getApplicationProtocol;
        }

        public Method getApplicationProtocol() {
            return getApplicationProtocol;
        }

        public Method setApplicationProtocols() {
            return setApplicationProtocols;
        }
    }

    @Override
    public boolean isEnabled(SSLEngine sslEngine) {
        return JDK_9_ALPN_METHODS != null;
    }

    @Override
    public SSLEngine setProtocols(SSLEngine engine, String[] protocols) {
        SSLParameters sslParameters = engine.getSSLParameters();
        try {
            JDK_9_ALPN_METHODS.setApplicationProtocols().invoke(sslParameters, (Object) protocols);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        engine.setSSLParameters(sslParameters);
        return engine;
    }

    @Override
    public String getSelectedProtocol(SSLEngine engine) {
        try {
            return (String) JDK_9_ALPN_METHODS.getApplicationProtocol().invoke(engine);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getPriority() {
        return 300;
    }
}
