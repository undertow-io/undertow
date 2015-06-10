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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.server.HttpServerExchange;

/**
 * Returns true if the request header is present and contains one of the strings to match.
 *
 * @author Stuart Douglas
 */
public class ContainsPredicate implements Predicate {

    private final ExchangeAttribute attribute;
    private final String[] values;

    ContainsPredicate(final ExchangeAttribute attribute, final String[] values) {
        this.attribute = attribute;
        this.values = new String[values.length];
        System.arraycopy(values, 0, this.values, 0, values.length);
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        String attr = attribute.readAttribute(value);
        if (attr == null) {
            return false;
        }
        for (int i = 0; i < values.length; ++i) {
            if (attr.contains(values[i])) {
                return true;
            }
        }
        return false;
    }

    public ExchangeAttribute getAttribute() {
        return attribute;
    }

    public String[] getValues() {
        String[] ret = new String[values.length];
        System.arraycopy(values, 0, ret, 0, values.length);
        return ret;
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "contains";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<>();
            params.put("value", ExchangeAttribute.class);
            params.put("search", String[].class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> params = new HashSet<>();
            params.add("value");
            params.add("search");
            return params;
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String[] search = (String[]) config.get("search");
            ExchangeAttribute values = (ExchangeAttribute) config.get("value");
            return new ContainsPredicate(values, search);
        }
    }
}
