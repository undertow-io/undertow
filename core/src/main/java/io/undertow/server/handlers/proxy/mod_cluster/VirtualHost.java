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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;

import io.undertow.UndertowMessages;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.PathMatcher;

/**
 * The virtual host handler.
 *
 * @author Emanuel Muckenhuber
 */
public class VirtualHost {

    private static final char PATH_SEPARATOR = '/';
    private static final String STRING_PATH_SEPARATOR = "/";

    private final HostEntry defaultHandler = new HostEntry(STRING_PATH_SEPARATOR);
    private final ConcurrentMap<String, HostEntry> contexts = new CopyOnWriteMap<>();

    /**
     * lengths of all registered contexts
     */
    private volatile int[] lengths = {};

    protected VirtualHost() {
        //
    }

    /**
     * Matches a path against the registered handlers.
     *
     * @param path The relative path to match
     * @return The match match. This will never be null, however if none matched its value field will be
     */
    PathMatcher.PathMatch<HostEntry> match(String path){
        int length = path.length();
        final int[] lengths = this.lengths;
        for (int i = 0; i < lengths.length; ++i) {
            int pathLength = lengths[i];
            if (pathLength == length) {
                HostEntry next = contexts.get(path);
                if (next != null) {
                    return new PathMatcher.PathMatch<>(path.substring(pathLength), next);
                }
            } else if (pathLength < length) {
                char c = path.charAt(pathLength);
                if (c == '/') {
                    String part = path.substring(0, pathLength);
                    HostEntry next = contexts.get(part);
                    if (next != null) {
                        return new PathMatcher.PathMatch<>(path.substring(pathLength), next);
                    }
                }
            }
        }
        return new PathMatcher.PathMatch<>(path, defaultHandler);
    }

    public synchronized void registerContext(final String path, final String jvmRoute, final Context context) {
        if (path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }

        final String normalizedPath = this.normalizeSlashes(path);
        if (STRING_PATH_SEPARATOR.equals(normalizedPath)) {
            defaultHandler.contexts.put(jvmRoute, context);
            return;
        }

        boolean rebuild = false;
        HostEntry hostEntry = contexts.get(normalizedPath);
        if (hostEntry == null) {
            rebuild = true;
            hostEntry = new HostEntry(normalizedPath);
            contexts.put(normalizedPath, hostEntry);
        }
        assert !hostEntry.contexts.containsKey(jvmRoute);
        hostEntry.contexts.put(jvmRoute, context);
        if (rebuild) {
            buildLengths();
        }
    }

    public synchronized void removeContext(final String path, final String jvmRoute, final Context context) {
        if (path == null || path.isEmpty()) {
            throw UndertowMessages.MESSAGES.pathMustBeSpecified();
        }

        final String normalizedPath = this.normalizeSlashes(path);
        if (STRING_PATH_SEPARATOR.equals(normalizedPath)) {
            defaultHandler.contexts.remove(jvmRoute, context);
        }

        final HostEntry hostEntry = contexts.get(normalizedPath);
        if (hostEntry != null) {
            if (hostEntry.contexts.remove(jvmRoute, context)) {
                if (hostEntry.contexts.isEmpty()) {
                    contexts.remove(normalizedPath);
                    buildLengths();
                }
            }
        }
    }

    boolean isEmpty() {
        return contexts.isEmpty() && defaultHandler.contexts.isEmpty();
    }

    private void buildLengths() {
        final Set<Integer> lengths = new TreeSet<>(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return -o1.compareTo(o2);
            }
        });
        for (String p : contexts.keySet()) {
            lengths.add(p.length());
        }

        int[] lengthArray = new int[lengths.size()];
        int pos = 0;
        for (int i : lengths) {
            lengthArray[pos++] = i;
        }
        this.lengths = lengthArray;
    }


    /**
     * Adds a '/' prefix to the beginning of a path if one isn't present
     * and removes trailing slashes if any are present.
     *
     * @param path the path to normalize
     * @return a normalized (with respect to slashes) result
     */
    private String normalizeSlashes(final String path) {
        // prepare
        final StringBuilder builder = new StringBuilder(path);
        boolean modified = false;

        // remove all trailing '/'s except the first one
        while (builder.length() > 0 && builder.length() != 1 && PATH_SEPARATOR == builder.charAt(builder.length() - 1)) {
            builder.deleteCharAt(builder.length() - 1);
            modified = true;
        }

        // add a slash at the beginning if one isn't present
        if (builder.length() == 0 || PATH_SEPARATOR != builder.charAt(0)) {
            builder.insert(0, PATH_SEPARATOR);
            modified = true;
        }

        // only create string when it was modified
        if (modified) {
            return builder.toString();
        }

        return path;
    }

    static class HostEntry {

        // node > context
        private final ConcurrentMap<String, Context> contexts = new CopyOnWriteMap<>();
        private final String contextPath;

        HostEntry(String contextPath) {
            this.contextPath = contextPath;
        }

        protected String getContextPath() {
            return contextPath;
        }

        /**
         * Get a context for a jvmRoute.
         *
         * @param jvmRoute    the jvm route
         * @return
         */
        protected Context getContextForNode(final String jvmRoute) {
            return contexts.get(jvmRoute);
        }

        /**
         * Get all registered contexts.
         *
         * @return
         */
        protected Collection<Context> getContexts() {
            return Collections.unmodifiableCollection(contexts.values());
        }

    }


}
