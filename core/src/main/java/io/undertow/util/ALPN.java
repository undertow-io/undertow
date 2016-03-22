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

package io.undertow.util;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * @author Stuart Douglas
 */
public class ALPN {

    private ALPN() {};

    public static final JDK9ALPNMethods JDK_9_ALPN_METHODS;

    static {
        JDK_9_ALPN_METHODS = AccessController.doPrivileged(new PrivilegedAction<JDK9ALPNMethods>() {
            @Override
            public JDK9ALPNMethods run() {
                try {
                    Method setApplicationProtocols = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
                    Method getApplicationProtocol = SSLEngine.class.getMethod("getApplicationProtocol");
                    return new JDK9ALPNMethods(setApplicationProtocols, getApplicationProtocol);
                } catch (Exception e) {
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
}
