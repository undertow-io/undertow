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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final String JDK8_SUPPORT_PROPERTY = "io.undertow.protocols.alpn.jdk8";

    static {
        JDK_9_ALPN_METHODS = AccessController.doPrivileged(new PrivilegedAction<JDK9ALPNMethods>() {
            @Override
            public JDK9ALPNMethods run() {
                try {
                    final String javaVersion = System.getProperty("java.specification.version");
                    int vmVersion = 8;
                    try {
                        final Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(javaVersion);
                        if (matcher.find()) {
                            vmVersion = Integer.parseInt(matcher.group(1));
                        }
                    } catch (Exception ignore) {
                    }
                    // There was a backport of the ALPN support to Java 8 in rev 251. If a non-JDK implementation of the
                    // SSLEngine is used these methods throw an UnsupportedOperationException by default. However the
                    // methods would exist and could result in issues. By default it seems most JDK's have a working
                    // implementation. However since this was introduced in a micro release we should have a way to
                    // disable this feature. Setting the io.undertow.protocols.alpn.jdk8 to false will workaround the
                    // possible issue where the SSLEngine does not have an implementation of these methods.
                    final String value = System.getProperty(JDK8_SUPPORT_PROPERTY);
                    final boolean addSupportIfExists = value == null || value.trim().isEmpty() || Boolean.parseBoolean(value);
                    if (vmVersion > 8 || addSupportIfExists) {
                        Method setApplicationProtocols = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
                        Method getApplicationProtocol = SSLEngine.class.getMethod("getApplicationProtocol");
                        UndertowLogger.ROOT_LOGGER.debug("Using JDK9 ALPN");
                        return new JDK9ALPNMethods(setApplicationProtocols, getApplicationProtocol);
                    }
                    UndertowLogger.ROOT_LOGGER.debugf("It's not certain ALPN support was found. " +
                            "Provider %s will be disabled.", JDK9AlpnProvider.class.getName());
                    return null;
                } catch (Exception e) {
                    UndertowLogger.ROOT_LOGGER.debug("JDK9 ALPN not supported");
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

    @Override
    public String toString() {
        return "JDK9AlpnProvider";
    }
}
