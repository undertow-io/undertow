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

package io.undertow.predicate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathMatcher;
import io.undertow.UndertowLogger;

/**
 * @author Stuart Douglas
 */
public class PathMatchPredicate implements Predicate {

    private final PathMatcher<Boolean> pathMatcher;
    private static final boolean traceEnabled;

    static {
        traceEnabled = UndertowLogger.PREDICATE_LOGGER.isTraceEnabled();
    }

    PathMatchPredicate(final String... paths) {
        PathMatcher<Boolean> matcher = new PathMatcher<>();
        for(String path : paths) {
            if(!path.startsWith("/")) {
                matcher.addExactPath("/" + path, Boolean.TRUE);
            } else {
                matcher.addExactPath(path, Boolean.TRUE);
            }
        }
        this.pathMatcher = matcher;
    }

    public String toString() {
        Set<String> matches = pathMatcher.getExactPathMatchesSet();
        if( matches.size() == 1 ) {
            return "path( '" + matches.toArray()[0] +  "' )";
        } else {
            return "path( { '" + matches.stream().collect(Collectors.joining("', '")) +  "' } )";
        }
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        final String relativePath = value.getRelativePath();
        PathMatcher.PathMatch<Boolean> result = pathMatcher.match(relativePath);
        boolean matches = Boolean.TRUE.equals(result.getValue());
        if (traceEnabled) {
            UndertowLogger.PREDICATE_LOGGER.tracef( "Path(s) [%s] %s input [%s] for %s.", pathMatcher.getExactPathMatchesSet().stream().collect(Collectors.joining(", ")), ( matches ? "MATCH" : "DO NOT MATCH" ), relativePath, value );
        }
        return matches;
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "path";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("path", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("path");
        }

        @Override
        public String defaultParameter() {
            return "path";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String[] path = (String[]) config.get("path");
            return new PathMatchPredicate(path);
        }
    }
}
