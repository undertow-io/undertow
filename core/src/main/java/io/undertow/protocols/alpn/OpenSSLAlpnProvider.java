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

import io.undertow.UndertowLogger;

/**
 * Open listener adaptor for ALPN connections that use the Wildfly OpenSSL implementation
 * <p>
 *
 * @author Stuart Douglas
 */
public class OpenSSLAlpnProvider implements ALPNProvider {


    public static final OpenSSLALPNMethods OPENSSL_ALPN_METHODS;

    public static final String OPENSSL_ENGINE = "org.wildfly.openssl.OpenSSLEngine";

    static {
        OPENSSL_ALPN_METHODS = AccessController.doPrivileged(new PrivilegedAction<OpenSSLALPNMethods>() {
            @Override
            public OpenSSLALPNMethods run() {
                try {
                    Class<?> openSSLEngine = Class.forName(OPENSSL_ENGINE, true, OpenSSLAlpnProvider.class.getClassLoader());
                    Method setApplicationProtocols = openSSLEngine.getMethod("setApplicationProtocols", String[].class);
                    Method getApplicationProtocol = openSSLEngine.getMethod("getSelectedApplicationProtocol");
                    UndertowLogger.ROOT_LOGGER.debug("OpenSSL ALPN Enabled");
                    return new OpenSSLALPNMethods(setApplicationProtocols, getApplicationProtocol);
                } catch (Throwable e) {
                    UndertowLogger.ROOT_LOGGER.debug("OpenSSL ALPN Enabled", e);
                    return null;
                }
            }
        });
    }

    public static class OpenSSLALPNMethods {
        private final Method setApplicationProtocols;
        private final Method getApplicationProtocol;

        OpenSSLALPNMethods(Method setApplicationProtocols, Method getApplicationProtocol) {
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
        return OPENSSL_ALPN_METHODS != null && sslEngine.getClass().getName().equals(OPENSSL_ENGINE);
    }

    @Override
    public SSLEngine setProtocols(SSLEngine engine, String[] protocols) {
        try {
            OPENSSL_ALPN_METHODS.setApplicationProtocols().invoke(engine, (Object) protocols);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return engine;
    }

    @Override
    public String getSelectedProtocol(SSLEngine engine) {
        try {
            return (String) OPENSSL_ALPN_METHODS.getApplicationProtocol().invoke(engine);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getPriority() {
        return 400;
    }
}
