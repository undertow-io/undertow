/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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

/**
 * Shared utility methods for parsing formatted string, creating router instances and routing requests.
 *
 * @author Dirk Roets
 */
class PathTemplateUtil {

    private PathTemplateUtil() {
    }

    /**
     * Returns a portion of the specified path as a character array and ensures that the returned path always starts with a '/'
     * character.
     *
     * @param pathString The path.
     * @param pathLength Number of characters at the start of pathString to use in the returned result.
     * @return The path.
     */
    public static char[] pathWithForwardSlash(
            final String pathString,
            final int pathLength
    ) {
        if (pathLength == 0) {
            return new char[]{'/'};
        }

        final boolean startsWithForwardSlash = pathString.charAt(0) == '/';
        final char[] path = new char[pathLength + (startsWithForwardSlash ? 0 : 1)];
        if (startsWithForwardSlash) {
            pathString.getChars(0, pathLength, path, 0);
        } else {
            path[0] = '/';
            pathString.getChars(0, pathLength, path, 1);
        }
        return path;
    }
}
