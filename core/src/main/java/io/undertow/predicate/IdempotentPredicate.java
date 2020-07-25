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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

/**
 * A predicate that returns true if the request is idempotent
 * according to the HTTP RFC.
 *
 * @author Stuart Douglas
 */
public class IdempotentPredicate implements Predicate {

    public static final IdempotentPredicate INSTANCE = new IdempotentPredicate();

    private static final Set<HttpString> METHODS;

    static {
        Set<HttpString> methods = new HashSet<>();
        methods.add(Methods.GET);
        methods.add(Methods.DELETE);
        methods.add(Methods.PUT);
        methods.add(Methods.HEAD);
        methods.add(Methods.OPTIONS);
        METHODS = Collections.unmodifiableSet(methods);
    }


    @Override
    public boolean resolve(HttpServerExchange value) {
        return METHODS.contains(value.getRequestMethod());
    }

    @Override
    public String toString() {
        return "idempotent()";
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "idempotent";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return INSTANCE;
        }
    }
}
