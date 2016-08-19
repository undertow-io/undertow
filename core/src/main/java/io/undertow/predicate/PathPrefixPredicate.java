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
import java.util.TreeMap;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathMatcher;

/**
 * @author Stuart Douglas
 */
class PathPrefixPredicate implements Predicate {

    private final PathMatcher<Boolean> pathMatcher;

    PathPrefixPredicate(final String... paths) {
        PathMatcher<Boolean> matcher = new PathMatcher<>();
        for(String path : paths) {
            if(!path.startsWith("/")) {
                matcher.addPrefixPath("/" + path, Boolean.TRUE);
            } else {
                matcher.addPrefixPath(path, Boolean.TRUE);
            }
        }
        this.pathMatcher = matcher;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        final String relativePath = value.getRelativePath();
        PathMatcher.PathMatch<Boolean> result = pathMatcher.match(relativePath);

        boolean matches = Boolean.TRUE.equals(result.getValue());
        if(matches) {
            Map<String, Object> context = value.getAttachment(PREDICATE_CONTEXT);
            if(context == null) {
                value.putAttachment(PREDICATE_CONTEXT, context = new TreeMap<>());
            }
            context.put("remaining", result.getRemaining());
        }
        return matches;
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "path-prefix";
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
            return new PathPrefixPredicate(path);
        }
    }
}
