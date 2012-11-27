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

package io.undertow.servlet.handlers;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that maintains the complete set of servlet path matches
 *
 * @author Stuart Douglas
 */
public class ServletPathMatches {

    private final Map<String, ServletPathMatch> exactPathMatches;

    private final Map<String, PathMatch> prefixMatches;

    private final Map<String, ServletInitialHandler> nameMatches;

    private final ServletInitialHandler defaultServlet;

    public ServletPathMatches(final Map<String, ServletInitialHandler> exactPathMatches, final Map<String, PathMatch> prefixMatches, final Map<String, ServletInitialHandler> nameMatches, final ServletInitialHandler defaultServlet) {
        this.prefixMatches = prefixMatches;
        this.nameMatches = nameMatches;
        this.defaultServlet = defaultServlet;
        Map<String, ServletPathMatch> newExactPathMatches = new HashMap<String, ServletPathMatch>();
        for (Map.Entry<String, ServletInitialHandler> entry : exactPathMatches.entrySet()) {
            newExactPathMatches.put(entry.getKey(), new ServletPathMatch(entry.getValue(), entry.getKey(), null));
        }
        this.exactPathMatches = newExactPathMatches;

    }

    public ServletInitialHandler getServletHandlerByName(final String name) {
        return nameMatches.get(name);
    }

    public ServletPathMatch getServletHandlerByExactPath(final String path) {
        return exactPathMatches.get(path);
    }

    public ServletPathMatch getServletHandlerByPath(final String path) {
        ServletPathMatch exact = exactPathMatches.get(path);
        if (exact != null) {
            return exact;
        }
        PathMatch match = prefixMatches.get(path);
        if (match != null) {
            return handleMatch(path, match, path, null, -1, path.lastIndexOf('.'));
        }
        int qsPos = -1;
        int extensionPos = -1;
        for (int i = path.length() - 1; i >= 0; --i) {
            final char c = path.charAt(i);
            if (c == '?') {
                //there was a query string, check the exact matches again
                final String part = path.substring(0, i);
                exact = exactPathMatches.get(part);
                if (exact != null) {
                    return exact;
                }
                qsPos = i;
                extensionPos = -1;
            } else if (c == '/') {
                final String part = path.substring(0, i);
                match = prefixMatches.get(part);
                if (match != null) {
                    return handleMatch(path, match, part, path.substring(i), qsPos, extensionPos);
                }
            } else if (c == '.') {
                if (extensionPos == -1) {
                    extensionPos = i;
                }
            }
        }
        return new ServletPathMatch(defaultServlet, "", path);
    }

    private ServletPathMatch handleMatch(final String path, final PathMatch match, String matched, String remaining, final int qsPos, final int extensionPos) {
        if (match.extensionMatches.isEmpty()) {
            return new ServletPathMatch(match.defaultHandler, matched, remaining);
        } else {
            if (extensionPos == -1) {
                return new ServletPathMatch(match.defaultHandler, matched, remaining);
            } else {
                final String ext;
                if (qsPos == -1) {
                    ext = path.substring(extensionPos + 1, path.length());
                } else {
                    ext = path.substring(extensionPos + 1, qsPos);
                }
                ServletInitialHandler handler = match.extensionMatches.get(ext);
                if (handler != null) {
                    if(qsPos == -1) {
                        return new ServletPathMatch(handler, path, null);
                    } else {
                        return new ServletPathMatch(handler, path.substring(0, qsPos), null);
                    }
                } else {
                    return new ServletPathMatch(match.defaultHandler, matched, remaining);
                }
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, ServletInitialHandler> exactPathMatches = new HashMap<String, ServletInitialHandler>();

        private final Map<String, PathMatch> prefixMatches = new HashMap<String, PathMatch>();

        private final Map<String, ServletInitialHandler> nameMatches = new HashMap<String, ServletInitialHandler>();

        private ServletInitialHandler defaultServlet;

        public void addExactMatch(final String exactMatch, final ServletInitialHandler match) {
            exactPathMatches.put(exactMatch, match);
        }

        public void addPrefixMatch(final String prefix, final ServletInitialHandler match) {
            PathMatch m = prefixMatches.get(prefix);
            if (m == null) {
                prefixMatches.put(prefix, m = new PathMatch(match));
            }
            m.defaultHandler = match;
        }

        public void addExtensionMatch(final String prefix, final String extension, final ServletInitialHandler match) {
            PathMatch m = prefixMatches.get(prefix);
            if (m == null) {
                prefixMatches.put(prefix, m = new PathMatch(null));
            }
            m.extensionMatches.put(extension, match);
        }

        public void addNameMatch(final String name, final ServletInitialHandler match) {
            nameMatches.put(name, match);
        }

        public ServletInitialHandler getDefaultServlet() {
            return defaultServlet;
        }

        public void setDefaultServlet(final ServletInitialHandler defaultServlet) {
            this.defaultServlet = defaultServlet;
        }

        public ServletPathMatches build() {
            return new ServletPathMatches(exactPathMatches, prefixMatches, nameMatches, defaultServlet);
        }

    }


    private static class PathMatch {

        private final Map<String, ServletInitialHandler> extensionMatches = new HashMap<String, ServletInitialHandler>();
        private volatile ServletInitialHandler defaultHandler;

        public PathMatch(final ServletInitialHandler defaultHandler) {
            this.defaultHandler = defaultHandler;
        }
    }

}
