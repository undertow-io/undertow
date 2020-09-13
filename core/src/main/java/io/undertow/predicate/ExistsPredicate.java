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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.server.HttpServerExchange;

/**
 * Returns true if the given attribute is not null and not an empty string
 *
 * @author Stuart Douglas
 */
public class ExistsPredicate implements Predicate {

    private final ExchangeAttribute attribute;

    ExistsPredicate(final ExchangeAttribute attribute) {
        this.attribute = attribute;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        final String att = attribute.readAttribute(value);
        if(att == null) {
            return false;
        }
        return !att.isEmpty();
    }

    @Override
    public String toString() {
        return "exists( '" + attribute.toString() + "' )";
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "exists";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<>();
            params.put("value", ExchangeAttribute.class);
            return params;
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
            ExchangeAttribute value = (ExchangeAttribute) config.get("value");
            return new ExistsPredicate(value);
        }
    }
}
