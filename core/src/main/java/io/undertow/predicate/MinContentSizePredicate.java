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
import io.undertow.util.Headers;

/**
 * Predicate that returns true if the Content-Size of a request is below a
 * given value.
 *
 * Use {@link RequestSmallerThanPredicate} instead.
 *
 * @author Stuart Douglas
 */
@Deprecated
public class MinContentSizePredicate implements Predicate {

    private final long minSize;

    MinContentSizePredicate(final long minSize) {
        this.minSize = minSize;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        final String length = value.getResponseHeaders().getFirst(Headers.CONTENT_LENGTH);
        if (length == null) {
            return false;
        }
        return Long.parseLong(length) < minSize;
    }

    @Override
    public String toString() {
        return "max-content-size( " + minSize + " )";
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "min-content-size";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("value", Long.class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("value");
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            Long max = (Long) config.get("value");
            return new MinContentSizePredicate(max);
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
