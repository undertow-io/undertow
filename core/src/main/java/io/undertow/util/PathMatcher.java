/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

import io.undertow.UndertowMessages;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;

/**
 * Handler that dispatches to a given handler based of a prefix match of the path.
 * <p/>
 * This only matches a single level of a request, e.g if you have a request that takes the form:
 * <p/>
 * /foo/bar
 * <p/>
 *
 * @author Stuart Douglas
 */
public class PathMatcher<T> {

    private volatile T defaultHandler;
    private final ConcurrentMap<String, T> paths = new CopyOnWriteMap<String, T>();
    private final ConcurrentMap<String, T> exactPathMatches = new CopyOnWriteMap<String, T>();

    /**
     * lengths of all registered paths
     */
    private volatile int[] lengths = {};

    public PathMatcher(final T defaultHandler) {
        this.defaultHandler = defaultHandler;
    }

    public PathMatcher() {
    }

    /**
     * Matches a path against the registered handlers.
     * @param path The relative path to match
     * @return The match match. This will never be null, however if none matched its value field will be
     */
    public PathMatch<T> match(String path){
        if (!exactPathMatches.isEmpty()) {
            T match = exactPathMatches.get(path);
            if (match != null) {
                return new PathMatch<T>("", match);
            }
        }

        int length = path.length();
        final int[] lengths = this.lengths;
        for (int i = 0; i < lengths.length; ++i) {
            int pathLength = lengths[i];
            if (pathLength == length) {
                T next = paths.get(path);
                if (next != null) {
                    return new PathMatch<T>(path.substring(pathLength), next);
                }
            } else if (pathLength < length) {
                char c = path.charAt(pathLength);
                if (c == '/') {
                    String part = path.substring(0, pathLength);
                    T next = paths.get(part);
                    if (next != null) {
                        return new PathMatch<T>(path.substring(pathLength), next);
                    }
                }
            }
        }
        return new PathMatch<T>(path, defaultHandler);
    }

    /**
     * Adds a path prefix and a handler for that path. If the path does not start
     * with a / then one will be prepended.
     * <p/>
     * The match is done on a prefix bases, so registering /foo will also match /bar. Exact
     * path matches are taken into account first.
     * <p/>
     * If / is specified as the path then it will replace the default handler.
     *
     * @param path    The path
     * @param handler The handler
     */
    public synchronized PathMatcher addPrefixPath(final String path, final T handler) {
        if (path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }
        if (path.equals("/")) {
            this.defaultHandler = handler;
            return this;
        }
        if (path.charAt(0) != '/') {
            paths.put("/" + path, handler);
        } else {
            paths.put(path, handler);
        }
        buildLengths();
        return this;
    }


    public synchronized PathMatcher addExactPath(final String path, final T handler) {
        if (path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }
        if (path.charAt(0) != '/') {
            exactPathMatches.put("/" + path, handler);
        } else {
            exactPathMatches.put(path, handler);
        }
        return this;
    }

    public T getExactPath(final String path) {
        if (path.charAt(0) != '/') {
            return exactPathMatches.get("/" + path);
        } else {
            return exactPathMatches.get(path);
        }
    }

    public T getPrefixPath(final String path) {
        if (path.charAt(0) != '/') {
            return paths.get("/" + path);
        } else {
            return paths.get(path);
        }
    }

    private void buildLengths() {
        final Set<Integer> lengths = new TreeSet<Integer>(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -o1.compareTo(o2);
            }
        });
        for (String p : paths.keySet()) {
            lengths.add(p.length());
        }

        int[] lengthArray = new int[lengths.size()];
        int pos = 0;
        for (int i : lengths) {
            lengthArray[pos++] = i;
        }
        this.lengths = lengthArray;
    }

    @Deprecated
    public synchronized PathMatcher removePath(final String path) {
        return removePrefixPath(path);
    }

    public synchronized PathMatcher removePrefixPath(final String path) {
        if (path == null || path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }

        if (path.equals("/")) {
            defaultHandler = null;
            return this;
        }

        if (path.charAt(0) != '/') {
            paths.remove("/" + path);
        } else {
            paths.remove(path);
        }
        buildLengths();
        return this;
    }

    public synchronized PathMatcher removeExactPath(final String path) {
        if (path == null || path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }
        if (path.charAt(0) != '/') {
            exactPathMatches.remove("/" + path);
        } else {
            exactPathMatches.remove(path);
        }
        return this;
    }

    public synchronized PathMatcher clearPaths() {
        paths.clear();
        exactPathMatches.clear();
        this.lengths = new int[0];
        defaultHandler = null;
        return this;
    }

    public Map<String, T> getPaths() {
        return Collections.unmodifiableMap(paths);
    }

    public static final class PathMatch<T> {
        private final String remaining;
        private final T value;

        public PathMatch(String remaining, T value) {
            this.remaining = remaining;
            this.value = value;
        }

        public String getRemaining() {
            return remaining;
        }

        public T getValue() {
            return value;
        }
    }
}
