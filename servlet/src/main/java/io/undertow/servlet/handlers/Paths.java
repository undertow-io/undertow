/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.handlers;

import java.util.StringTokenizer;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class Paths {

    private static final String META_INF = "META-INF";
    private static final String WEB_INF = "WEB-INF";

    private Paths() {
        // forbidden instantiation
    }

    static boolean isForbidden(final String path) {
        final StringTokenizer st = new StringTokenizer(path, "/\\", false);
        String subPath;
        while (st.hasMoreTokens()) {
            subPath = st.nextToken();
            if (META_INF.equalsIgnoreCase(subPath) || WEB_INF.equalsIgnoreCase(subPath)) {
                return true;
            }
        }
        return false;
    }

}
