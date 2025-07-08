/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import java.security.PrivilegedAction;

import static java.lang.System.getProperty;

/**
 * Security actions to access system environment information.  No methods in
 * this class are to be made public under any circumstances!
 */
final class SecurityActions {

    private SecurityActions() {
        // forbidden inheritance
    }

    @SuppressWarnings("removal")
    static String getSystemProperty(final String key, final String def) {
        return System.getSecurityManager() == null ? getProperty(key) : java.security.AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> getProperty(key,def));
    }
}
