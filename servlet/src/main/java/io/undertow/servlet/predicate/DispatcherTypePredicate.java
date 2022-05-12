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

package io.undertow.servlet.predicate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.DispatcherType;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * Predicate that returns true if the dispatcher type matches the specified type.
 *
 * @author Stuart Douglas
 */
public class DispatcherTypePredicate implements Predicate {

    public static final DispatcherTypePredicate FORWARD = new DispatcherTypePredicate(DispatcherType.FORWARD);
    public static final DispatcherTypePredicate INCLUDE = new DispatcherTypePredicate(DispatcherType.INCLUDE);
    public static final DispatcherTypePredicate REQUEST = new DispatcherTypePredicate(DispatcherType.REQUEST);
    public static final DispatcherTypePredicate ASYNC = new DispatcherTypePredicate(DispatcherType.ASYNC);
    public static final DispatcherTypePredicate ERROR = new DispatcherTypePredicate(DispatcherType.ERROR);


    private final DispatcherType dispatcherType;

    public DispatcherTypePredicate(final DispatcherType dispatcherType) {
        this.dispatcherType = dispatcherType;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        return value.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getDispatcherType() == dispatcherType;
    }

    @Override
    public String toString() {
        return "dispatcher( " + dispatcherType.toString() + " )";
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "dispatcher";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<>();
            params.put("value", String.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> params = new HashSet<>();
            params.add("value");
            return params;
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String value = (String) config.get("value");
            return new DispatcherTypePredicate(DispatcherType.valueOf(value));
        }
    }

}
