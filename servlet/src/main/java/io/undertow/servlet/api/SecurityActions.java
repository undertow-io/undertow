/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.api;

import java.security.PrivilegedAction;

final class SecurityActions {

    private SecurityActions() {
        // forbidden inheritance
    }

    @SuppressWarnings("removal")
    static String getSystemProperty(final String prop) {
        if (System.getSecurityManager() == null) {
            return System.getProperty(prop);
        } else {
            return (String) java.security.AccessController.doPrivileged((PrivilegedAction<Object>) () -> System.getProperty(prop));
        }
    }
}
