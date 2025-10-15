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

import io.undertow.server.HttpServerExchange;
import io.undertow.UndertowLogger;

/**
 * @author Stuart Douglas
 */
public class PathSuffixPredicate implements Predicate {

    private final String suffix;
    private static final boolean traceEnabled;

    static {
        traceEnabled = UndertowLogger.PREDICATE_LOGGER.isTraceEnabled();
    }

    PathSuffixPredicate(final String suffix) {
            this.suffix = suffix;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        boolean matches = value.getRelativePath().endsWith(suffix);
        if (traceEnabled) {
            UndertowLogger.PREDICATE_LOGGER.tracef("Path suffix [%s] %s input [%s] for %s.", suffix, (matches ? "MATCHES" : "DOES NOT MATCH" ), value.getRelativePath(), value);
        }
        return matches;
    }

    public String toString() {
        return "path-suffix( '" + suffix +  "' )";
    }


    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "path-suffix";
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
            return Predicates.suffixes(path);
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
