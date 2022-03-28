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
package io.undertow.websockets.jsr;

import java.security.AccessController;
import java.security.PrivilegedAction;

import jakarta.websocket.WebSocketContainer;

class SecurityActions {
    static void addContainer(final ClassLoader classLoader, final WebSocketContainer webSocketContainer) {
        if (System.getSecurityManager() == null) {
            UndertowContainerProvider.addContainer(classLoader, webSocketContainer);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    UndertowContainerProvider.addContainer(classLoader, webSocketContainer);
                    return null;
                }
            });
        }
    }

    static void removeContainer(final ClassLoader classLoader) {
        if (System.getSecurityManager() == null) {
            UndertowContainerProvider.removeContainer(classLoader);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    UndertowContainerProvider.removeContainer(classLoader);
                    return null;
                }
            });
        }
    }
}
